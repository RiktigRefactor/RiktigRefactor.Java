# Command line

The tool is not published yet.
Build it with `./mvnw package -DskipTests`, then run `java -jar target/riktigrefactor-java-0.1.0-SNAPSHOT.jar`; the examples below call that `riktig-java`.

The commands are `rename`, `extract-iife`, `make-method-static`, `move-static-member` and `move-to-instance-method`; each refactoring's page shows an example.

Positions are 1-based, and columns count Unicode scalar values.

- `--apply` writes the changes to disk.
- `--json` prints the result using the shared protocol, without the diff.
- `--commit-message` prints a suggested commit message in Arlo's Commit Notation.

The exit code is 0 on success, 1 when the refactoring is refused, and 2 on an error such as an unknown file.
The MCP server, batch mode, and language server are not implemented yet.

## Workspaces

The tools work on a workspace: for now, a directory whose `.java` files are compiled together against the JDK alone.
The specification's workspace for Java is a Maven or Gradle build, whose dependencies would be on the class path; reading those builds is not implemented yet.
A workspace that does not compile is refused, because the meaning of its names can't be known.

## Refusals and limitations

Each refusal has a code, listed in [refusal-codes.md](../refusal-codes.md).

See [limitations](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/limitations.md) for what is out of scope, such as reflection, serialization by name, and updating comments and strings.
Files that contain Unicode escapes (`\uXXXX`) are refused for now.

## Java version

The tool targets Java 21, the newest JDK available where it is developed, and analyzes code as the running JDK's compiler does.
The specification asks for the latest stable language version; moving to it is planned.
