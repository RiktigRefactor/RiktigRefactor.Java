package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Helpers shared by the refactorings that change declarations and call sites. */
public final class Support {
    private final Compilation compilation;

    public Support(Compilation compilation) {
        this.compilation = compilation;
    }

    /** The first token at or after {@code from} in the file that is exactly {@code text}, or null. */
    public Token tokenAtOrAfter(String file, long from, String text) {
        for (Token token : compilation.tokensByFile.get(file)) {
            if (token.start() >= from && token.text().equals(text)) {
                return token;
            }
        }
        return null;
    }

    public String textOf(String file, Tree tree) {
        CompilationUnitTree unit = compilation.unitsByFile.get(file);
        return compilation.sources.get(file).substring((int) compilation.start(unit, tree), (int)
                compilation.end(unit, tree));
    }

    public static String packageOf(Element element) {
        for (Element e = element; e != null; e = e.getEnclosingElement()) {
            if (e instanceof PackageElement pkg) {
                return pkg.getQualifiedName().toString();
            }
        }
        return "";
    }

    /**
     * How to name a type from code in {@code fromFile}: its name qualified by its enclosing types within the same
     * package, its canonical name from another package, or null for an anonymous class.
     */
    public String typeReference(TypeElement type, String fromFile) {
        if (type.getNestingKind() == NestingKind.ANONYMOUS) {
            return null;
        }
        if (type.getNestingKind() == NestingKind.LOCAL) {
            return type.getSimpleName().toString();
        }
        String fromPackage = compilation.unitsByFile.get(fromFile).getPackageName() == null
                ? ""
                : compilation.unitsByFile.get(fromFile).getPackageName().toString();
        if (!fromPackage.equals(packageOf(type))) {
            return type.getQualifiedName().toString();
        }
        StringBuilder name = new StringBuilder(type.getSimpleName());
        for (Element e = type.getEnclosingElement(); e instanceof TypeElement outer; e = e.getEnclosingElement()) {
            if (outer.getNestingKind() == NestingKind.LOCAL) {
                return type.getSimpleName().toString();
            }
            name.insert(0, outer.getSimpleName() + ".");
        }
        return name.toString();
    }

    /** {@code Order} becomes {@code order}. */
    public static String lowerCamel(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** Whether the local is never assigned after its declaration anywhere in its compilation unit. */
    public boolean isNeverReassigned(VariableElement variable) {
        TreePath declaration = compilation.trees.getPath(variable);
        if (declaration == null) {
            return false;
        }
        boolean[] assigned = new boolean[1];
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree identifier, Void unused) {
                if (isWrite(getCurrentPath()) && variable.equals(compilation.trees.getElement(getCurrentPath()))) {
                    assigned[0] = true;
                }
                return null;
            }
        }.scan(new TreePath(declaration.getCompilationUnit()), null);
        return !assigned[0];
    }

    public static boolean isWrite(TreePath path) {
        Tree tree = path.getLeaf();
        return switch (path.getParentPath().getLeaf()) {
            case AssignmentTree assignment -> assignment.getVariable() == tree;
            case CompoundAssignmentTree assignment -> assignment.getVariable() == tree;
            case UnaryTree unary ->
                unary.getExpression() == tree
                        && switch (unary.getKind()) {
                            case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> true;
                            default -> false;
                        };
            default -> false;
        };
    }

    /**
     * Whether an expression is provably not null: {@code this}, an object creation, a string literal, or a local that
     * is initialized with one of those and never reassigned.
     */
    public boolean isProvablyNonNull(TreePath path) {
        Tree tree = path.getLeaf();
        return switch (tree) {
            case ParenthesizedTree parenthesized ->
                isProvablyNonNull(new TreePath(path, parenthesized.getExpression()));
            case NewClassTree creation -> true;
            case LiteralTree literal -> literal.getKind() == Tree.Kind.STRING_LITERAL;
            case IdentifierTree identifier when identifier.getName().contentEquals("this") -> true;
            case IdentifierTree identifier ->
                compilation.trees.getElement(path) instanceof VariableElement variable
                        && variable.getKind() == ElementKind.LOCAL_VARIABLE
                        && isNeverReassigned(variable)
                        && compilation.trees.getPath(variable) instanceof TreePath declaration
                        && declaration.getLeaf() instanceof VariableTree declared
                        && declared.getInitializer() != null
                        && isProvablyNonNull(new TreePath(declaration, declared.getInitializer()));
            default -> false;
        };
    }

    /**
     * Whether evaluating the expression has no observable effect and can't throw: a literal, a local or parameter, or
     * {@code this}.
     */
    public boolean isSideEffectFree(TreePath path) {
        Tree tree = path.getLeaf();
        return switch (tree) {
            case ParenthesizedTree parenthesized -> isSideEffectFree(new TreePath(path, parenthesized.getExpression()));
            case LiteralTree literal -> true;
            case IdentifierTree identifier when identifier.getName().contentEquals("this") -> true;
            case IdentifierTree identifier -> {
                Element element = compilation.trees.getElement(path);
                yield element != null
                        && (element.getKind() == ElementKind.LOCAL_VARIABLE
                                || element.getKind() == ElementKind.PARAMETER
                                || element.getKind() == ElementKind.EXCEPTION_PARAMETER
                                || element.getKind() == ElementKind.RESOURCE_VARIABLE
                                || element.getKind() == ElementKind.BINDING_VARIABLE);
            }
            default -> false;
        };
    }

    /** The locals a tree assigns. */
    public List<Element> assignedLocals(TreePath root) {
        List<Element> result = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree identifier, Void unused) {
                if (isWrite(getCurrentPath())) {
                    result.add(compilation.trees.getElement(getCurrentPath()));
                }
                return null;
            }
        }.scan(root, null);
        return result;
    }

    /**
     * Whether two expressions are independent (principles.md): one of them is side-effect free and reads nothing the
     * other assigns.
     */
    public boolean areIndependent(TreePath a, TreePath b) {
        return (isSideEffectFree(a) && !assignedLocals(b).contains(compilation.trees.getElement(a)))
                || (isSideEffectFree(b) && !assignedLocals(a).contains(compilation.trees.getElement(b)));
    }

    public static boolean isStatic(Element element) {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    public static ExpressionTree unparenthesized(ExpressionTree tree) {
        ExpressionTree result = tree;
        while (result instanceof ParenthesizedTree parenthesized) {
            result = parenthesized.getExpression();
        }
        return result;
    }
}
