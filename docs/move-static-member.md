# Move static member

Move static member moves a static method or field to another class or record in the workspace, named by its qualified name (`--target shop.Pricing`, or `Program.Pricing` for a nested class), and rewrites every reference to name the target.
Names in the moved declaration that resolved through the source's scope are qualified with the source (`Pricing.RATE`), nested types are qualified, and the target's file gains imports the declaration needs.
Single-static-import declarations are updated; other unqualified uses are qualified with the target.

It refuses when using the member would initialize classes at a different time (a field initializer, or the source's or target's static initialization, that may have observable effects), when a reference goes through an expression with effects (`make().rate()`), when something would become inaccessible, for `main`, `synchronized` and `native` methods, fields declared together with others, and when the move would change which member any reference binds to.
Interfaces and enums as targets are not supported yet.

## Command

```shell
riktig-java move-static-member --workspace . --file src/main/java/shop/Order.java --at 12:17 --target shop.Pricing
```

See [the command line](command-line.md) for the options every command accepts.
