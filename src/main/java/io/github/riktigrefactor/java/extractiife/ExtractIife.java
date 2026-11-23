package io.github.riktigrefactor.java.extractiife;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.RefusalCodes;
import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.Prepared;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.analysis.Verification;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

/**
 * Wraps an expression or whole statements in an immediately-invoked lambda. Java lambdas capture effectively final
 * locals by value, so the lambda captures them directly instead of taking parameters, and refuses when a captured local
 * is not effectively final or is written.
 */
public final class ExtractIife {
    private static final Set<ElementKind> LOCAL_KINDS = Set.of(
            ElementKind.LOCAL_VARIABLE,
            ElementKind.PARAMETER,
            ElementKind.EXCEPTION_PARAMETER,
            ElementKind.RESOURCE_VARIABLE,
            ElementKind.BINDING_VARIABLE);

    private final Compilation compilation;
    private final String file;
    private final Selection selection;

    private ExtractIife(Compilation compilation, String file, Selection selection) {
        this.compilation = compilation;
        this.file = file;
        this.selection = selection;
    }

    public static Outcome run(Map<String, String> sources, String file, int start, int end) {
        return run(sources, file, start, end, false);
    }

    /** The deliberately naive Extract IIFE: wraps the selection with no precondition checks and no verification. */
    public static Outcome runNaive(Map<String, String> sources, String file, int start, int end) {
        return run(sources, file, start, end, true);
    }

    private static Outcome run(Map<String, String> sources, String file, int start, int end, boolean naive) {
        Prepared prepared = Prepared.analyze(sources);
        if (prepared.refusal() != null) {
            return prepared.refusal();
        }
        Selection selection = Selection.find(prepared.compilation(), file, start, end);
        if (selection == null) {
            return Outcome.refuse(
                    RefusalCodes.INVALID_SELECTION,
                    "The selection is not exactly one expression, or whole statements of one block.",
                    file,
                    start,
                    end,
                    "Select a complete expression, or complete statements from the same block.");
        }
        return new ExtractIife(prepared.compilation(), file, selection).extract(naive);
    }

    private Outcome extract(boolean naive) {
        if (!naive) {
            Outcome refusal = selection.isExpression() ? checkExpression() : null;
            if (refusal == null) {
                refusal = checkControlFlow();
            }
            if (refusal == null) {
                refusal = checkDeclarationsEscape();
            }
            if (refusal != null) {
                return refusal;
            }
        }
        Captures captures = Captures.analyze(compilation, file, selection);
        if (!naive && captures.refusalCode != null) {
            return refuse(captures.refusalCode, captures.refusalMessage);
        }
        if (!naive && selection.isExpression() && !captures.written.isEmpty()) {
            return refuse(
                    RefusalCodes.WRITES_OUTER_VARIABLE,
                    "The selected expression assigns a local declared outside it; only whole statements can return a written variable.",
                    "Select the whole statement instead.");
        }
        if (!naive && !captures.written.isEmpty() && captures.isInsideTry()) {
            return refuse(
                    RefusalCodes.WRITES_LOST_ON_EXCEPTION,
                    "Inside a try statement, the selection assigns a local declared outside it; an exception would lose the assignment, because the lambda returns values only when it completes.");
        }
        IifeWriter writer = new IifeWriter(compilation, file, selection, captures, naive);
        Outcome generated = writer.write();
        if (generated instanceof Outcome.Refused refused) {
            return naive ? generated : refused;
        }
        List<TextEdit> edits = ((Outcome.Success) generated).edits();
        if (naive) {
            return generated;
        }
        Verification verification = Verification.verify(
                compilation,
                edits,
                true,
                error -> switch (String.valueOf(error.getCode())) {
                    case "compiler.err.unreported.exception.need.to.catch.or.throw" -> RefusalCodes.CHECKED_EXCEPTION;
                    case "compiler.err.cant.ref.non.effectively.final.var" -> RefusalCodes.NOT_EFFECTIVELY_FINAL;
                    default -> RefusalCodes.WOULD_INTRODUCE_ERRORS;
                },
                Verification.Mode.STRICT,
                writer::expectedAfter);
        if (verification.refusal != null) {
            return new Outcome.Refused(verification.refusal);
        }
        return generated;
    }

    private Outcome refuse(String code, String message) {
        return Outcome.refuse(code, message, file, selection.start(), selection.end());
    }

    private Outcome refuse(String code, String message, String suggestion) {
        return Outcome.refuse(code, message, file, selection.start(), selection.end(), suggestion);
    }

    private Outcome checkExpression() {
        TreePath path = selection.expression();
        Tree tree = path.getLeaf();
        Tree parent = path.getParentPath().getLeaf();
        Element element = compilation.trees.getElement(path);
        boolean isTypeOrPackage = element instanceof TypeElement || element instanceof PackageElement;
        boolean isMethodName =
                parent instanceof MethodInvocationTree invocation && invocation.getMethodSelect() == tree;
        boolean isSuper = tree instanceof IdentifierTree identifier
                && identifier.getName().contentEquals("super");
        if (isTypeOrPackage || isMethodName || isSuper) {
            return refuse(RefusalCodes.INVALID_SELECTION, "The selection is not an expression that denotes a value.");
        }
        boolean isWriteTarget = switch (parent) {
            case AssignmentTree assignment -> assignment.getVariable() == tree;
            case CompoundAssignmentTree assignment -> assignment.getVariable() == tree;
            case UnaryTree unary -> unary.getExpression() == tree && isIncrementOrDecrement(unary.getKind());
            default -> false;
        };
        if (isWriteTarget) {
            return refuse(
                    RefusalCodes.SELECTION_IS_VARIABLE,
                    "The expression is assigned to, so it denotes a variable; a lambda would return only its value.");
        }
        boolean isTargetTyped = tree.getKind() == Tree.Kind.LAMBDA_EXPRESSION
                || tree.getKind() == Tree.Kind.MEMBER_REFERENCE
                || tree.getKind() == Tree.Kind.NULL_LITERAL
                || (tree instanceof NewArrayTree array && array.getType() == null);
        if (isTargetTyped) {
            return refuse(
                    RefusalCodes.TARGET_TYPED_EXPRESSION,
                    "The expression's meaning depends on its target type (a lambda, method reference, null, or array initializer).");
        }
        if (isConstantExpression(path)) {
            return refuse(
                    RefusalCodes.CONSTANT_EXPRESSION,
                    "The expression is a compile-time constant; the call would not be, which can change types, conversions and switch labels.");
        }
        TypeMirror type = compilation.trees.getTypeMirror(path);
        if (type.getKind() == TypeKind.VOID && !(parent instanceof ExpressionStatementTree)) {
            return refuse(RefusalCodes.INVALID_SELECTION, "The expression has no value.");
        }
        if (type.getKind() != TypeKind.VOID && !isNameable(type)) {
            return refuse(
                    RefusalCodes.TYPE_NOT_NAMEABLE,
                    "The expression's type (" + type
                            + ") can't be written as a type argument, such as an anonymous class or intersection type.");
        }
        return null;
    }

    private static boolean isIncrementOrDecrement(Tree.Kind kind) {
        return kind == Tree.Kind.PREFIX_INCREMENT
                || kind == Tree.Kind.PREFIX_DECREMENT
                || kind == Tree.Kind.POSTFIX_INCREMENT
                || kind == Tree.Kind.POSTFIX_DECREMENT;
    }

    /** Conservatively: whether the expression may be a constant expression (Java §15.29). */
    private boolean isConstantExpression(TreePath path) {
        return switch (path.getLeaf()) {
            case LiteralTree literal -> literal.getKind() != Tree.Kind.NULL_LITERAL;
            case ParenthesizedTree parenthesized ->
                isConstantExpression(new TreePath(path, parenthesized.getExpression()));
            case TypeCastTree cast -> isConstantExpression(new TreePath(path, cast.getExpression()));
            case UnaryTree unary ->
                !isIncrementOrDecrement(unary.getKind())
                        && isConstantExpression(new TreePath(path, unary.getExpression()));
            case BinaryTree binary ->
                isConstantExpression(new TreePath(path, binary.getLeftOperand()))
                        && isConstantExpression(new TreePath(path, binary.getRightOperand()));
            case ConditionalExpressionTree conditional ->
                isConstantExpression(new TreePath(path, conditional.getCondition()))
                        && isConstantExpression(new TreePath(path, conditional.getTrueExpression()))
                        && isConstantExpression(new TreePath(path, conditional.getFalseExpression()));
            case IdentifierTree identifier -> isConstantVariable(path);
            case MemberSelectTree select -> isConstantVariable(path);
            default -> false;
        };
    }

    private boolean isConstantVariable(TreePath path) {
        return compilation.trees.getElement(path) instanceof VariableElement variable
                && variable.getConstantValue() != null;
    }

    static boolean isNameable(TypeMirror type) {
        return switch (type) {
            case PrimitiveType primitive -> true;
            case ArrayType array -> isNameable(array.getComponentType());
            case DeclaredType declared ->
                ((TypeElement) declared.asElement()).getNestingKind() != NestingKind.ANONYMOUS
                        && declared.getTypeArguments().stream().allMatch(ExtractIife::isNameable)
                        && (declared.getEnclosingType().getKind() == TypeKind.NONE
                                || isNameable(declared.getEnclosingType()));
            case TypeVariable variable -> !variable.toString().contains("capture#");
            case WildcardType wildcard ->
                (wildcard.getExtendsBound() == null || isNameable(wildcard.getExtendsBound()))
                        && (wildcard.getSuperBound() == null || isNameable(wildcard.getSuperBound()));
            default -> false;
        };
    }

    /** Refuses return, and break, continue or yield whose target is outside the selection. */
    private Outcome checkControlFlow() {
        for (TreePath root : selection.roots()) {
            Outcome[] refusal = new Outcome[1];
            new TreePathScanner<Void, Void>() {
                private final Deque<Tree> jumpTargets = new ArrayDeque<>();
                private final Set<String> labels = new HashSet<>();

                @Override
                public Void scan(TreePath path, Void unused) {
                    enter(path.getLeaf());
                    try {
                        return super.scan(path, unused);
                    } finally {
                        exit(path.getLeaf());
                    }
                }

                @Override
                public Void scan(Tree tree, Void unused) {
                    if (tree == null || refusal[0] != null) {
                        return null;
                    }
                    if (tree instanceof LambdaExpressionTree || tree instanceof ClassTree) {
                        return null;
                    }
                    enter(tree);
                    try {
                        return super.scan(tree, unused);
                    } finally {
                        exit(tree);
                    }
                }

                private static boolean isJumpTarget(Tree tree) {
                    return tree instanceof ForLoopTree
                            || tree instanceof EnhancedForLoopTree
                            || tree instanceof WhileLoopTree
                            || tree instanceof DoWhileLoopTree
                            || tree instanceof SwitchTree
                            || tree instanceof SwitchExpressionTree;
                }

                private void enter(Tree tree) {
                    if (isJumpTarget(tree)) {
                        jumpTargets.push(tree);
                    }
                    if (tree instanceof LabeledStatementTree labeled) {
                        labels.add(labeled.getLabel().toString());
                    }
                }

                private void exit(Tree tree) {
                    if (isJumpTarget(tree)) {
                        jumpTargets.pop();
                    }
                    if (tree instanceof LabeledStatementTree labeled) {
                        labels.remove(labeled.getLabel().toString());
                    }
                }

                @Override
                public Void visitReturn(ReturnTree tree, Void unused) {
                    refusal[0] = refuse(
                            RefusalCodes.NON_LOCAL_CONTROL_FLOW,
                            "The selection contains `return`, which would return from the lambda instead of the method.",
                            "Shrink the selection to exclude the return statement.");
                    return null;
                }

                @Override
                public Void visitBreak(BreakTree tree, Void unused) {
                    boolean hasTarget = tree.getLabel() == null
                            ? jumpTargets.stream().anyMatch(t -> !(t instanceof SwitchExpressionTree))
                            : labels.contains(tree.getLabel().toString());
                    if (!hasTarget) {
                        refusal[0] = refuse(
                                RefusalCodes.NON_LOCAL_CONTROL_FLOW,
                                "The selection contains `break` whose target is outside the selection.",
                                "Include the enclosing loop or switch in the selection.");
                    }
                    return null;
                }

                @Override
                public Void visitContinue(ContinueTree tree, Void unused) {
                    boolean hasTarget = tree.getLabel() == null
                            ? jumpTargets.stream()
                                    .anyMatch(t -> !(t instanceof SwitchTree) && !(t instanceof SwitchExpressionTree))
                            : labels.contains(tree.getLabel().toString());
                    if (!hasTarget) {
                        refusal[0] = refuse(
                                RefusalCodes.NON_LOCAL_CONTROL_FLOW,
                                "The selection contains `continue` whose loop is outside the selection.",
                                "Include the enclosing loop in the selection.");
                    }
                    return super.visitContinue(tree, unused);
                }

                @Override
                public Void visitYield(YieldTree tree, Void unused) {
                    if (jumpTargets.stream().noneMatch(t -> t instanceof SwitchExpressionTree)) {
                        refusal[0] = refuse(
                                RefusalCodes.NON_LOCAL_CONTROL_FLOW,
                                "The selection contains `yield` whose switch expression is outside the selection.",
                                "Include the enclosing switch expression in the selection.");
                        return null;
                    }
                    return super.visitYield(tree, unused);
                }
            }.scan(root, null);
            if (refusal[0] != null) {
                return refusal[0];
            }
        }
        return null;
    }

    /** A local or class the selection declares would no longer be in scope after it. */
    private Outcome checkDeclarationsEscape() {
        Set<Element> declaredInside = new HashSet<>();
        for (TreePath root : selection.roots()) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitVariable(VariableTree tree, Void unused) {
                    declaredInside.add(compilation.trees.getElement(getCurrentPath()));
                    return super.visitVariable(tree, unused);
                }

                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    declaredInside.add(compilation.trees.getElement(getCurrentPath()));
                    return super.visitClass(tree, unused);
                }
            }.scan(root, null);
        }
        if (declaredInside.isEmpty()) {
            return null;
        }
        var unit = compilation.unitsByFile.get(file);
        Outcome[] refusal = new Outcome[1];
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree tree, Void unused) {
                long position = compilation.start(unit, tree);
                if (refusal[0] == null
                        && !selection.contains(position)
                        && declaredInside.contains(compilation.trees.getElement(getCurrentPath()))) {
                    refusal[0] = refuse(
                            RefusalCodes.DECLARATION_ESCAPES,
                            "'" + tree.getName()
                                    + "' is declared in the selection and used after it, where it would no longer be in scope.",
                            "Extend the selection to include every use, or move the declaration out of the selection.");
                }
                return null;
            }
        }.scan(new TreePath(unit), null);
        return refusal[0];
    }
}
