# Make method static

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
