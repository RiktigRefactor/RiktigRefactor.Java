# Rename

Rename renames a symbol and every reference to it across the workspace: locals, parameters, fields, methods, classes, interfaces, enums and their constants, and type parameters.
Renaming a method renames every method it must stay in step with: those it overrides, those that override it, and those that implement the same interface method through a common subclass.
Renaming a class renames its constructors.
A single-static-import declaration is renamed when every static member it imports is renamed.

After editing, the tool compiles the result and checks that every name binds to the same declaration as before, and that no method starts or stops overriding another.
It refuses when, for example:

- the new name is not a valid identifier, or is a keyword or restricted identifier such as `var`;
- the method overrides a method outside the workspace, such as `toString`;
- a reference would be captured by another declaration (a local, a lambda parameter, a field of an inner or anonymous class, an inherited member) or a call would pick a different overload;
- the result would not compile, for example because of a conflicting declaration;
- the symbol is a `main` or `native` method, a record component, an annotation element, a public top-level type (its file would need renaming), or a name the runtime looks up, such as `serialVersionUID`.

Rename warns when a string literal equals the old name.
