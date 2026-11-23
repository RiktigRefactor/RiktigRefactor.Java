package io.github.riktigrefactor.java.movestatic;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.RefusalCodes;
import io.github.riktigrefactor.java.analysis.Bindings;
import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.MemberMove;
import io.github.riktigrefactor.java.analysis.Occurrences;
import io.github.riktigrefactor.java.analysis.Prepared;
import io.github.riktigrefactor.java.analysis.SourceTypes;
import io.github.riktigrefactor.java.analysis.Support;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.analysis.Verification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Moves a static method or field to another class, and rewrites every reference to name the new class. */
public final class MoveStaticMember {
    private final Compilation compilation;
    private final Support support;
    private final List<TextEdit> edits = new ArrayList<>();
    private MemberMove mover;
    private Element member;
    private TypeElement source;
    private TypeElement target;
    private String sourceFile;
    private String targetFile;
    private CompilationUnitTree sourceUnit;
    private Tree declaration;
    private int declarationStart;
    private int declarationEnd;
    private Outcome refusal;

    private MoveStaticMember(Compilation compilation) {
        this.compilation = compilation;
        this.support = new Support(compilation);
    }

    public static Outcome run(Map<String, String> sources, String file, int cursor, String target) {
        return run(sources, file, cursor, target, false);
    }

    /** The deliberately naive version: the same edits with no preconditions and no verification. */
    public static Outcome runNaive(Map<String, String> sources, String file, int cursor, String target) {
        return run(sources, file, cursor, target, true);
    }

    private static Outcome run(Map<String, String> sources, String file, int cursor, String target, boolean naive) {
        Prepared prepared = Prepared.analyze(sources);
        if (prepared.refusal() != null) {
            return prepared.refusal();
        }
        return new MoveStaticMember(prepared.compilation()).move(file, cursor, target, naive);
    }

    private void refuse(String code, String message, String file, long start, long end, String suggestion) {
        if (refusal == null) {
            refusal = Outcome.refuse(code, message, file, (int) start, (int) end, suggestion);
        }
    }

    private Outcome move(String cursorFile, int cursor, String targetName, boolean naive) {
        for (var occurrence : Occurrences.find(compilation)) {
            if (occurrence.file().equals(cursorFile)
                    && occurrence.token().start() <= cursor
                    && cursor <= occurrence.token().end()) {
                member = occurrence.element();
                break;
            }
        }
        boolean isMovableKind = member != null
                && (member.getKind() == ElementKind.METHOD || member.getKind() == ElementKind.FIELD)
                && member.getEnclosingElement() instanceof TypeElement;
        if (!isMovableKind) {
            return Outcome.refuse(
                    RefusalCodes.NO_SYMBOL, "There is no method or field at the cursor.", cursorFile, cursor, cursor);
        }
        if (!Support.isStatic(member)) {
            return Outcome.refuse(
                    RefusalCodes.NOT_STATIC,
                    "'" + member.getSimpleName() + "' is an instance member; only static members move.",
                    cursorFile,
                    cursor,
                    cursor,
                    "Use Make method static first.");
        }
        source = (TypeElement) member.getEnclosingElement();
        target = compilation.elements.getTypeElement(targetName);
        SourceTypes sourceTypes = new SourceTypes(compilation);
        if (target == null || !sourceTypes.isInSource(target)) {
            return Outcome.refuse(
                    RefusalCodes.TARGET_NOT_FOUND,
                    "The target '" + targetName + "' is not a class declared in the workspace.",
                    cursorFile,
                    cursor,
                    cursor);
        }
        if (target.equals(source)) {
            return Outcome.refuse(
                    RefusalCodes.TARGET_NOT_FOUND, "The member is already in the target.", cursorFile, cursor, cursor);
        }
        TreePath path = compilation.trees.getPath(member);
        if (path == null || !sourceTypes.isInSource(member)) {
            return Outcome.refuse(
                    RefusalCodes.NOT_RENAMEABLE,
                    "The member is not declared in the workspace.",
                    cursorFile,
                    cursor,
                    cursor);
        }
        sourceUnit = path.getCompilationUnit();
        sourceFile = compilation.fileOfUnit.get(sourceUnit);
        targetFile =
                compilation.fileOfUnit.get(compilation.trees.getPath(target).getCompilationUnit());
        declaration = path.getLeaf();
        declarationStart = (int) compilation.start(sourceUnit, declaration);
        declarationEnd = (int) compilation.end(sourceUnit, declaration);
        if (!naive) {
            Outcome checked = check(path);
            if (checked != null) {
                return checked;
            }
        }
        mover = new MemberMove(compilation, member, target);
        mover.qualifySourceScope(path);
        rewriteReferences();
        if (refusal != null && !naive) {
            return refusal;
        }
        mover.moveDeclaration(edits);
        if (naive) {
            return new Outcome.Success(edits, List.of());
        }
        Verification verification = Verification.verify(
                compilation,
                edits,
                true,
                error -> switch (String.valueOf(error.getCode())) {
                    case "compiler.err.report.access", "compiler.err.not.def.public.cant.access" ->
                        RefusalCodes.INACCESSIBLE;
                    default ->
                        String.valueOf(error.getCode()).startsWith("compiler.err.already.defined")
                                ? RefusalCodes.CONFLICTING_DECLARATION
                                : RefusalCodes.WOULD_INTRODUCE_ERRORS;
                },
                Verification.Mode.STRUCTURAL,
                Bindings.Binding::binding);
        if (verification.refusal != null) {
            return new Outcome.Refused(verification.refusal);
        }
        return new Outcome.Success(edits, List.of());
    }

    private Outcome check(TreePath path) {
        int at = declarationStart;
        if (target.getKind() != ElementKind.CLASS && target.getKind() != ElementKind.RECORD) {
            return Outcome.refuse(
                    RefusalCodes.TARGET_NOT_SUPPORTED,
                    "The target must be a class or record; moving into interfaces and enums is not supported yet.",
                    sourceFile,
                    at,
                    at);
        }
        if (target.getNestingKind() == NestingKind.LOCAL || target.getNestingKind() == NestingKind.ANONYMOUS) {
            return Outcome.refuse(
                    RefusalCodes.TARGET_NOT_SUPPORTED, "The target is a local or anonymous class.", sourceFile, at, at);
        }
        if (member instanceof ExecutableElement method && isMain(method)) {
            return Outcome.refuse(
                    RefusalCodes.ENTRY_POINT, "'main' is found by name in its class.", sourceFile, at, at);
        }
        if (member.getModifiers().contains(Modifier.SYNCHRONIZED)
                || member.getModifiers().contains(Modifier.NATIVE)) {
            return Outcome.refuse(
                    RefusalCodes.SYNCHRONIZED_OR_NATIVE,
                    "The method is synchronized (it locks its class object) or native (found by name in its class).",
                    sourceFile,
                    at,
                    at);
        }
        for (Element existing : target.getEnclosedElements()) {
            if (existing.getSimpleName().contentEquals(member.getSimpleName())
                    && (existing.getKind() == ElementKind.FIELD) == (member.getKind() == ElementKind.FIELD)
                    && member.getKind() == ElementKind.FIELD) {
                return Outcome.refuse(
                        RefusalCodes.CONFLICTING_DECLARATION,
                        "The target already has a field named '" + member.getSimpleName() + "'.",
                        sourceFile,
                        at,
                        at,
                        "Rename one of them first.");
            }
        }
        if (declaration instanceof VariableTree variable) {
            for (Tree sibling : ((ClassTree) path.getParentPath().getLeaf()).getMembers()) {
                if (sibling != variable && compilation.start(sourceUnit, sibling) == declarationStart) {
                    return Outcome.refuse(
                            RefusalCodes.MULTIPLE_DECLARATORS,
                            "The field is declared together with others in one declaration.",
                            sourceFile,
                            at,
                            at,
                            "Split the declaration first.");
                }
            }
            if (variable.getInitializer() != null
                    && !isFreeOfObservableEffects(new TreePath(path, variable.getInitializer()))) {
                return Outcome.refuse(
                        RefusalCodes.STATIC_INITIALIZATION,
                        "The field's initializer may have observable effects, which would run when the target class is initialized instead.",
                        sourceFile,
                        at,
                        at);
            }
        }
        for (TypeElement type : List.of(source, target)) {
            String problem = staticInitializationProblem(type);
            if (problem != null) {
                return Outcome.refuse(
                        RefusalCodes.STATIC_INITIALIZATION,
                        "Using the member initializes its class, and " + type.getSimpleName()
                                + "'s static initialization " + problem + ", which would then run at a different time.",
                        sourceFile,
                        at,
                        at);
            }
        }
        return null;
    }

    private static boolean isMain(ExecutableElement method) {
        return method.getSimpleName().contentEquals("main");
    }

    /** Null if the type's static initialization is free of observable effects; otherwise what may be observable. */
    private String staticInitializationProblem(TypeElement type) {
        if (type.getKind() == ElementKind.ENUM) {
            return "creates enum constants";
        }
        TreePath path = compilation.trees.getPath(type);
        if (!(path.getLeaf() instanceof ClassTree classTree)) {
            return "is unknown";
        }
        for (Tree memberTree : classTree.getMembers()) {
            if (memberTree instanceof BlockTree block && block.isStatic()) {
                return "has a static initializer block";
            }
            if (memberTree instanceof VariableTree field
                    && field.getModifiers().getFlags().contains(Modifier.STATIC)
                    && field.getInitializer() != null
                    && !isFreeOfObservableEffects(new TreePath(new TreePath(path, field), field.getInitializer()))) {
                return "has a static field initializer with possible effects ('" + field.getName() + "')";
            }
        }
        return null;
    }

    /**
     * Conservatively: a constant, a literal, null, an array of such, or creating a java.* object with such arguments.
     */
    private boolean isFreeOfObservableEffects(TreePath path) {
        Tree tree = path.getLeaf();
        if (tree instanceof ExpressionTree expression) {
            Element element = compilation.trees.getElement(path);
            if (element instanceof VariableElement variable && variable.getConstantValue() != null) {
                return true;
            }
            return switch (Support.unparenthesized(expression)) {
                case LiteralTree literal -> true;
                case NewArrayTree array ->
                    array.getInitializers() == null
                            ? array.getDimensions().stream()
                                    .allMatch(d -> isFreeOfObservableEffects(new TreePath(path, d)))
                            : array.getInitializers().stream()
                                    .allMatch(i -> isFreeOfObservableEffects(new TreePath(path, i)));
                case NewClassTree creation ->
                    creation.getClassBody() == null
                            && compilation.trees.getElement(path) instanceof ExecutableElement constructor
                            && Support.packageOf(constructor).startsWith("java.")
                            && creation.getArguments().stream()
                                    .allMatch(a -> isFreeOfObservableEffects(new TreePath(path, a)));
                default -> false;
            };
        }
        return false;
    }

    /** Every reference names the target; static imports of the member import it from the target. */
    private void rewriteReferences() {
        for (var entry : compilation.unitsByFile.entrySet()) {
            String file = entry.getKey();
            CompilationUnitTree unit = entry.getValue();
            String targetReference = support.typeReference(target, file);
            for (ImportTree importTree : unit.getImports()) {
                rewriteStaticImport(file, unit, importTree);
            }
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitImport(ImportTree tree, Void unused) {
                    return null;
                }

                @Override
                public Void visitIdentifier(IdentifierTree tree, Void unused) {
                    if (member.equals(compilation.trees.getElement(getCurrentPath()))
                            && !(isThroughSingleStaticImport(getCurrentPath()) && hasSingleStaticImport(unit))) {
                        int start = (int) compilation.start(unit, tree);
                        add(file, TextEdit.insert(file, start, targetReference + "."));
                    }
                    return null;
                }

                @Override
                public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                    if (member.equals(compilation.trees.getElement(getCurrentPath()))) {
                        rewriteQualifier(
                                file, unit, new TreePath(getCurrentPath(), tree.getExpression()), targetReference);
                        return null;
                    }
                    return super.visitMemberSelect(tree, unused);
                }

                @Override
                public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
                    if (member.equals(compilation.trees.getElement(getCurrentPath()))) {
                        rewriteQualifier(
                                file,
                                unit,
                                new TreePath(getCurrentPath(), tree.getQualifierExpression()),
                                targetReference);
                        return null;
                    }
                    return super.visitMemberReference(tree, unused);
                }
            }.scan(new TreePath(unit), null);
        }
    }

    /** An unqualified use outside the source's scope finds the member through a static import. */
    private boolean isThroughSingleStaticImport(TreePath path) {
        for (TreePath p = path; p != null; p = p.getParentPath()) {
            if (p.getLeaf() instanceof ClassTree && compilation.trees.getElement(p) instanceof TypeElement type) {
                if (compilation.types.isSubtype(
                        compilation.types.erasure(type.asType()), compilation.types.erasure(source.asType()))) {
                    return false;
                }
                if (type.equals(source) || isNestedIn(type, source)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNestedIn(TypeElement type, TypeElement outer) {
        for (Element e = type.getEnclosingElement(); e != null; e = e.getEnclosingElement()) {
            if (e.equals(outer)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSingleStaticImport(CompilationUnitTree unit) {
        for (ImportTree importTree : unit.getImports()) {
            if (importTree.isStatic()
                    && importTree.getQualifiedIdentifier() instanceof MemberSelectTree select
                    && select.getIdentifier().contentEquals(member.getSimpleName())
                    && source.getQualifiedName()
                            .contentEquals(select.getExpression().toString())) {
                return true;
            }
        }
        return false;
    }

    private void rewriteQualifier(String file, CompilationUnitTree unit, TreePath qualifier, String targetReference) {
        Element element = compilation.trees.getElement(qualifier);
        int start = (int) compilation.start(unit, qualifier.getLeaf());
        int end = (int) compilation.end(unit, qualifier.getLeaf());
        if (!(element instanceof TypeElement) && !support.isSideEffectFree(qualifier)) {
            refuse(
                    RefusalCodes.RECEIVER_HAS_EFFECTS,
                    "The member is used through an expression that may have effects; the static reference evaluates it, the rewritten one would not.",
                    file,
                    start,
                    end,
                    "Store the expression in a local first.");
            return;
        }
        add(file, new TextEdit(file, start, end, targetReference));
    }

    private void rewriteStaticImport(String file, CompilationUnitTree unit, ImportTree importTree) {
        if (!importTree.isStatic()
                || !(importTree.getQualifiedIdentifier() instanceof MemberSelectTree select)
                || !select.getIdentifier().contentEquals(member.getSimpleName())) {
            return;
        }
        TreePath qualifierPath = new TreePath(
                new TreePath(new TreePath(new TreePath(unit), importTree), select), select.getExpression());
        if (!source.equals(compilation.trees.getElement(qualifierPath))) {
            return;
        }
        boolean othersRemain = source.getEnclosedElements().stream()
                .anyMatch(e -> !e.equals(member)
                        && Support.isStatic(e)
                        && e.getSimpleName().contentEquals(member.getSimpleName()));
        String newImport = "import static " + target.getQualifiedName() + "." + member.getSimpleName() + ";";
        if (othersRemain) {
            int end = (int) compilation.end(unit, importTree);
            add(file, TextEdit.insert(file, end, "\n" + newImport));
        } else {
            add(
                    file,
                    new TextEdit(
                            file,
                            (int) compilation.start(unit, select.getExpression()),
                            (int) compilation.end(unit, select.getExpression()),
                            target.getQualifiedName().toString()));
        }
    }

    private void add(String file, TextEdit edit) {
        if (mover.isInside(file, edit)) {
            mover.innerEdits.add(edit);
        } else {
            edits.add(edit);
        }
    }
}
