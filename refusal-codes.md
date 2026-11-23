# Refusal codes

The Java tool defines its own refusal codes; the [specification](https://github.com/RiktigRefactor/riktigrefactor.spec) does not list any, except `internal-verification-failed`.
This file is the source of truth for them, and `src/main/java/io/github/riktigrefactor/java/RefusalCodes.java` defines them in code.
Codes are stable, and messages may change.
A new code is listed in the release notes.

| Code | Meaning |
|---|---|
| `input-has-errors` | The workspace does not compile, so the meaning of its names can't be known. |
| `unicode-escape` | A file contains a Unicode escape (`\uXXXX`), which the tool does not support yet. |
| `no-symbol` | There is no renameable symbol at the cursor. |
| `not-renameable` | The symbol can't be renamed: it is a package, it is not declared in the workspace, or it is declared implicitly (such as an enum's `values()`). |
| `invalid-identifier` | The new name is not a valid identifier, or is a keyword or a restricted identifier such as `var` or `record`. |
| `overrides-external` | The method overrides, or must be renamed together with a method that overrides, a method declared outside the workspace (such as `toString` or `Comparable.compareTo`). |
| `entry-point` | The method is found by name from outside Java: a `main` method or a `native` method, or the rename would create a `main` method. |
| `name-used-by-runtime` | The old or new name of a field or method is one the runtime looks up by name, such as `serialVersionUID`, `readObject`, or `finalize`. |
| `record-component` | The symbol is a record component, or its field, accessor, or canonical constructor parameter; renaming these is not supported yet. |
| `annotation-member` | The symbol is an annotation interface element, whose name reflection and the `value` shorthand depend on. |
| `would-rename-file` | The symbol is a public top-level type, so its file would have to be renamed, which is not supported yet. |
| `static-import-shared` | A single-static-import declaration names the symbol and other static members of the same name that are not renamed. |
| `conflicting-declaration` | The new name collides with an existing declaration in the same scope. |
| `would-introduce-errors` | The result would not compile, for a reason other than a conflicting declaration. |
| `binding-would-change` | After the change, a name would refer to a different declaration than before, such as when a reference is captured by a local, a lambda parameter, an inherited member, or a different overload. |
| `overriding-would-change` | After the rename, a method would override (or stop overriding) a method it did not before, which would change which method runs. |
| `internal-verification-failed` | The tool's own check of its result failed. This is a bug in the tool; please report it. |
