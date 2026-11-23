package io.github.riktigrefactor.java.extractiife;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.RefusalCodes;
import io.github.riktigrefactor.java.analysis.Bindings;
import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.analysis.Token;
import io.github.riktigrefactor.java.analysis.Verification;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

/**
 * Writes the immediately-invoked lambda in the specification's Java form: every outer local the selection uses, and
 * the receiver, is passed in as a parameter. The lambda's parameters get fresh names, because a Java lambda parameter
 * can't have the name of a local in scope. The functional interface is a {@code java.util.function} type when one
 * matches the exact parameter and return types; otherwise a local interface is declared before the enclosing
 * statement. Written locals are returned, in a local record when there are several.
 */
final class IifeWriter {
    /** Parameter and return kinds ("R" reference, "I", "J", "D", "Z", "V" void) to interface and method. */
    private static final Map<String, String> STANDARD_INTERFACES = standardInterfaces();

    private final Compilation compilation;
    private final String file;
    private final Selection selection;
    private final Captures captures;
    private final Set<String> usedNames = new HashSet<>();
    private final Map<VariableElement, String> parameterNames = new LinkedHashMap<>();
    private String selfName;
    private String thisKey;

    private final boolean naive;

    IifeWriter(Compilation compilation, String file, Selection selection, Captures captures, boolean naive) {
        this.naive = naive;
        this.compilation = compilation;
        this.file = file;
        this.selection = selection;
        this.captures = captures;
        for (Token token : compilation.tokensByFile.get(file)) {
            if (token.kind() == Token.Kind.IDENTIFIER) {
                usedNames.add(token.text());
            }
        }
    }

    private String fresh(String base) {
        String name = base;
        for (int i = 2; usedNames.contains(name); i++) {
            name = base + i;
        }
        usedNames.add(name);
        return name;
    }

    private Outcome refuse(String code, String message) {
        return Outcome.refuse(code, message, file, selection.start(), selection.end());
    }

    Outcome write() {
        List<String> parameterTypes = new ArrayList<>();
        List<String> parameterNamesInOrder = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        List<TypeMirror> signatureTypes = new ArrayList<>();
        if (captures.usesThis()) {
            if (captures.enclosingClass == null) {
                return refuse(RefusalCodes.TYPE_NOT_NAMEABLE, "The receiver's class can't be named.");
            }
            TypeMirror selfType = captures.enclosingClass.asType();
            if (!naive && !ExtractIife.isNameable(selfType)) {
                return refuse(
                        RefusalCodes.TYPE_NOT_NAMEABLE, "The receiver's class (" + selfType + ") can't be named.");
            }
            selfName = fresh("self");
            parameterTypes.add(selfType.toString());
            parameterNamesInOrder.add(selfName);
            arguments.add("this");
            signatureTypes.add(selfType);
            if (captures.thisElement != null) {
                thisKey = Bindings.of(compilation, Map.of()).elementKey(captures.thisElement);
            }
        }
        for (VariableElement local : captures.outerLocals.keySet()) {
            TypeMirror type = local.asType();
            if (!naive && !ExtractIife.isNameable(type)) {
                return refuse(
                        RefusalCodes.TYPE_NOT_NAMEABLE,
                        "The type of '" + local.getSimpleName() + "' (" + type
                                + ") can't be written as a parameter type.");
            }
            String name = fresh(local.getSimpleName() + "Arg");
            parameterNames.put(local, name);
            parameterTypes.add(type.toString());
            parameterNamesInOrder.add(name);
            arguments.add(local.getSimpleName().toString());
            signatureTypes.add(type);
        }

        List<VariableElement> written = new ArrayList<>(captures.written);
        TypeMirror returnType;
        String returnTypeText;
        String recordName = null;
        if (selection.isExpression()) {
            returnType = compilation.trees.getTypeMirror(selection.expression());
            returnTypeText = returnType.toString();
        } else if (written.isEmpty()) {
            returnType = compilation.types.getNoType(TypeKind.VOID);
            returnTypeText = "void";
        } else if (written.size() == 1) {
            returnType = written.getFirst().asType();
            returnTypeText = returnType.toString();
        } else {
            returnType = null;
            recordName = fresh("ExtractedResult");
            returnTypeText = null;
        }
        if (returnType != null && returnType.getKind() != TypeKind.VOID) {
            signatureTypes.add(returnType);
        }
        for (VariableElement variable : written) {
            signatureTypes.add(variable.asType());
        }

        List<TypeMirror> exceptions = captures.thrownCheckedExceptions();
        if (exceptions == null) {
            return refuse(
                    RefusalCodes.CHECKED_EXCEPTION,
                    "The checked exceptions the selection can throw can't be determined (a generic throws clause, or a resource without a close method).");
        }
        signatureTypes.addAll(exceptions);

        String typeParameters = typeParameters(signatureTypes);
        String typeArguments = typeParameters.isEmpty()
                ? ""
                : "<"
                        + typeVariables(signatureTypes).stream()
                                .map(TypeMirror::toString)
                                .collect(Collectors.joining(", "))
                        + ">";
        String recordType = recordName == null ? null : recordName + typeArguments;
        if (recordType != null) {
            returnTypeText = recordType;
        }

        String[] standard = recordName == null && exceptions.isEmpty()
                ? standardInterface(signatureTypesOfParameters(), returnType)
                : null;
        List<String> declarations = new ArrayList<>();
        String interfaceType;
        String method;
        if (standard != null) {
            interfaceType = standard[0];
            method = standard[1];
        } else {
            String interfaceName = fresh("Extracted");
            method = "apply";
            StringBuilder declaration = new StringBuilder(
                    "interface " + interfaceName + typeParameters + " { " + returnTypeText + " apply(");
            for (int i = 0; i < parameterTypes.size(); i++) {
                declaration
                        .append(i == 0 ? "" : ", ")
                        .append(parameterTypes.get(i))
                        .append(' ')
                        .append(parameterNamesInOrder.get(i));
            }
            declaration.append(")");
            if (!exceptions.isEmpty()) {
                declaration
                        .append(" throws ")
                        .append(exceptions.stream().map(TypeMirror::toString).collect(Collectors.joining(", ")));
            }
            declaration.append("; }");
            declarations.add(declaration.toString());
            interfaceType = interfaceName + typeArguments;
        }
        if (recordName != null) {
            declarations.add(
                    0,
                    "record " + recordName + typeParameters + "("
                            + written.stream()
                                    .map(v -> v.asType() + " " + v.getSimpleName())
                                    .collect(Collectors.joining(", "))
                            + ") {}");
        }

        List<TextEdit> edits = new ArrayList<>();
        String indent = "";
        if (!declarations.isEmpty()) {
            Integer statementStart = enclosingStatementStart();
            if (statementStart == null) {
                return refuse(
                        RefusalCodes.NO_STATEMENT_CONTEXT,
                        "The extracted lambda needs a local interface or record, and no statement here can hold its declaration (such as in a field initializer).");
            }
            indent = indentationAt(statementStart);
            StringBuilder text = new StringBuilder();
            for (String declaration : declarations) {
                text.append(declaration).append('\n').append(indent);
            }
            edits.add(TextEdit.insert(file, statementStart, text.toString()));
        } else if (!selection.isExpression()) {
            indent = indentationAt(selection.start());
        }

        String lambda = "((" + interfaceType + ") (" + String.join(", ", parameterNamesInOrder) + ") -> ";
        String call = "." + method + "(" + String.join(", ", arguments) + ")";
        String head;
        String tail;
        if (selection.isExpression()) {
            head = lambda;
            tail = ")" + call;
        } else if (written.isEmpty()) {
            head = lambda + "{ ";
            tail = " })" + call + ";";
        } else if (written.size() == 1) {
            VariableElement only = written.getFirst();
            head = only.getSimpleName() + " = " + lambda + "{ ";
            tail = " return " + parameterNames.get(only) + "; })" + call + ";";
        } else {
            String resultName = fresh("extractedResult");
            head = "var " + resultName + " = " + lambda + "{ ";
            StringBuilder end = new StringBuilder(" return new " + recordType + "("
                    + written.stream().map(parameterNames::get).collect(Collectors.joining(", ")) + "); })" + call
                    + ";");
            for (VariableElement variable : written) {
                end.append('\n')
                        .append(indent)
                        .append(variable.getSimpleName())
                        .append(" = ")
                        .append(resultName)
                        .append('.')
                        .append(variable.getSimpleName())
                        .append("();");
            }
            tail = end.toString();
        }
        edits.add(TextEdit.insert(file, selection.start(), head));
        for (var entry : captures.outerLocals.entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            for (int start : entry.getValue()) {
                edits.add(new TextEdit(file, start, start + name.length(), parameterNames.get(entry.getKey())));
            }
        }
        for (int start : captures.explicitThis) {
            edits.add(new TextEdit(file, start, start + "this".length(), selfName));
        }
        for (int start : captures.implicitMembers) {
            edits.add(TextEdit.insert(file, start, selfName + "."));
        }
        edits.add(TextEdit.insert(file, selection.end(), tail));
        return new Outcome.Success(edits, List.of());
    }

    private List<TypeMirror> parameterTypeMirrors = null;

    private List<TypeMirror> signatureTypesOfParameters() {
        if (parameterTypeMirrors == null) {
            parameterTypeMirrors = new ArrayList<>();
            if (captures.usesThis()) {
                parameterTypeMirrors.add(captures.enclosingClass.asType());
            }
            for (VariableElement local : captures.outerLocals.keySet()) {
                parameterTypeMirrors.add(local.asType());
            }
        }
        return parameterTypeMirrors;
    }

    /** What a tree in the selection is expected to bind to: outer locals and `this` become the lambda's parameters. */
    String expectedAfter(Bindings.Binding old) {
        boolean inSelection =
                old.file().equals(file) && selection.start() <= old.start() && old.end() <= selection.end();
        if (!inSelection) {
            return old.binding();
        }
        String binding = old.binding();
        Bindings keys = Bindings.of(compilation, Map.of());
        for (var entry : parameterNames.entrySet()) {
            binding = Verification.replaceElement(
                    binding, keys.elementKey(entry.getKey()), "inserted PARAMETER " + entry.getValue());
        }
        if (captures.explicitThis.contains((int) old.start()) && old.end() == old.start() + "this".length()) {
            String self = "inserted PARAMETER " + selfName;
            binding = thisKey != null && !binding.startsWith(" : ")
                    ? Verification.replaceElement(binding, thisKey, self)
                    : self + binding;
        }
        return binding;
    }

    private Integer enclosingStatementStart() {
        TreePath path = selection.roots().getFirst();
        var unit = compilation.unitsByFile.get(file);
        while (path.getParentPath() != null) {
            Tree parent = path.getParentPath().getLeaf();
            boolean isInStatementList = parent instanceof BlockTree
                    || (parent instanceof CaseTree caseTree
                            && caseTree.getStatements() != null
                            && caseTree.getStatements().contains(path.getLeaf()));
            if (isInStatementList) {
                return (int) compilation.start(unit, path.getLeaf());
            }
            if (parent instanceof LambdaExpressionTree || parent instanceof ClassTree || parent instanceof MethodTree) {
                return null;
            }
            path = path.getParentPath();
        }
        return null;
    }

    private String indentationAt(int offset) {
        String text = compilation.sources.get(file);
        int lineStart = offset;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        String before = text.substring(lineStart, offset);
        return before.isBlank() ? before : " ";
    }

    private String typeParameters(List<TypeMirror> types) {
        List<TypeVariable> variables = typeVariables(types);
        if (variables.isEmpty()) {
            return "";
        }
        return variables.stream()
                .map(v -> {
                    TypeMirror bound = v.getUpperBound();
                    String bounds = bound instanceof IntersectionType intersection
                            ? intersection.getBounds().stream()
                                    .map(TypeMirror::toString)
                                    .collect(Collectors.joining(" & "))
                            : bound.toString();
                    return bounds.equals("java.lang.Object") ? v.toString() : v + " extends " + bounds;
                })
                .collect(Collectors.joining(", ", "<", ">"));
    }

    private List<TypeVariable> typeVariables(List<TypeMirror> types) {
        Set<String> seen = new LinkedHashSet<>();
        List<TypeVariable> result = new ArrayList<>();
        for (TypeMirror type : types) {
            collectTypeVariables(type, seen, result);
        }
        return result;
    }

    private void collectTypeVariables(TypeMirror type, Set<String> seen, List<TypeVariable> into) {
        switch (type) {
            case null -> {}
            case TypeVariable variable -> {
                if (seen.add(variable.toString())) {
                    into.add(variable);
                    collectTypeVariables(variable.getUpperBound(), seen, into);
                }
            }
            case IntersectionType intersection ->
                intersection.getBounds().forEach(b -> collectTypeVariables(b, seen, into));
            case ArrayType array -> collectTypeVariables(array.getComponentType(), seen, into);
            case DeclaredType declared -> {
                declared.getTypeArguments().forEach(a -> collectTypeVariables(a, seen, into));
                if (declared.getEnclosingType().getKind() != TypeKind.NONE) {
                    collectTypeVariables(declared.getEnclosingType(), seen, into);
                }
            }
            case WildcardType wildcard -> {
                collectTypeVariables(wildcard.getExtendsBound(), seen, into);
                collectTypeVariables(wildcard.getSuperBound(), seen, into);
            }
            default -> {}
        }
    }

    private static String kindCode(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED, ARRAY, TYPEVAR -> "R";
            case INT -> "I";
            case LONG -> "J";
            case DOUBLE -> "D";
            case BOOLEAN -> "Z";
            case VOID -> "V";
            default -> "X";
        };
    }

    /** A java.util.function interface (with type arguments) and its method, or null if none matches exactly. */
    private static String[] standardInterface(List<TypeMirror> parameters, TypeMirror returnType) {
        StringBuilder signature = new StringBuilder();
        for (TypeMirror parameter : parameters) {
            signature.append(kindCode(parameter));
        }
        signature.append('>').append(kindCode(returnType));
        String entry = STANDARD_INTERFACES.get(signature.toString());
        if (entry == null) {
            return null;
        }
        String[] parts = entry.split("\\|");
        String type = parts[0];
        for (int i = 0; i < parameters.size(); i++) {
            type = type.replace("%" + (i + 1), parameters.get(i).toString());
        }
        type = type.replace("%R", returnType.toString());
        String packageName = type.equals("Runnable") ? "java.lang." : "java.util.function.";
        return new String[] {packageName + type, parts[1]};
    }

    private static Map<String, String> standardInterfaces() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(">V", "Runnable|run");
        map.put(">R", "Supplier<%R>|get");
        map.put(">I", "IntSupplier|getAsInt");
        map.put(">J", "LongSupplier|getAsLong");
        map.put(">D", "DoubleSupplier|getAsDouble");
        map.put(">Z", "BooleanSupplier|getAsBoolean");
        map.put("R>V", "Consumer<%1>|accept");
        map.put("I>V", "IntConsumer|accept");
        map.put("J>V", "LongConsumer|accept");
        map.put("D>V", "DoubleConsumer|accept");
        map.put("R>R", "Function<%1, %R>|apply");
        map.put("I>R", "IntFunction<%R>|apply");
        map.put("J>R", "LongFunction<%R>|apply");
        map.put("D>R", "DoubleFunction<%R>|apply");
        map.put("R>I", "ToIntFunction<%1>|applyAsInt");
        map.put("I>I", "IntUnaryOperator|applyAsInt");
        map.put("J>I", "LongToIntFunction|applyAsInt");
        map.put("D>I", "DoubleToIntFunction|applyAsInt");
        map.put("R>J", "ToLongFunction<%1>|applyAsLong");
        map.put("I>J", "IntToLongFunction|applyAsLong");
        map.put("J>J", "LongUnaryOperator|applyAsLong");
        map.put("D>J", "DoubleToLongFunction|applyAsLong");
        map.put("R>D", "ToDoubleFunction<%1>|applyAsDouble");
        map.put("I>D", "IntToDoubleFunction|applyAsDouble");
        map.put("J>D", "LongToDoubleFunction|applyAsDouble");
        map.put("D>D", "DoubleUnaryOperator|applyAsDouble");
        map.put("R>Z", "Predicate<%1>|test");
        map.put("I>Z", "IntPredicate|test");
        map.put("J>Z", "LongPredicate|test");
        map.put("D>Z", "DoublePredicate|test");
        map.put("RR>V", "BiConsumer<%1, %2>|accept");
        map.put("RI>V", "ObjIntConsumer<%1>|accept");
        map.put("RJ>V", "ObjLongConsumer<%1>|accept");
        map.put("RD>V", "ObjDoubleConsumer<%1>|accept");
        map.put("RR>R", "BiFunction<%1, %2, %R>|apply");
        map.put("RR>I", "ToIntBiFunction<%1, %2>|applyAsInt");
        map.put("RR>J", "ToLongBiFunction<%1, %2>|applyAsLong");
        map.put("RR>D", "ToDoubleBiFunction<%1, %2>|applyAsDouble");
        map.put("RR>Z", "BiPredicate<%1, %2>|test");
        map.put("II>I", "IntBinaryOperator|applyAsInt");
        map.put("JJ>J", "LongBinaryOperator|applyAsLong");
        map.put("DD>D", "DoubleBinaryOperator|applyAsDouble");
        return map;
    }
}
