# Move static method to instance method

The inverse of Make method static: a static method becomes an instance method of the class of the parameter named by `--receiver`, which becomes `this`, and every call `Pricing.total(order, 3)` becomes `order.total(3)`.
The method moves to the end of that class; names that resolved through the old class's scope are qualified, and it is `final` when the class has subclasses.

It refuses when a receiver argument may be null (the instance call would throw before the body), when an argument written before the receiver may depend on it, when the class, a supertype or a subtype already has a method with that name (or the name is one of `Object`'s), when the receiver is varargs, assigned, or used in a nested class, for method references whose receiver isn't the first parameter, and for static imports of the method.
Generic methods and generic target classes are not supported yet.

## Command

```shell
riktig-java move-to-instance-method --workspace . --file src/main/java/shop/Pricing.java --at 12:17 --receiver order
```

See [the command line](command-line.md) for the options every command accepts.
