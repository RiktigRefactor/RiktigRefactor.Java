# RiktigRefactor.Java

[![Build and test](https://github.com/RiktigRefactor/RiktigRefactor.Java/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/RiktigRefactor/RiktigRefactor.Java/actions/workflows/build-and-test.yml)

Behavior-preserving refactorings for Java, built on the JDK's compiler API (`javax.tools` and `com.sun.source`).

*Riktig* (Swedish/Norwegian) means correct.
When the tool cannot prove a transformation is safe, it refuses with a clear reason instead of guessing.

This is the Java implementation of the [RiktigRefactor specification](https://github.com/RiktigRefactor/riktigrefactor.spec).
The specification defines the correctness model, the refactorings, the JSON protocol, and the test format.

## What it can do

The tool has these refactorings: Rename, Extract IIFE, Make method static, Move static member, and Move static method to instance method.
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

### Make method static

Make method static turns an instance method into a static method of the same class that takes the receiver as its first parameter (named after the class, `order` for `Order`, unless `--receiver-name` says otherwise), and rewrites every call `recv.total(3)` to `Order.total(recv, 3)`:

```java
// Before
int total(int qty) { return price * qty; }
int t = order.total(3);

// After
static int total(Order order, int qty) { return order.price * qty; }
int t = Order.total(order, 3);
```

Uses of the receiver in the body, explicit (`this`) and implicit (fields and calls), including inside lambdas, become uses of the parameter.
A generic class's type parameters become the method's own.
Unbound method references (`Order::total`) already take the receiver first and stay as written.
Only the specification's `receiver` mode is implemented; `members` mode is not yet.

It refuses when calls are dispatched dynamically (the method is abstract, an interface method, overrides or is overridden), when a call's receiver may be null (a null receiver would throw before the body, but the static method would run it), for bound method references such as `order::total`, `super` in the body or at a call, `synchronized` or `native` methods, creating an inner class instance that needs the receiver, and when a new overload would change which method another call picks.

### Move static member

Move static member moves a static method or field to another class or record in the workspace, named by its qualified name (`--target shop.Pricing`, or `Program.Pricing` for a nested class), and rewrites every reference to name the target.
Names in the moved declaration that resolved through the source's scope are qualified with the source (`Pricing.RATE`), nested types are qualified, and the target's file gains imports the declaration needs.
Single-static-import declarations are updated; other unqualified uses are qualified with the target.

It refuses when using the member would initialize classes at a different time (a field initializer, or the source's or target's static initialization, that may have observable effects), when a reference goes through an expression with effects (`make().rate()`), when something would become inaccessible, for `main`, `synchronized` and `native` methods, fields declared together with others, and when the move would change which member any reference binds to.
Interfaces and enums as targets are not supported yet.

### Move static method to instance method

The inverse of Make method static: a static method becomes an instance method of the class of the parameter named by `--receiver`, which becomes `this`, and every call `Pricing.total(order, 3)` becomes `order.total(3)`.
The method moves to the end of that class; names that resolved through the old class's scope are qualified, and it is `final` when the class has subclasses.

It refuses when a receiver argument may be null (the instance call would throw before the body), when an argument written before the receiver may depend on it, when the class, a supertype or a subtype already has a method with that name (or the name is one of `Object`'s), when the receiver is varargs, assigned, or used in a nested class, for method references whose receiver isn't the first parameter, and for static imports of the method.
Generic methods and generic target classes are not supported yet.

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

Make a method static, with the cursor on its name or a call:

```shell
riktig-java make-method-static --workspace . --file src/main/java/shop/Order.java --at 12:17
```

Move a static member:

```shell
riktig-java move-static-member --workspace . --file src/main/java/shop/Order.java --at 12:17 --target shop.Pricing
```

Move a static method to an instance method of its `order` parameter's class:

```shell
riktig-java move-to-instance-method --workspace . --file src/main/java/shop/Pricing.java --at 12:17 --receiver order
```

The exit code is 0 on success, 1 when the refactoring is refused, and 2 on an error such as an unknown file.
The MCP server, batch mode, and language server are not implemented yet.

## Java version

The tool targets Java 21, the newest JDK available where it is developed, and analyzes code as the running JDK's compiler does.
The specification asks for the latest stable language version; moving to it is planned.

## Building and testing

```shell
./build-and-test
```

For more information, see [CONTRIBUTING.md](CONTRIBUTING.md).
