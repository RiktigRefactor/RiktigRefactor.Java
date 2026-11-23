package io.github.riktigrefactor.java.cli;

import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.Workspace;
import io.github.riktigrefactor.java.analysis.Lexer;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.analysis.Token;
import io.github.riktigrefactor.java.extractiife.ExtractIife;
import io.github.riktigrefactor.java.makestatic.MakeMethodStatic;
import io.github.riktigrefactor.java.movestatic.MoveStaticMember;
import io.github.riktigrefactor.java.movetoinstance.MoveToInstanceMethod;
import io.github.riktigrefactor.java.protocol.Json;
import io.github.riktigrefactor.java.protocol.ProtocolResult;
import io.github.riktigrefactor.java.protocol.ProtocolResult.CommitMessage;
import io.github.riktigrefactor.java.protocol.SourceText;
import io.github.riktigrefactor.java.protocol.UnifiedDiff;
import io.github.riktigrefactor.java.rename.Rename;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The riktig-java command line: exit code 0 on success, 1 when the refactoring is refused, 2 on a usage or input error. */
public final class Main {
    static final int SUCCESS = 0;
    static final int REFUSED = 1;
    static final int ERROR = 2;

    private static final String USAGE = """
            Usage:
              riktig-java rename --workspace DIR --file FILE --at LINE:COLUMN --new-name NAME [--apply] [--json] [--commit-message]
              riktig-java extract-iife --workspace DIR --file FILE --range LINE:COLUMN-LINE:COLUMN [--apply] [--json] [--commit-message]
              riktig-java move-static-member --workspace DIR --file FILE --at LINE:COLUMN --target QUALIFIED.NAME [--apply] [--json] [--commit-message]
              riktig-java move-to-instance-method --workspace DIR --file FILE --at LINE:COLUMN --receiver PARAMETER [--apply] [--json] [--commit-message]
              riktig-java make-method-static --workspace DIR --file FILE --at LINE:COLUMN [--receiver-name NAME] [--apply] [--json] [--commit-message]

            The workspace is a directory (or a build file in it) whose .java files are compiled together.
            Lines and columns are 1-based; columns count Unicode scalar values. A range's end is exclusive.
            By default the change is printed as a diff; --apply writes it, and --json prints the protocol result.
            """;
    private static final Set<String> FLAGS = Set.of("--apply", "--json", "--commit-message");

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("help")) {
            out.print(USAGE);
            return args.length == 0 ? ERROR : SUCCESS;
        }
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (FLAGS.contains(args[i])) {
                options.put(args[i], "true");
            } else if (args[i].startsWith("--") && i + 1 < args.length) {
                options.put(args[i], args[++i]);
            } else {
                err.println("Unexpected argument: " + args[i]);
                err.print(USAGE);
                return ERROR;
            }
        }
        try {
            return run(args[0], options, out);
        } catch (UsageException e) {
            err.println(e.getMessage());
            return ERROR;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return ERROR;
        }
    }

    private static int run(String command, Map<String, String> options, PrintStream out)
            throws IOException, UsageException {
        Workspace workspace = Workspace.load(Path.of(required(options, "--workspace")));
        String file = workspace.relativeFile(required(options, "--file"));
        String text = workspace.sources().get(file);
        if (text == null) {
            throw new UsageException("The file " + file + " is not a .java file in the workspace " + workspace.root());
        }
        Outcome outcome;
        CommitMessage commit;
        switch (command) {
            case "rename" -> {
                int cursor = offset(text, required(options, "--at"));
                String newName = required(options, "--new-name");
                outcome = Rename.run(workspace.sources(), file, cursor, newName);
                String oldName = nameAt(text, cursor);
                SourceText.Position at = SourceText.position(text, cursor);
                commit = new CommitMessage(
                        ". r Rename " + oldName + " to " + newName,
                        "Behavior-preserving rename.",
                        "Refactor: rename java " + file + ":" + at.line() + ":" + at.column() + " " + oldName + " -> "
                                + newName);
            }
            case "extract-iife" -> {
                String[] range = required(options, "--range").split("-", 2);
                if (range.length != 2) {
                    throw new UsageException("--range must be LINE:COLUMN-LINE:COLUMN");
                }
                int start = offset(text, range[0]);
                int end = offset(text, range[1]);
                outcome = ExtractIife.run(workspace.sources(), file, start, end);
                commit = new CommitMessage(
                        ". r Extract IIFE in " + file,
                        "Behavior-preserving wrap in an immediately-invoked lambda.",
                        "Refactor: extract-iife java " + file + ":" + options.get("--range"));
            }
            case "make-method-static" -> {
                int cursor = offset(text, required(options, "--at"));
                outcome = MakeMethodStatic.run(workspace.sources(), file, cursor, options.get("--receiver-name"));
                String name = nameAt(text, cursor);
                SourceText.Position at = SourceText.position(text, cursor);
                commit = new CommitMessage(
                        ". r Make " + name + " static",
                        "Behavior-preserving change of an instance method to a static method taking its receiver.",
                        "Refactor: make-method-static java " + file + ":" + at.line() + ":" + at.column());
            }
            case "move-static-member" -> {
                int cursor = offset(text, required(options, "--at"));
                String target = required(options, "--target");
                outcome = MoveStaticMember.run(workspace.sources(), file, cursor, target);
                String name = nameAt(text, cursor);
                SourceText.Position at = SourceText.position(text, cursor);
                commit = new CommitMessage(
                        ". r Move " + name + " to " + target,
                        "Behavior-preserving move of a static member.",
                        "Refactor: move-static-member java " + file + ":" + at.line() + ":" + at.column() + " -> "
                                + target);
            }
            case "move-to-instance-method" -> {
                int cursor = offset(text, required(options, "--at"));
                String receiver = required(options, "--receiver");
                outcome = MoveToInstanceMethod.run(workspace.sources(), file, cursor, receiver);
                String name = nameAt(text, cursor);
                SourceText.Position at = SourceText.position(text, cursor);
                commit = new CommitMessage(
                        ". r Move " + name + " to an instance method on " + receiver,
                        "Behavior-preserving move of a static method to an instance method of its parameter's class.",
                        "Refactor: move-to-instance-method java " + file + ":" + at.line() + ":" + at.column() + " "
                                + receiver);
            }
            default -> throw new UsageException("Unknown command: " + command + "\n" + USAGE);
        }

        if (options.containsKey("--json")) {
            out.println(Json.write(ProtocolResult.of(workspace, outcome, commit)));
        }
        if (outcome instanceof Outcome.Refused refused) {
            if (!options.containsKey("--json")) {
                out.println("Refused (" + refused.reason().code() + "): "
                        + refused.reason().message());
                if (refused.reason().suggestion() != null) {
                    out.println("Suggestion: " + refused.reason().suggestion());
                }
            }
            return REFUSED;
        }
        Outcome.Success success = (Outcome.Success) outcome;
        if (!options.containsKey("--json")) {
            out.print(UnifiedDiff.of(workspace.sources(), success.edits()));
            success.warnings().forEach(w -> out.println("Warning (" + w.code() + "): " + w.message()));
        }
        if (options.containsKey("--commit-message") && !options.containsKey("--json")) {
            out.println(commit.subject() + "\n\n" + commit.body() + "\n\n" + commit.trailer());
        }
        if (options.containsKey("--apply")) {
            for (var entry :
                    TextEdit.apply(workspace.sources(), success.edits()).entrySet()) {
                if (!entry.getValue().equals(workspace.sources().get(entry.getKey()))) {
                    Files.writeString(
                            workspace.root().resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
                }
            }
        }
        return SUCCESS;
    }

    private static String nameAt(String text, int cursor) {
        for (Token token : Lexer.tokenize(text)) {
            if (token.kind() == Token.Kind.IDENTIFIER && token.start() <= cursor && cursor <= token.end()) {
                return token.text();
            }
        }
        return "the symbol";
    }

    private static String required(Map<String, String> options, String name) throws UsageException {
        String value = options.get(name);
        if (value == null) {
            throw new UsageException("Missing " + name + "\n" + USAGE);
        }
        return value;
    }

    private static int offset(String text, String lineColumn) throws UsageException {
        String[] parts = lineColumn.split(":");
        try {
            if (parts.length == 2) {
                int offset = SourceText.offset(text, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                if (offset >= 0) {
                    return offset;
                }
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new UsageException("Not a position in the file: " + lineColumn + " (expected LINE:COLUMN)");
    }

    static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
