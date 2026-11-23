package io.github.riktigrefactor.java;

/** The stable refusal codes; refusal-codes.md documents each one. */
public final class RefusalCodes {
    public static final String INPUT_HAS_ERRORS = "input-has-errors";
    public static final String UNICODE_ESCAPE = "unicode-escape";
    public static final String NO_SYMBOL = "no-symbol";
    public static final String NOT_RENAMEABLE = "not-renameable";
    public static final String INVALID_IDENTIFIER = "invalid-identifier";
    public static final String OVERRIDES_EXTERNAL = "overrides-external";
    public static final String ENTRY_POINT = "entry-point";
    public static final String NAME_USED_BY_RUNTIME = "name-used-by-runtime";
    public static final String RECORD_COMPONENT = "record-component";
    public static final String ANNOTATION_MEMBER = "annotation-member";
    public static final String WOULD_RENAME_FILE = "would-rename-file";
    public static final String STATIC_IMPORT_SHARED = "static-import-shared";
    public static final String CONFLICTING_DECLARATION = "conflicting-declaration";
    public static final String WOULD_INTRODUCE_ERRORS = "would-introduce-errors";
    public static final String BINDING_WOULD_CHANGE = "binding-would-change";
    public static final String OVERRIDING_WOULD_CHANGE = "overriding-would-change";
    public static final String INVALID_SELECTION = "invalid-selection";
    public static final String NON_LOCAL_CONTROL_FLOW = "non-local-control-flow";
    public static final String TYPE_NOT_NAMEABLE = "type-not-nameable";
    public static final String TARGET_TYPED_EXPRESSION = "target-typed-expression";
    public static final String CONSTANT_EXPRESSION = "constant-expression";
    public static final String SELECTION_IS_VARIABLE = "selection-is-variable";
    public static final String WRITES_OUTER_VARIABLE = "writes-outer-variable";
    public static final String NOT_EFFECTIVELY_FINAL = "not-effectively-final";
    public static final String DECLARATION_ESCAPES = "declaration-escapes";
    public static final String CHECKED_EXCEPTION = "checked-exception";
    public static final String WRITES_LOST_ON_EXCEPTION = "writes-lost-on-exception";
    public static final String NO_STATEMENT_CONTEXT = "no-statement-context";
    public static final String SUPER_ACCESS = "super-access";
    public static final String QUALIFIED_THIS = "qualified-this";
    public static final String OUTER_INSTANCE_ACCESS = "outer-instance-access";
    public static final String ALREADY_STATIC = "already-static";
    public static final String DYNAMIC_DISPATCH = "dynamic-dispatch";
    public static final String SYNCHRONIZED_OR_NATIVE = "synchronized-or-native";
    public static final String INNER_CLASS_CREATION = "inner-class-creation";
    public static final String BOUND_METHOD_REFERENCE = "bound-method-reference";
    public static final String RECEIVER_MAY_BE_NULL = "receiver-may-be-null";
    public static final String INTERNAL_VERIFICATION_FAILED = "internal-verification-failed";

    private RefusalCodes() {}
}
