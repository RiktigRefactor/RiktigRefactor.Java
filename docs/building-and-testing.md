# Building and testing

Install [Git](https://git-scm.com/) and [Mise](https://mise.jdx.dev/).
Mise provides JDK 21; the Maven wrapper downloads Maven itself, so nothing else needs to be installed:

```shell
./build-and-test
```

`build-and-test` builds, checks formatting (palantir-java-format, through Spotless) and runs every test case; `build-and-test.cmd` does the same on Windows through Git for Windows' Bash.
Run `./tidy-code` to format the code.

Test cases live in `src/test/cases`, in the [format defined by the specification](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/test-format.md), with a `case.json` per case.
Every input and expected program is compiled and run, and edge cases must fail under a deliberately naive implementation.

### Citing the Java specification

Every test case lists the language-specification sections it covers in its `spec` field, written as `Java §<number> <title>`, for example `Java §6.4.1 Shadowing`.
The numbers and titles follow [The Java Language Specification, Java SE 21 Edition](https://docs.oracle.com/javase/specs/jls/se21/html/index.html).
