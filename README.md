# RiktigRefactor.Java

[![Build and test](https://github.com/RiktigRefactor/RiktigRefactor.Java/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/RiktigRefactor/RiktigRefactor.Java/actions/workflows/build-and-test.yml)

Behavior-preserving refactorings for Java, built on the JDK's compiler API (`javax.tools` and `com.sun.source`).

*Riktig* (Swedish/Norwegian) means correct.
When the tool cannot prove a transformation is safe, it refuses with a clear reason instead of guessing.

This is the Java implementation of the [RiktigRefactor specification](https://github.com/RiktigRefactor/riktigrefactor.spec).
The specification defines the correctness model, the refactorings, the JSON protocol, and the test format.

## What it can do

The tool has two refactorings: Rename and Extract IIFE.
Both work on a workspace: for now, a directory whose `.java` files are compiled together against the JDK alone.
The specification's workspace for Java is a Maven or Gradle build, whose dependencies would be on the class path; reading those builds is not implemented yet.
A workspace that does not compile is refused, because the meaning of its names can't be known.

### Rename

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

### Extract IIFE

Extract IIFE wraps a selected expression, or whole statements of one block, in an immediately-invoked lambda, in the specification's Java form:

```java
// Before
int total = price * qty + 1;

// After
int total = ((java.util.function.IntBinaryOperator) (priceArg, qtyArg) -> priceArg * qtyArg).applyAsInt(price, qty) + 1;
```

Every local the selection uses is passed in as a parameter, and so is `this` (as `self`) when it uses instance members.
A Java lambda parameter can't have the name of a local in scope, so parameters are named after the local with an `Arg` suffix.
The functional interface is a `java.util.function` type when one matches the exact parameter and return types without boxing; otherwise, and when the selection throws checked exceptions, a local interface is declared before the enclosing statement, with the method's type variables as its own and a `throws` clause.
A local the statements assign is returned and assigned back; several are returned in a local record.

It refuses, for example, when:

- the selection contains `return`, or `break`, `continue` or `yield` whose target is outside it;
- it uses `super`, `Outer.this`, or a member of an enclosing instance;
- it assigns an outer local inside a `try`, or the selected expression (not statements) assigns one;
- the expression is a constant expression, a lambda, a method reference, `null`, or an array initializer, or is assigned to;
- a type can't be written, such as an anonymous class type;
- it declares a variable used after it;
- it needs a local interface or record where no statement can hold one, such as a field initializer.

After wrapping, the tool compiles the result and checks that every name binds as before and every expression keeps its type.

Each refusal has a code, listed in [refusal-codes.md](refusal-codes.md).

See [limitations](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/limitations.md) for what is out of scope, such as reflection, serialization by name, and updating comments and strings.
Files that contain Unicode escapes (`\uXXXX`) are refused for now.

## Command line

The tool is not published yet.
Build it with `./mvnw package -DskipTests`, then run `java -jar target/riktigrefactor-java-0.1.0-SNAPSHOT.jar`; the examples below call that `riktig-java`.

Preview a rename as a diff:

```shell
riktig-java rename --workspace . --file src/main/java/shop/Order.java --at 12:17 --new-name total
```

Preview an Extract IIFE of the text from line 12, column 19 up to (not including) column 30:

```shell
riktig-java extract-iife --workspace . --file src/main/java/shop/Order.java --range 12:19-12:30
```

Positions are 1-based, and columns count Unicode scalar values.

- `--apply` writes the changes to disk.
- `--json` prints the result using the shared protocol, without the diff.
- `--commit-message` prints a suggested commit message in Arlo's Commit Notation.

The exit code is 0 on success, 1 when the refactoring is refused, and 2 on an error such as an unknown file.
The MCP server, batch mode, and language server are not implemented yet.

## Java version

The tool targets Java 21, the newest JDK available where it is developed, and analyzes code as the running JDK's compiler does.
The specification asks for the latest stable language version; moving to it is planned.

## Build and test

Run `./build-and-test` on Linux or macOS, or `build-and-test.cmd` on Windows (it needs Git for Windows).
It needs a JDK 21 or newer; the Maven wrapper downloads Maven itself.
It checks formatting (palantir-java-format, through Spotless) and runs every test.
Run `./tidy-code` to format the code.

Test cases live in `src/test/cases`, in the [format defined by the specification](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/test-format.md), with a `case.json` per case.
Every input and expected program is compiled and run, and edge cases must fail under a deliberately naive implementation.

### Citing the Java specification

Every test case lists the language-specification sections it covers in its `spec` field, written as `Java §<number> <title>`, for example `Java §6.4.1 Shadowing`.
The numbers and titles follow [The Java Language Specification, Java SE 21 Edition](https://docs.oracle.com/javase/specs/jls/se21/html/index.html).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the commit message convention.
