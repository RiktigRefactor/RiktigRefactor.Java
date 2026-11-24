# Extract IIFE

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
