package io.github.riktigrefactor.java.movetoinstance;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
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
import io.github.riktigrefactor.java.analysis.Token;
import io.github.riktigrefactor.java.analysis.Verification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Turns a static method into an instance method of the class of one of its parameters: the parameter becomes the
 * receiver, and every call {@code S.m(a, recv)} becomes {@code recv.m(a)}.
 */
public final class MoveToInstanceMethod {
    private final Compilation compilation;
    private final Support support;
    private final List<TextEdit> edits = new ArrayList<>();
    private ExecutableElement method;
    private VariableElement receiver;
    private int receiverIndex;
    private TypeElement target;
    private MemberMove mover;
    private Outcome refusal;

    private MoveToInstanceMethod(Compilation compilation) {
        this.compilation = compilation;
        this.support = new Support(compilation);
    }

    public static Outcome run(Map<String, String> sources, String file, int cursor, String receiverName) {
        return run(sources, file, cursor, receiverName, false);
    }

    /** The deliberately naive version: the same edits with no preconditions and no verification. */
    public static Outcome runNaive(Map<String, String> sources, String file, int cursor, String receiverName) {
        return run(sources, file, cursor, receiverName, true);
    }

    private static Outcome run(
            Map<String, String> sources, String file, int cursor, String receiverName, boolean naive) {
        Prepared prepared = Prepared.analyze(sources);
        if (prepared.refusal() != null) {
            return prepared.refusal();
        }
        return new MoveToInstanceMethod(prepared.compilation()).move(file, cursor, receiverName, naive);
    }

    private void refuse(String code, String message, String file, long start, long end, String suggestion) {
        if (refusal == null) {
            refusal = Outcome.refuse(code, message, file, (int) start, (int) end, suggestion);
        }
    }

    private Outcome move(String cursorFile, int cursor, String receiverName, boolean naive) {
        Element found = null;
        for (var occurrence : Occurrences.find(compilation)) {
            if (occurrence.file().equals(cursorFile)
                    && occurrence.token().start() <= cursor
                    && cursor <= occurrence.token().end()) {
                found = occurrence.element();
                break;
            }
        }
        if (!(found instanceof ExecutableElement executable) || found.getKind() != ElementKind.METHOD) {
            return Outcome.refuse(
                    RefusalCodes.NO_SYMBOL, "There is no method at the cursor.", cursorFile, cursor, cursor);
        }
        method = executable;
        SourceTypes sourceTypes = new SourceTypes(compilation);
        if (!sourceTypes.isInSource(method)) {
            return Outcome.refuse(
                    RefusalCodes.NOT_RENAMEABLE,
                    "The method is not declared in the workspace.",
                    cursorFile,
                    cursor,
                    cursor);
        }
        TreePath path = compilation.trees.getPath(method);
        CompilationUnitTree unit = path.getCompilationUnit();
        String file = compilation.fileOfUnit.get(unit);
        MethodTree declaration = (MethodTree) path.getLeaf();
        int at = (int) compilation.start(unit, declaration);
        if (!Support.isStatic(method)) {
            return Outcome.refuse(
                    RefusalCodes.NOT_STATIC,
                    "The method is not static.",
                    file,
                    at,
                    at,
                    "Use Make method static first.");
        }
        receiverIndex = -1;
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (method.getParameters().get(i).getSimpleName().contentEquals(String.valueOf(receiverName))) {
                receiverIndex = i;
            }
        }
        if (receiverIndex < 0) {
            return Outcome.refuse(
                    RefusalCodes.NO_SYMBOL, "The method has no parameter named '" + receiverName + "'.", file, at, at);
        }
        receiver = method.getParameters().get(receiverIndex);
        TypeMirror receiverType = receiver.asType();
        if (!(receiverType instanceof DeclaredType declared)
                || !(declared.asElement() instanceof TypeElement type)
                || !(type.getKind() == ElementKind.CLASS || type.getKind() == ElementKind.RECORD)
                || !sourceTypes.isInSource(type)) {
            return Outcome.refuse(
                    RefusalCodes.TARGET_NOT_SUPPORTED,
                    "The receiver's type must be a class or record declared in the workspace.",
                    file,
                    at,
                    at);
        }
        target = type;
        if (!naive) {
            Outcome checked = check(declaration, unit, file, at, sourceTypes);
            if (checked != null) {
                return checked;
            }
        }
        mover = new MemberMove(compilation, method, target);
        mover.qualifySourceScope(path);
        rewriteDeclaration(declaration, unit, file, sourceTypes);
        rewriteReferences(naive);
        if (refusal != null && !naive) {
            return refusal;
        }
        mover.moveDeclaration(edits);
        if (naive) {
            return new Outcome.Success(edits, List.of());
        }
        String methodKey = Bindings.of(compilation, Map.of()).elementKey(method);
        Verification verification = Verification.verify(
                compilation,
                edits,
                true,
                error -> switch (String.valueOf(error.getCode())) {
                    case "compiler.err.report.access", "compiler.err.not.def.public.cant.access" ->
                        RefusalCodes.INACCESSIBLE;
                    default -> RefusalCodes.WOULD_INTRODUCE_ERRORS;
                },
                Verification.Mode.STRUCTURAL,
                old -> Verification.elementIs(old.binding(), methodKey)
                        ? methodKey + Verification.ANY_TYPE
                        : old.binding());
        if (verification.refusal != null) {
            return new Outcome.Refused(verification.refusal);
        }
        return new Outcome.Success(edits, List.of());
    }

    private Outcome check(
            MethodTree declaration, CompilationUnitTree unit, String file, int at, SourceTypes sourceTypes) {
        if (!method.getTypeParameters().isEmpty()) {
            return Outcome.refuse(RefusalCodes.TYPE_PARAMETERS, "Generic methods are not supported yet.", file, at, at);
        }
        if (method.isVarArgs() && receiverIndex == method.getParameters().size() - 1) {
            return Outcome.refuse(
                    RefusalCodes.INVALID_RECEIVER, "The receiver parameter is a varargs parameter.", file, at, at);
        }
        if (target.getTypeParameters().size() > 0) {
            return Outcome.refuse(
                    RefusalCodes.TYPE_PARAMETERS, "Moving into a generic class is not supported yet.", file, at, at);
        }
        for (String objectMethod : List.of(
                "equals", "hashCode", "toString", "getClass", "clone", "finalize", "notify", "notifyAll", "wait")) {
            if (method.getSimpleName().contentEquals(objectMethod)) {
                return Outcome.refuse(
                        RefusalCodes.NAME_IN_HIERARCHY,
                        "'" + objectMethod + "' is a method of Object, which the runtime and libraries call.",
                        file,
                        at,
                        at);
            }
        }
        List<TypeElement> hierarchy = new ArrayList<>(sourceTypes.supertypes(target));
        hierarchy.add(target);
        for (TypeElement type : sourceTypes.all) {
            if (sourceTypes.supertypes(type).contains(target)) {
                hierarchy.add(type);
            }
        }
        for (TypeElement type : hierarchy) {
            for (Element existing : type.getEnclosedElements()) {
                if (existing.getSimpleName().contentEquals(method.getSimpleName())
                        && existing.getKind() == ElementKind.METHOD) {
                    return Outcome.refuse(
                            RefusalCodes.NAME_IN_HIERARCHY,
                            type.getSimpleName() + " already declares a method named '" + method.getSimpleName()
                                    + "', which the new method would overload, override or be overridden by.",
                            file,
                            at,
                            at,
                            "Rename the method first.");
                }
            }
        }
        if (!support.isNeverReassigned(receiver)) {
            return Outcome.refuse(
                    RefusalCodes.INVALID_RECEIVER, "The body assigns the receiver parameter.", file, at, at);
        }
        for (ImportTree importTree : compilation.unitsByFile.values().stream()
                .flatMap(u -> u.getImports().stream())
                .toList()) {
            if (importTree.isStatic()
                    && importTree.getQualifiedIdentifier() instanceof MemberSelectTree select
                    && select.getIdentifier().contentEquals(method.getSimpleName())) {
                return Outcome.refuse(
                        RefusalCodes.STATIC_IMPORT_SHARED,
                        "The method is statically imported somewhere, and an instance method can't be.",
                        file,
                        at,
                        at);
            }
        }
        return null;
    }

    /** Drops `static` and the receiver parameter; uses of the parameter become `this`. */
    private void rewriteDeclaration(
            MethodTree declaration, CompilationUnitTree unit, String file, SourceTypes sourceTypes) {
        boolean hasSubclasses = sourceTypes.all.stream()
                .anyMatch(t -> !t.equals(target) && sourceTypes.supertypes(t).contains(target));
        long modifiersStart = compilation.start(unit, declaration);
        Token staticToken = null;
        for (Token token : compilation.tokensByFile.get(file)) {
            if (token.start() >= modifiersStart && token.text().equals("static")) {
                staticToken = token;
                break;
            }
        }
        String text = compilation.sources.get(file);
        int afterStatic = staticToken.end();
        while (afterStatic < text.length() && text.charAt(afterStatic) == ' ') {
            afterStatic++;
        }
        mover.innerEdits.add(new TextEdit(file, staticToken.start(), afterStatic, hasSubclasses ? "final " : ""));
        List<? extends VariableTree> parameters = declaration.getParameters();
        VariableTree receiverTree = parameters.get(receiverIndex);
        int removeStart;
        int removeEnd;
        if (parameters.size() == 1) {
            removeStart = (int) compilation.start(unit, receiverTree);
            removeEnd = (int) compilation.end(unit, receiverTree);
        } else if (receiverIndex + 1 < parameters.size()) {
            removeStart = (int) compilation.start(unit, receiverTree);
            removeEnd = (int) compilation.start(unit, parameters.get(receiverIndex + 1));
        } else {
            removeStart = (int) compilation.end(unit, parameters.get(receiverIndex - 1));
            removeEnd = (int) compilation.end(unit, receiverTree);
        }
        int removedFrom = removeStart;
        int removedTo = removeEnd;
        mover.innerEdits.removeIf(e -> removedFrom <= e.start() && e.end() <= removedTo);
        mover.innerEdits.add(new TextEdit(file, removeStart, removeEnd, ""));
        if (declaration.getBody() == null) {
            return;
        }
        new TreePathScanner<Void, Integer>() {
            @Override
            public Void visitClass(ClassTree tree, Integer depth) {
                return super.visitClass(tree, depth + 1);
            }

            @Override
            public Void visitIdentifier(IdentifierTree tree, Integer depth) {
                if (receiver.equals(compilation.trees.getElement(getCurrentPath()))
                        && !isReceiverOfRewrittenCall(getCurrentPath())) {
                    int start = (int) compilation.start(unit, tree);
                    if (depth > 0) {
                        refuse(
                                RefusalCodes.RECEIVER_IN_NESTED_CLASS,
                                "The receiver is used inside a nested class, where `this` would mean that class.",
                                file,
                                start,
                                start,
                                null);
                    }
                    mover.innerEdits.add(
                            new TextEdit(file, start, start + tree.getName().length(), "this"));
                }
                return null;
            }
        }.scan(new TreePath(compilation.trees.getPath(method), declaration.getBody()), 0);
    }

    /** The receiver argument of a recursive call, which the call's own rewrite replaces. */
    private boolean isReceiverOfRewrittenCall(TreePath path) {
        return path.getParentPath().getLeaf() instanceof MethodInvocationTree call
                && method.equals(compilation.trees.getElement(path.getParentPath()))
                && call.getArguments().get(receiverIndex) == path.getLeaf();
    }

    private void rewriteReferences(boolean naive) {
        for (var entry : compilation.unitsByFile.entrySet()) {
            String file = entry.getKey();
            CompilationUnitTree unit = entry.getValue();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                    if (method.equals(compilation.trees.getElement(getCurrentPath()))) {
                        rewriteCall(file, unit, getCurrentPath(), tree, naive);
                    }
                    return super.visitMethodInvocation(tree, unused);
                }

                @Override
                public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
                    if (method.equals(compilation.trees.getElement(getCurrentPath()))) {
                        if (receiverIndex != 0) {
                            refuse(
                                    RefusalCodes.METHOD_REFERENCE_SHAPE,
                                    "A method reference needs the receiver to be the first parameter to become an unbound reference.",
                                    file,
                                    compilation.start(unit, tree),
                                    compilation.end(unit, tree),
                                    null);
                        } else {
                            ExpressionTree qualifier = tree.getQualifierExpression();
                            add(
                                    file,
                                    new TextEdit(
                                            file,
                                            (int) compilation.start(unit, qualifier),
                                            (int) compilation.end(unit, qualifier),
                                            support.typeReference(target, file)));
                        }
                    }
                    return super.visitMemberReference(tree, unused);
                }
            }.scan(new TreePath(unit), null);
        }
    }

    private void rewriteCall(
            String file, CompilationUnitTree unit, TreePath path, MethodInvocationTree call, boolean naive) {
        List<? extends ExpressionTree> arguments = call.getArguments();
        ExpressionTree receiverArgument = arguments.get(receiverIndex);
        TreePath receiverPath = new TreePath(path, receiverArgument);
        long callStart = compilation.start(unit, call);
        if (!naive) {
            boolean isTheReceiverItself = receiverArgument instanceof IdentifierTree
                    && receiver.equals(compilation.trees.getElement(receiverPath));
            if (!isTheReceiverItself && !support.isProvablyNonNull(receiverPath)) {
                refuse(
                        RefusalCodes.RECEIVER_MAY_BE_NULL,
                        "The receiver argument may be null: the static method runs its body, but the instance call would throw before it.",
                        file,
                        compilation.start(unit, receiverArgument),
                        compilation.end(unit, receiverArgument),
                        "Store the receiver in a local initialized with `new`, or check it for null first.");
                return;
            }
            TypeMirror argumentType = compilation.trees.getTypeMirror(receiverPath);
            if (argumentType == null
                    || !compilation.types.isSubtype(
                            compilation.types.erasure(argumentType), compilation.types.erasure(target.asType()))) {
                refuse(
                        RefusalCodes.INVALID_RECEIVER,
                        "The receiver argument needs a conversion that is not a reference widening.",
                        file,
                        callStart,
                        callStart,
                        null);
                return;
            }
            for (int i = 0; i < receiverIndex; i++) {
                if (!support.areIndependent(new TreePath(path, arguments.get(i)), receiverPath)) {
                    refuse(
                            RefusalCodes.EVALUATION_ORDER,
                            "The receiver argument would be evaluated before the arguments written before it, which may depend on each other.",
                            file,
                            compilation.start(unit, arguments.get(i)),
                            compilation.end(unit, arguments.get(i)),
                            null);
                    return;
                }
            }
            if (call.getMethodSelect() instanceof MemberSelectTree select
                    && !(compilation.trees.getElement(new TreePath(new TreePath(path, select), select.getExpression()))
                            instanceof TypeElement)
                    && !support.isSideEffectFree(new TreePath(new TreePath(path, select), select.getExpression()))) {
                refuse(
                        RefusalCodes.RECEIVER_HAS_EFFECTS,
                        "The static call goes through an expression that may have effects, which the instance call would not evaluate.",
                        file,
                        callStart,
                        callStart,
                        null);
                return;
            }
        }
        int receiverStart = (int) compilation.start(unit, receiverArgument);
        int receiverEnd = (int) compilation.end(unit, receiverArgument);
        boolean isPrimary = Support.unparenthesized(receiverArgument) == receiverArgument
                        && (receiverArgument instanceof IdentifierTree
                                || receiverArgument instanceof MemberSelectTree
                                || receiverArgument instanceof MethodInvocationTree
                                || receiverArgument instanceof LiteralTree)
                || receiverArgument instanceof ParenthesizedTree;
        boolean isCreation = receiverArgument instanceof NewClassTree;
        String prefix = isPrimary || isCreation ? "" : "(";
        String suffix = (isPrimary || isCreation ? "" : ")") + ".";
        boolean receiverInsideMovedMethod = mover.isInside(file, new TextEdit(file, receiverStart, receiverEnd, ""));
        if (receiverInsideMovedMethod
                && !(receiverArgument instanceof IdentifierTree identifier
                        && receiver.equals(compilation.trees.getElement(receiverPath)))) {
            refuse(
                    RefusalCodes.RECURSIVE_CALL_RECEIVER,
                    "A recursive call passes a receiver other than the receiver parameter itself, which is not supported yet.",
                    file,
                    receiverStart,
                    receiverEnd,
                    null);
            return;
        }
        if (call.getMethodSelect() instanceof MemberSelectTree select) {
            int qualifierStart = (int) compilation.start(unit, select.getExpression());
            int nameStart = (int)
                    (compilation.end(unit, select) - select.getIdentifier().length());
            add(
                    file,
                    copyReceiver(
                            file,
                            qualifierStart,
                            nameStart,
                            receiverStart,
                            receiverEnd,
                            prefix,
                            suffix,
                            receiverInsideMovedMethod));
        } else {
            int nameStart = (int) compilation.start(unit, call.getMethodSelect());
            add(
                    file,
                    copyReceiver(
                            file,
                            nameStart,
                            nameStart,
                            receiverStart,
                            receiverEnd,
                            prefix,
                            suffix,
                            receiverInsideMovedMethod));
        }
        int removeStart;
        int removeEnd;
        if (arguments.size() == 1) {
            removeStart = receiverStart;
            removeEnd = receiverEnd;
        } else if (receiverIndex + 1 < arguments.size()) {
            removeStart = receiverStart;
            removeEnd = (int) compilation.start(unit, arguments.get(receiverIndex + 1));
        } else {
            removeStart = (int) compilation.end(unit, arguments.get(receiverIndex - 1));
            removeEnd = receiverEnd;
        }
        add(file, new TextEdit(file, removeStart, removeEnd, ""));
    }

    /**
     * The receiver argument, copied to the receiver position. Inside the moved method the copy is plain text, since a
     * copy edit can't nest in another; the parameter there is rewritten to `this` anyway.
     */
    private TextEdit copyReceiver(
            String file,
            int start,
            int end,
            int receiverStart,
            int receiverEnd,
            String prefix,
            String suffix,
            boolean inside) {
        if (inside) {
            return new TextEdit(file, start, end, prefix + "this" + suffix);
        }
        return TextEdit.copy(
                compilation.sources, file, start, end, file, receiverStart, receiverEnd, prefix, suffix, List.of());
    }

    private void add(String file, TextEdit edit) {
        if (mover.isInside(file, edit)) {
            mover.innerEdits.add(edit);
        } else {
            edits.add(edit);
        }
    }
}
