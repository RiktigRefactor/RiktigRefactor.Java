# Contributing

## Building and testing

Run `./build-and-test` on Linux or macOS, or `build-and-test.cmd` on Windows (it needs Git for Windows).
It needs [Mise](https://mise.jdx.dev/), which installs JDK 25; the Maven wrapper downloads Maven itself.
It compiles and runs every test; it does not check formatting.
Run `./tidy-code` to format the code (palantir-java-format, through Spotless).

Test cases live in `src/test/cases`, in the [format defined by the specification](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/test-format.md), with a `case.json` per case.
Every input and expected program is compiled and run, and edge cases must fail under a deliberately naive implementation.

## Citing the Java specification

Every test case lists the language-specification sections it covers in its `spec` field, written as `Java §<number> <title>`, for example `Java §6.4.1 Shadowing`.
The numbers and titles follow [The Java Language Specification, Java SE 25 Edition](https://docs.oracle.com/javase/specs/jls/se25/html/index.html).

## Commit messages

Commits use [Arlo's Commit Notation](https://github.com/RefactoringCombos/ArlosCommitNotation).

Uppercase intentions mean a user-facing change; lowercase means the change is not user-facing.
For example, `^ F` adds a feature users can see, and `. r` is a refactoring.

### Extension intentions

This project uses these [extension intentions](https://github.com/RefactoringCombos/ArlosCommitNotation/blob/main/Extension%20Intentions.md):

| Prefix | Name | Intention |
|---|---|---|
| `t` | Test-only | Change automated tests without changing product code, such as adding an edge-case test. |
| `a` | Auto | Automatic formatting, code generation, or similar tasks. |
| `e` | Environment | Development setup and tooling changes that don't affect program behavior, such as CI, linting, and dependency-update configuration. |

Dependency updates use `^ r` for packages and SDKs, and `. e` for CI actions.
