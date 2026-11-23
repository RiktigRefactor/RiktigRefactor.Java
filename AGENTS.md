This repo implements the Java side of RiktigRefactor.
The specification is the source of truth: https://github.com/RiktigRefactor/riktigrefactor.spec
Read its `README.md`, `principles.md`, and the page for the refactoring you are changing before you change behavior.
Soundness comes before completeness: refusing is always acceptable, and wrong output never is.

Don't bother touching line endings and BOMs.

When asked to "build" or "test", run `./build-and-test`.
Also run it automatically before each commit.

## Test cases

Cases are directories under `src/test/cases/<refactoring>/<group>/<case>/`, in the [format defined by the specification](https://github.com/RiktigRefactor/riktigrefactor.spec/blob/main/test-format.md): a `case.json`, an `input/` directory, and an `expected/` directory unless a refusal is expected.
`CaseTests` finds every case directory and runs it; there is no per-case test code.
Every input and expected file set is a complete program whose `main` method throws if its behavior is wrong.
Rename cases mark the cursor with `/*|*/`; Extract IIFE cases mark the selection with `/*[*/` and `/*]*/`.
To create the `expected/` directory for a new case, run the tests once with `RIKTIG_WRITE_EXPECTED=1`, then read the output and confirm it is correct and behavior-preserving before keeping it.
Edge cases set `"naiveShouldFail": true` so the tests prove the assertion can detect the problem.

## Secrets

Always obscure API keys and other secrets, especially in saved screenshots.

## Java

The most important thing about a name is that it not be misleading.
A reader should never need to look at how a variable or method is used/implemented to understand what it represents.
It is OK to make the name long to achieve this.
If you see a comment explaining what a function or variable does/is, rename it to make it more obvious.

Analysis uses only the JDK's public compiler API (`javax.tools`, `com.sun.source`), so no `--add-exports` is needed.

## Committing

Before committing, run `./build-and-test`.
If the change has more than one thing going on, split it into multiple commits, each with a clear purpose.
Especially separate refactoring from behavior changes.
See [CONTRIBUTING.md](CONTRIBUTING.md) for the commit notation convention.

## Cloud sessions

- `gh pr create` is blocked in cloud sessions (it uses GraphQL).
  Open pull requests with `gh api repos/OWNER/REPO/pulls -f title=... -f head=... -f base=main -f body=...`.
- Cloud sessions reach the network only through an allowlisting proxy.
  Hosts needed: Maven Central (`repo.maven.apache.org`).
  The JVM picks up the proxy and its CA from `JAVA_TOOL_OPTIONS`, which the session sets.

## After every task

At the end of every task report on all of these, briefly:
- Skills used: name them.
- Skill gaps: if a skill you wished existed would have helped, propose one, even briefly.
- Prompt feedback: one concrete way the request could have been made more effectively this time — skip it only if there's genuinely nothing worth saying.
- Questions, ideas, concerns: surface them as they come up, not just at the end.
- Changes to your skills and instructions that would reduce token usage or improve efficiency.

Treat this end-of-task report as mandatory, like a checklist item — don't let it get crowded out by concise-output-style guidance; it's a small fixed addition, not a violation of terseness.

Never use Claude Code's persistent memory system for this project.
Durable guidance (conventions, process corrections, preferences) belongs in-repo instead — this file or a skill — so it's visible in version control and to every contributor.

## Decisions

When you need input on a decision:
- First, pick the option you think is best and propose it. Start that message with `⭐ 1: `
- Also list the alternatives you rejected, each with a brief reason why. Start each with `❌ n:`

Then have a discussion.
This formatting applies every time you present a decision, even a quick one.

## To-do list

The claude.ai project "RiktigRefactor" holds the only to-do list, in its `claude/todo.md` doc.
It is the source of truth for open decisions and remaining work, across all repos.
Update it as items are decided or done.
Don't keep a copy in a repo, so there is only one list to keep current.
