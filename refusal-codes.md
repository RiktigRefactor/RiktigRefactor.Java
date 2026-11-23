# Refusal codes

The Java tool defines its own refusal codes; the [specification](https://github.com/RiktigRefactor/riktigrefactor.spec) does not list any, except `internal-verification-failed`.
This file is the source of truth for them, and `src/main/java/io/github/riktigrefactor/java/RefusalCodes.java` defines them in code.
Codes are stable, and messages may change.
A new code is listed in the release notes.

| Code | Meaning |
|---|---|
| `input-has-errors` | The workspace does not compile, so the meaning of its names can't be known. |
| `unicode-escape` | A file contains a Unicode escape (`\uXXXX`), which the tool does not support yet. |
| `no-symbol` | There is no renameable symbol (or, for the method refactorings, no method) at the cursor. |
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
| `invalid-selection` | Extract IIFE: the selection is not exactly one expression or whole statements of one statement list, or does not denote a value (such as a type name, or the method name of a call). |
| `non-local-control-flow` | Extract IIFE: `return`, or a `break`, `continue`, or `yield` whose target is outside the selection, would cross the boundary of the lambda. |
| `type-not-nameable` | Extract IIFE: the expression's type can't be written as a type argument, such as an anonymous class, intersection, or captured wildcard type. |
| `target-typed-expression` | Extract IIFE: the expression's meaning depends on its target type (a lambda, a method reference, `null`, or an array initializer). |
| `constant-expression` | Extract IIFE: the expression is (or may be) a compile-time constant; a method call is not, which can change the type of a conditional, narrowing in assignments, `switch` labels, and string interning. |
| `selection-is-variable` | Extract IIFE: the expression is assigned to or incremented, so it denotes a variable; the lambda would return only its value. |
| `writes-outer-variable` | Extract IIFE: the selected expression assigns a local variable or parameter declared outside it; only whole statements can return written variables. |
| `not-effectively-final` | Extract IIFE: a lambda or class inside the selection would capture a parameter that the selection writes. |
| `declaration-escapes` | Extract IIFE: a variable or class the selection declares is used after it, where it would no longer be in scope. |
| `checked-exception` | Extract IIFE: the checked exceptions the selection can throw can't be determined (a generic `throws` clause, or a resource without a `close` method), so the local interface's `throws` clause can't be written. |
| `writes-lost-on-exception` | Extract IIFE: inside a `try`, the selection assigns a local read afterwards; an exception would lose the assignment, because the lambda returns values only when it completes. |
| `no-statement-context` | Extract IIFE: the lambda needs a local interface or record, and no statement can hold its declaration (a field initializer, or a body that is a single expression). |
| `super-access` | Extract IIFE or Make method static: the code uses `super`, which a parameter can't stand for, or a call through `super` would have to become a static call. |
| `qualified-this` | Extract IIFE or Make method static: the code uses `Outer.this` or `Outer.super`. |
| `outer-instance-access` | Extract IIFE or Make method static: the code uses a member of an enclosing instance (not `this`), which can't be passed in as the receiver. |
| `already-static` | Make method static: the method is already static. |
| `dynamic-dispatch` | Make method static: the method is abstract, an interface method, or overrides or is overridden by another method, so calls to it are dispatched dynamically. |
| `synchronized-or-native` | Make method static: the method is `synchronized` (it locks its receiver; a static one would lock the class) or `native` (found by name). |
| `inner-class-creation` | Make method static: the body creates an inner class instance that needs the receiver as its enclosing instance. |
| `bound-method-reference` | Make method static: a method reference such as `order::total` binds its receiver when it is created; rewriting it as a lambda would move that evaluation. |
| `receiver-may-be-null` | Make method static: a call's receiver is not provably non-null (`this`, an object creation, a string literal, or a local initialized with one and never reassigned); a null receiver throws before the body, but the static method would run it. |
| `internal-verification-failed` | The tool's own check of its result failed. This is a bug in the tool; please report it. |
