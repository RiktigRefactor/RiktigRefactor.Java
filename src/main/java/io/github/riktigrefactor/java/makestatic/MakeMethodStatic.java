package io.github.riktigrefactor.java.makestatic;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.RefusalCodes;
import io.github.riktigrefactor.java.analysis.Bindings;
import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.Hierarchy;
import io.github.riktigrefactor.java.analysis.Occurrences;
import io.github.riktigrefactor.java.analysis.Prepared;
import io.github.riktigrefactor.java.analysis.Support;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.analysis.Token;
import io.github.riktigrefactor.java.analysis.Verification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;

/**
 * Turns an instance method into a static method of the same class, taking the receiver as its first parameter, and
 * rewrites every call {@code recv.m(args)} to {@code Type.m(recv, args)}.
 */
public final class MakeMethodStatic {
    private final Compilation compilation;
    private final Support support;
    private final List<TextEdit> edits = new ArrayList<>();
    private ExecutableElement method;
    private TypeElement owner;
    private String receiverName;
    private String file;
    private MethodTree declaration;
    private CompilationUnitTree declarationUnit;
    private Outcome refusal;

    private MakeMethodStatic(Compilation compilation) {
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
        return new MakeMethodStatic(prepared.compilation()).make(file, cursor, receiverName, naive);
    }

    private void refuse(String code, String message, String atFile, long start, long end, String suggestion) {
        if (refusal == null) {
            refusal = Outcome.refuse(code, message, atFile, (int) start, (int) end, suggestion);
        }
    }

    private Outcome make(String cursorFile, int cursor, String requestedReceiverName, boolean naive) {
        Element target = null;
        for (var occurrence : Occurrences.find(compilation)) {
            if (occurrence.file().equals(cursorFile)
                    && occurrence.token().start() <= cursor
                    && cursor <= occurrence.token().end()) {
                target = occurrence.element();
                break;
            }
        }
        if (!(target instanceof ExecutableElement executable) || target.getKind() != ElementKind.METHOD) {
            return Outcome.refuse(
                    RefusalCodes.NO_SYMBOL, "There is no method at the cursor.", cursorFile, cursor, cursor);
        }
        method = executable;
        owner = (TypeElement) method.getEnclosingElement();
        TreePath path = compilation.trees.getPath(method);
        if (path == null || !compilation.fileOfUnit.containsKey(path.getCompilationUnit())) {
            return Outcome.refuse(
                    RefusalCodes.NOT_RENAMEABLE,
                    "The method is not declared in the workspace.",
                    cursorFile,
                    cursor,
                    cursor);
        }
        declarationUnit = path.getCompilationUnit();
        file = compilation.fileOfUnit.get(declarationUnit);
        declaration = (MethodTree) path.getLeaf();
        long declarationStart = compilation.start(declarationUnit, declaration);
        receiverName = requestedReceiverName != null
                ? requestedReceiverName
                : Support.lowerCamel(owner.getSimpleName().toString());
        if (!naive) {
            Outcome checked = checkDeclaration(declarationStart);
            if (checked != null) {
                return checked;
            }
        }
        String ownerReference = support.typeReference(owner, file);
        if (ownerReference == null) {
            return Outcome.refuse(
                    RefusalCodes.NOT_RENAMEABLE,
                    "The method's class is anonymous, so a static call can't name it.",
                    file,
                    (int) declarationStart,
                    (int) declarationStart);
        }
        rewriteDeclaration(ownerReference);
        rewriteBody();
        rewriteReferences();
        if (refusal != null && !naive) {
            return refusal;
        }
        if (naive) {
            return new Outcome.Success(edits, List.of());
        }
        Bindings original = Bindings.of(compilation, Map.of());
        List<String> classTypeParameterKeys = new ArrayList<>();
        for (var parameter : owner.getTypeParameters()) {
            classTypeParameterKeys.add(original.elementKey(parameter));
        }
        String methodKey = original.elementKey(method);
        long methodStart = declarationStart;
        long methodEnd = compilation.end(declarationUnit, declaration);
        Verification verification = Verification.verify(
                compilation,
                edits,
                true,
                error -> String.valueOf(error.getCode()).startsWith("compiler.err.already.defined")
                        ? RefusalCodes.CONFLICTING_DECLARATION
                        : RefusalCodes.WOULD_INTRODUCE_ERRORS,
                Verification.Mode.STRUCTURAL,
                old -> {
                    String binding = old.binding();
                    if (Verification.elementIs(binding, methodKey)) {
                        return methodKey + Verification.ANY_TYPE;
                    }
                    if (old.file().equals(file) && methodStart <= old.start() && old.end() <= methodEnd) {
                        for (int i = 0; i < classTypeParameterKeys.size(); i++) {
                            binding = Verification.replaceElement(
                                    binding,
                                    classTypeParameterKeys.get(i),
                                    "inserted TYPE_PARAMETER "
                                            + owner.getTypeParameters().get(i).getSimpleName());
                        }
                    }
                    return binding;
                });
        if (verification.refusal != null) {
            return new Outcome.Refused(verification.refusal);
        }
        return new Outcome.Success(edits, List.of());
    }

    private Outcome checkDeclaration(long start) {
        int at = (int) start;
        if (!SourceVersion.isIdentifier(receiverName) || SourceVersion.isKeyword(receiverName)) {
            return Outcome.refuse(
                    RefusalCodes.INVALID_IDENTIFIER,
                    "'" + receiverName + "' is not a valid parameter name.",
                    file,
                    at,
                    at);
        }
        if (Support.isStatic(method)) {
            return Outcome.refuse(RefusalCodes.ALREADY_STATIC, "The method is already static.", file, at, at);
        }
        if (method.getModifiers().contains(Modifier.ABSTRACT)
                || method.getModifiers().contains(Modifier.DEFAULT)
                || owner.getKind() == ElementKind.INTERFACE) {
            return Outcome.refuse(
                    RefusalCodes.DYNAMIC_DISPATCH,
                    "The method is abstract or an interface method, so calls to it are dispatched dynamically.",
                    file,
                    at,
                    at);
        }
        if (method.getModifiers().contains(Modifier.SYNCHRONIZED)
                || method.getModifiers().contains(Modifier.NATIVE)) {
            return Outcome.refuse(
                    RefusalCodes.SYNCHRONIZED_OR_NATIVE,
                    "The method is synchronized (it locks its receiver) or native (found by name), which a static method can't keep.",
                    file,
                    at,
                    at);
        }
        Hierarchy.Result hierarchy = new Hierarchy(compilation).of(method);
        if (hierarchy.methods().size() > 1 || hierarchy.externalOverridden() != null) {
            return Outcome.refuse(
                    RefusalCodes.DYNAMIC_DISPATCH,
                    "The method overrides or is overridden by another method, so calls to it are dispatched dynamically.",
                    file,
                    at,
                    at);
        }
        for (var classParameter : owner.getTypeParameters()) {
            for (var methodParameter : method.getTypeParameters()) {
                if (classParameter.getSimpleName().contentEquals(methodParameter.getSimpleName())) {
                    return Outcome.refuse(
                            RefusalCodes.CONFLICTING_DECLARATION,
                            "The method's type parameter " + methodParameter.getSimpleName()
                                    + " has the name of its class's type parameter, which the static method needs too.",
                            file,
                            at,
                            at);
                }
            }
        }
        return null;
    }

    private void rewriteDeclaration(String ownerReference) {
        long nameStart = new io.github.riktigrefactor.java.analysis.NameLocator(compilation)
                .nameToken(declarationUnit, declaration, declaration.getName().toString())
                .start();
        long insertAt = declaration.getTypeParameters().isEmpty()
                ? compilation.start(declarationUnit, declaration.getReturnType())
                : compilation.start(
                                declarationUnit, declaration.getTypeParameters().getFirst())
                        - 1;
        String classTypeParameters = classTypeParametersText();
        String prefix = "static ";
        if (!classTypeParameters.isEmpty() && declaration.getTypeParameters().isEmpty()) {
            prefix += "<" + classTypeParameters + "> ";
        }
        edits.add(TextEdit.insert(file, (int) insertAt, prefix));
        if (!classTypeParameters.isEmpty() && !declaration.getTypeParameters().isEmpty()) {
            edits.add(TextEdit.insert(file, (int) insertAt + 1, classTypeParameters + ", "));
        }
        Token open = support.tokenAtOrAfter(file, nameStart, "(");
        String receiverType = ownerReference
                + (owner.getTypeParameters().isEmpty()
                        ? ""
                        : owner.getTypeParameters().stream()
                                .map(p -> p.getSimpleName().toString())
                                .collect(java.util.stream.Collectors.joining(", ", "<", ">")));
        edits.add(TextEdit.insert(
                file,
                open.end(),
                receiverType + " " + receiverName + (declaration.getParameters().isEmpty() ? "" : ", ")));
    }

    private String classTypeParametersText() {
        TreePath ownerPath = compilation.trees.getPath(owner);
        if (!(ownerPath.getLeaf() instanceof ClassTree ownerTree)) {
            return "";
        }
        List<String> parameters = new ArrayList<>();
        for (TypeParameterTree parameter : ownerTree.getTypeParameters()) {
            parameters.add(support.textOf(compilation.fileOfUnit.get(ownerPath.getCompilationUnit()), parameter));
        }
        return String.join(", ", parameters);
    }

    /** Uses of the receiver in the body, explicit and implicit, become uses of the new parameter. */
    private void rewriteBody() {
        if (declaration.getBody() == null) {
            return;
        }
        TreePath bodyPath = new TreePath(compilation.trees.getPath(method), declaration.getBody());
        new TreePathScanner<Void, List<TypeElement>>() {
            @Override
            public Void visitClass(ClassTree tree, List<TypeElement> nested) {
                Element element = compilation.trees.getElement(getCurrentPath());
                List<TypeElement> inner = new ArrayList<>(nested);
                if (element instanceof TypeElement type) {
                    inner.add(type);
                }
                return super.visitClass(tree, inner);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree tree, List<TypeElement> nested) {
                if (nested.isEmpty() && tree.getIdentifier().contentEquals("this")) {
                    refuseAt(RefusalCodes.QUALIFIED_THIS, "The method uses a qualified `this`.", tree);
                }
                return super.visitMemberSelect(tree, nested);
            }

            @Override
            public Void visitNewClass(NewClassTree tree, List<TypeElement> nested) {
                Element created = compilation.trees.getElement(new TreePath(getCurrentPath(), tree.getIdentifier()));
                if (tree.getEnclosingExpression() == null
                        && created instanceof TypeElement type
                        && type.getNestingKind() == NestingKind.MEMBER
                        && !Support.isStatic(type)
                        && nested.isEmpty()) {
                    refuseAt(
                            RefusalCodes.INNER_CLASS_CREATION,
                            "The method creates an instance of the inner class " + type.getSimpleName()
                                    + ", which needs the receiver as its enclosing instance.",
                            tree);
                }
                return super.visitNewClass(tree, nested);
            }

            @Override
            public Void visitIdentifier(IdentifierTree tree, List<TypeElement> nested) {
                TreePath path = getCurrentPath();
                Element element = compilation.trees.getElement(path);
                int start = (int) compilation.start(declarationUnit, tree);
                if (tree.getName().contentEquals("this")) {
                    boolean isReceiverOfCallToMethod = path.getParentPath().getLeaf() instanceof MemberSelectTree
                            && path.getParentPath().getParentPath().getLeaf() instanceof MethodInvocationTree call
                            && method.equals(compilation.trees.getElement(
                                    path.getParentPath().getParentPath()));
                    if (nested.isEmpty() && !isReceiverOfCallToMethod) {
                        edits.add(new TextEdit(file, start, start + 4, receiverName));
                    }
                } else if (tree.getName().contentEquals("super")) {
                    if (nested.isEmpty()) {
                        refuseAt(
                                RefusalCodes.SUPER_ACCESS,
                                "The method uses `super`, which a static method can't.",
                                tree);
                    }
                } else if (element != null
                        && !element.equals(method)
                        && (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.METHOD)
                        && !Support.isStatic(element)
                        && element.getEnclosingElement() instanceof TypeElement memberOwner) {
                    boolean nestedInherits = nested.stream().anyMatch(n -> inherits(n, memberOwner));
                    if (nestedInherits) {
                        return null;
                    }
                    if (inherits(owner, memberOwner)) {
                        edits.add(TextEdit.insert(file, start, receiverName + "."));
                    } else {
                        refuseAt(
                                RefusalCodes.OUTER_INSTANCE_ACCESS,
                                "The method uses '" + tree.getName() + "' of an enclosing instance.",
                                tree);
                    }
                }
                return null;
            }
        }.scan(bodyPath, List.of());
    }

    private boolean inherits(TypeElement type, TypeElement memberOwner) {
        return compilation.types.isSubtype(
                compilation.types.erasure(type.asType()), compilation.types.erasure(memberOwner.asType()));
    }

    private void refuseAt(String code, String message, Tree tree) {
        refuse(
                code,
                message,
                file,
                compilation.start(declarationUnit, tree),
                compilation.end(declarationUnit, tree),
                null);
    }

    /** Every call and method reference becomes a static call that passes the receiver first. */
    private void rewriteReferences() {
        long bodyStart = declaration.getBody() == null ? -1 : compilation.start(declarationUnit, declaration.getBody());
        long bodyEnd = declaration.getBody() == null ? -1 : compilation.end(declarationUnit, declaration.getBody());
        for (var entry : compilation.unitsByFile.entrySet()) {
            String unitFile = entry.getKey();
            CompilationUnitTree unit = entry.getValue();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                    if (method.equals(compilation.trees.getElement(getCurrentPath()))) {
                        rewriteCall(unitFile, unit, getCurrentPath(), tree);
                    }
                    return super.visitMethodInvocation(tree, unused);
                }

                @Override
                public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
                    if (method.equals(compilation.trees.getElement(getCurrentPath()))) {
                        Element qualifier = compilation.trees.getElement(
                                new TreePath(getCurrentPath(), tree.getQualifierExpression()));
                        if (!(qualifier instanceof TypeElement)) {
                            refuse(
                                    RefusalCodes.BOUND_METHOD_REFERENCE,
                                    "A method reference here binds its receiver when it is created; as a lambda it would be evaluated at a different time.",
                                    unitFile,
                                    compilation.start(unit, tree),
                                    compilation.end(unit, tree),
                                    null);
                        }
                    }
                    return super.visitMemberReference(tree, unused);
                }

                private void rewriteCall(
                        String callFile, CompilationUnitTree callUnit, TreePath path, MethodInvocationTree call) {
                    String typeName = support.typeReference(owner, callFile);
                    Token open =
                            support.tokenAtOrAfter(callFile, compilation.end(callUnit, call.getMethodSelect()), "(");
                    String separator = call.getArguments().isEmpty() ? "" : ", ";
                    long callStart = compilation.start(callUnit, call);
                    boolean inBody = callFile.equals(file) && bodyStart <= callStart && callStart < bodyEnd;
                    if (call.getMethodSelect() instanceof MemberSelectTree select) {
                        TreePath receiverPath = new TreePath(new TreePath(path, select), select.getExpression());
                        String receiverText = support.textOf(callFile, select.getExpression());
                        if (receiverText.equals("super") || receiverText.endsWith(".super")) {
                            refuse(
                                    RefusalCodes.SUPER_ACCESS,
                                    "A call through `super` can't become a static call here.",
                                    callFile,
                                    callStart,
                                    callStart,
                                    null);
                            return;
                        }
                        if (!support.isProvablyNonNull(receiverPath)) {
                            refuse(
                                    RefusalCodes.RECEIVER_MAY_BE_NULL,
                                    "The receiver '" + receiverText
                                            + "' may be null: the call throws before running the method, but the static method would run its body first.",
                                    callFile,
                                    compilation.start(callUnit, select.getExpression()),
                                    compilation.end(callUnit, select.getExpression()),
                                    "Store the receiver in a local initialized with `new`, or check it for null first.");
                            return;
                        }
                        if (inBody && receiverText.equals("this")) {
                            receiverText = receiverName;
                        }
                        long receiverStart = compilation.start(callUnit, select.getExpression());
                        long receiverEnd = compilation.end(callUnit, select.getExpression());
                        edits.add(new TextEdit(callFile, (int) receiverStart, (int) receiverEnd, typeName));
                        if (receiverText.equals(support.textOf(callFile, select.getExpression()))) {
                            edits.add(TextEdit.copy(
                                    compilation.sources,
                                    callFile,
                                    open.end(),
                                    open.end(),
                                    callFile,
                                    (int) receiverStart,
                                    (int) receiverEnd,
                                    "",
                                    separator,
                                    List.of()));
                        } else {
                            edits.add(TextEdit.insert(callFile, open.end(), receiverText + separator));
                        }
                    } else {
                        String receiver = implicitReceiver(callFile, path, inBody);
                        if (receiver == null) {
                            return;
                        }
                        long nameStart = compilation.start(callUnit, call.getMethodSelect());
                        edits.add(TextEdit.insert(callFile, (int) nameStart, typeName + "."));
                        edits.add(TextEdit.insert(callFile, open.end(), receiver + separator));
                    }
                }
            }.scan(new TreePath(unit), null);
        }
    }

    /** {@code this}, {@code Outer.this}, or (in the method's own body) the new parameter. */
    private String implicitReceiver(String callFile, TreePath callPath, boolean inBody) {
        boolean innermost = true;
        for (TreePath path = callPath; path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof ClassTree && compilation.trees.getElement(path) instanceof TypeElement type) {
                if (inherits(type, owner)) {
                    if (type.equals(owner) && inBody) {
                        return receiverName;
                    }
                    if (innermost) {
                        return "this";
                    }
                    String reference = support.typeReference(type, callFile);
                    if (reference == null) {
                        refuse(
                                RefusalCodes.OUTER_INSTANCE_ACCESS,
                                "The call's receiver is an anonymous class's instance, which can't be named.",
                                callFile,
                                0,
                                0,
                                null);
                        return null;
                    }
                    return reference + ".this";
                }
                innermost = false;
            }
        }
        return "this";
    }
}
