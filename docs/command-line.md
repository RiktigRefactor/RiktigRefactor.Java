# Command line

Each refusal has a code, listed in [refusal-codes.md](../refusal-codes.md).

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
riktig-java convert-to-instance-method --workspace . --file src/main/java/shop/Pricing.java --at 12:17 --receiver order
```

The exit code is 0 on success, 1 when the refactoring is refused, and 2 on an error such as an unknown file.
The MCP server, batch mode, and language server are not implemented yet.

## Java version

The tool targets Java 25, the newest JDK available where it is developed, and analyzes code as the running JDK's compiler does.
The specification asks for the latest stable language version; moving to it is planned.

A workspace is, for now, a directory whose `.java` files are compiled together against the JDK alone.
The specification's workspace for Java is a Maven or Gradle build, whose dependencies would be on the class path; reading those builds is not implemented yet.
A workspace that does not compile is refused, because the meaning of its names can't be known.

See [limitations](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/limitations.md) for what is out of scope, such as reflection, serialization by name, and updating comments and strings.
Files that contain Unicode escapes (`\uXXXX`) are refused for now.
