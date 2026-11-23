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
    public static final String INTERNAL_VERIFICATION_FAILED = "internal-verification-failed";

    private RefusalCodes() {}
}
