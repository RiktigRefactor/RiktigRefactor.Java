package io.github.riktigrefactor.java.extractiife;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.github.riktigrefactor.java.RefusalCodes;
import io.github.riktigrefactor.java.analysis.Compilation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** What the selection uses from outside: locals (and which it writes), the receiver, and checked exceptions. */
final class Captures {
    static final Set<ElementKind> LOCAL_KINDS = Set.of(
            ElementKind.LOCAL_VARIABLE,
            ElementKind.PARAMETER,
            ElementKind.EXCEPTION_PARAMETER,
            ElementKind.RESOURCE_VARIABLE,
            ElementKind.BINDING_VARIABLE);

    /** Outer locals in order of first use, with the start of every identifier that uses them. */
    final Map<VariableElement, List<Integer>> outerLocals = new LinkedHashMap<>();

    final Set<VariableElement> written = new LinkedHashSet<>();
    final List<Integer> explicitThis = new ArrayList<>();
    final List<Integer> implicitMembers = new ArrayList<>();
    Element thisElement;
    String refusalCode;
    String refusalMessage;
    TypeElement enclosingClass;

    private final Compilation compilation;
    private final String file;
    private final Selection selection;

    private Captures(Compilation compilation, String file, Selection selection) {
        this.compilation = compilation;
        this.file = file;
        this.selection = selection;
    }

    boolean usesThis() {
        return !explicitThis.isEmpty() || !implicitMembers.isEmpty();
    }

    static Captures analyze(Compilation compilation, String file, Selection selection) {
        Captures captures = new Captures(compilation, file, selection);
        captures.scan();
        return captures;
    }

    private void refuse(String code, String message) {
        if (refusalCode == null) {
            refusalCode = code;
            refusalMessage = message;
        }
    }

    private void scan() {
        for (TreePath path = selection.roots().getFirst(); path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof ClassTree && compilation.trees.getElement(path) instanceof TypeElement type) {
                enclosingClass = type;
                break;
            }
        }
        var unit = compilation.unitsByFile.get(file);
        for (TreePath root : selection.roots()) {
            new TreePathScanner<Void, Void>() {
                private int classDepth = 0;

                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    classDepth++;
                    try {
                        return super.visitClass(tree, unused);
                    } finally {
                        classDepth--;
                    }
                }

                @Override
                public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                    boolean qualifiedThisOrSuper = tree.getIdentifier().contentEquals("this")
                            || tree.getExpression() instanceof MemberSelectTree inner
                                    && inner.getIdentifier().contentEquals("super");
                    if (classDepth == 0 && qualifiedThisOrSuper) {
                        refuse(
                                RefusalCodes.QUALIFIED_THIS,
                                "The selection uses a qualified `Outer.this` or `Outer.super`, which can't be passed in as a parameter.");
                    }
                    return super.visitMemberSelect(tree, unused);
                }

                @Override
                public Void visitIdentifier(IdentifierTree tree, Void unused) {
                    TreePath path = getCurrentPath();
                    Element element = compilation.trees.getElement(path);
                    int start = (int) compilation.start(unit, tree);
                    if (tree.getName().contentEquals("this")) {
                        if (classDepth == 0) {
                            explicitThis.add(start);
                            thisElement = element;
                        }
                    } else if (tree.getName().contentEquals("super")) {
                        if (classDepth == 0) {
                            refuse(
                                    RefusalCodes.SUPER_ACCESS,
                                    "The selection uses `super`, which a parameter can't stand for.");
                        }
                    } else if (element instanceof VariableElement variable
                            && LOCAL_KINDS.contains(element.getKind())
                            && !isDeclaredInsideSelection(element)) {
                        outerLocals
                                .computeIfAbsent(variable, v -> new ArrayList<>())
                                .add(start);
                        if (isWrite(path)) {
                            written.add(variable);
                        }
                    } else if (classDepth == 0 && isInstanceMember(element)) {
                        if (enclosingClass != null && isInheritedBy(element, enclosingClass)) {
                            implicitMembers.add(start);
                        } else {
                            refuse(
                                    RefusalCodes.OUTER_INSTANCE_ACCESS,
                                    "The selection uses '" + tree.getName()
                                            + "', a member of an enclosing instance, which can't be passed in as a parameter.");
                        }
                    }
                    return null;
                }
            }.scan(root, null);
        }
    }

    private static boolean isInstanceMember(Element element) {
        return element != null
                && (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.METHOD)
                && !element.getModifiers().contains(Modifier.STATIC)
                && element.getEnclosingElement() instanceof TypeElement;
    }

    private boolean isInheritedBy(Element member, TypeElement type) {
        return compilation.types.isSubtype(
                compilation.types.erasure(type.asType()),
                compilation.types.erasure(member.getEnclosingElement().asType()));
    }

    boolean isDeclaredInsideSelection(Element element) {
        TreePath declaration = compilation.trees.getPath(element);
        return declaration != null
                && compilation.fileOfUnit.get(declaration.getCompilationUnit()).equals(file)
                && selection.contains(compilation.start(declaration.getCompilationUnit(), declaration.getLeaf()));
    }

    static boolean isWrite(TreePath path) {
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

    /** Whether an exception from the selection could reach code in the same method that reads a written local. */
    boolean isInsideTry() {
        for (TreePath path = selection.roots().getFirst().getParentPath(); path != null; path = path.getParentPath()) {
            Tree leaf = path.getLeaf();
            if (leaf instanceof MethodTree || leaf instanceof LambdaExpressionTree || leaf instanceof ClassTree) {
                return false;
            }
            if (leaf instanceof TryTree) {
                return true;
            }
        }
        return false;
    }

    private List<? extends TypeMirror> closeThrows(TypeMirror resourceType) {
        if (resourceType == null || !(compilation.types.asElement(resourceType) instanceof TypeElement type)) {
            return null;
        }
        for (Element member : compilation.elements.getAllMembers(type)) {
            if (member instanceof ExecutableElement method
                    && method.getSimpleName().contentEquals("close")
                    && method.getParameters().isEmpty()) {
                return method.getThrownTypes();
            }
        }
        return null;
    }

    /**
     * The checked exceptions the selection can throw, outside nested lambdas and classes, less those it catches
     * itself; null if a thrown type is a type variable or a resource's close method can't be found.
     */
    List<TypeMirror> thrownCheckedExceptions() {
        List<TypeMirror> thrown = new ArrayList<>();
        boolean[] unknown = new boolean[1];
        for (TreePath root : selection.roots()) {
            new TreePathScanner<Void, List<TypeMirror>>() {
                @Override
                public Void visitClass(ClassTree tree, List<TypeMirror> into) {
                    return null;
                }

                @Override
                public Void visitLambdaExpression(LambdaExpressionTree tree, List<TypeMirror> into) {
                    return null;
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree tree, List<TypeMirror> into) {
                    if (compilation.trees.getElement(getCurrentPath()) instanceof ExecutableElement method) {
                        into.addAll(method.getThrownTypes());
                    }
                    return super.visitMethodInvocation(tree, into);
                }

                @Override
                public Void visitNewClass(NewClassTree tree, List<TypeMirror> into) {
                    if (compilation.trees.getElement(getCurrentPath()) instanceof ExecutableElement constructor) {
                        into.addAll(constructor.getThrownTypes());
                    }
                    scan(tree.getEnclosingExpression(), into);
                    scan(tree.getArguments(), into);
                    return null;
                }

                @Override
                public Void visitThrow(ThrowTree tree, List<TypeMirror> into) {
                    into.add(compilation.trees.getTypeMirror(new TreePath(getCurrentPath(), tree.getExpression())));
                    return super.visitThrow(tree, into);
                }

                @Override
                public Void visitTry(TryTree tree, List<TypeMirror> into) {
                    List<TypeMirror> fromBody = new ArrayList<>();
                    for (Tree resource : tree.getResources()) {
                        scan(resource, fromBody);
                        List<? extends TypeMirror> closeThrows =
                                closeThrows(compilation.trees.getTypeMirror(new TreePath(getCurrentPath(), resource)));
                        if (closeThrows == null) {
                            unknown[0] = true;
                        } else {
                            fromBody.addAll(closeThrows);
                        }
                    }
                    scan(tree.getBlock(), fromBody);
                    for (var catchTree : tree.getCatches()) {
                        Tree type = catchTree.getParameter().getType();
                        List<? extends Tree> alternatives =
                                type instanceof UnionTypeTree union ? union.getTypeAlternatives() : List.of(type);
                        for (Tree alternative : alternatives) {
                            TypeMirror caught =
                                    compilation.trees.getTypeMirror(TreePath.getPath(getCurrentPath(), alternative));
                            fromBody.removeIf(t -> caught != null && compilation.types.isSubtype(t, caught));
                        }
                    }
                    into.addAll(fromBody);
                    for (var catchTree : tree.getCatches()) {
                        scan(catchTree.getBlock(), into);
                    }
                    scan(tree.getFinallyBlock(), into);
                    return null;
                }
            }.scan(root, thrown);
        }
        TypeMirror runtime = compilation
                .elements
                .getTypeElement("java.lang.RuntimeException")
                .asType();
        TypeMirror error =
                compilation.elements.getTypeElement("java.lang.Error").asType();
        List<TypeMirror> checked = new ArrayList<>();
        for (TypeMirror type : thrown) {
            if (type == null || type.getKind() == TypeKind.TYPEVAR) {
                return null;
            }
            if (compilation.types.isSubtype(type, runtime) || compilation.types.isSubtype(type, error)) {
                continue;
            }
            if (checked.stream().noneMatch(t -> compilation.types.isSameType(t, type))) {
                checked.add(type);
            }
        }
        return unknown[0] ? null : checked;
    }
}
