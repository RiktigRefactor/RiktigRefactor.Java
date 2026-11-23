package io.github.riktigrefactor.java.analysis;

import io.github.riktigrefactor.java.Refusal;
import io.github.riktigrefactor.java.RefusalCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** Compiles edited text and checks it against the original: it must compile, and every name must bind as before. */
public final class Verification {
    /** How strictly trees are compared. */
    public enum Mode {
        /** Every tree must be found again, bound as before; edits only rename names or wrap code. */
        STRICT,
        /**
         * Trees the edits rewrote (inside an edited range, and not moved by a copy edit) are not compared, and new
         * trees are allowed; every other tree, including moved code and trees enclosing edits, must bind as before.
         */
        STRUCTURAL
    }

    /** What a tree is expected to bind to after the edits; {@link #IGNORE} to skip it. */
    public static final String IGNORE = "\u0000ignore";

    public final Compilation edited;
    public final Map<String, List<TextEdit>> editsByFile;
    public final Refusal refusal;

    private Verification(Compilation edited, Map<String, List<TextEdit>> editsByFile, Refusal refusal) {
        this.edited = edited;
        this.editsByFile = editsByFile;
        this.refusal = refusal;
    }

    public static Verification verify(
            Compilation original,
            List<TextEdit> edits,
            boolean compareTypes,
            Function<Diagnostic<? extends JavaFileObject>, String> codeForError) {
        return verify(original, edits, compareTypes, codeForError, Mode.STRICT, Bindings.Binding::binding);
    }

    public static Verification verify(
            Compilation original,
            List<TextEdit> edits,
            boolean compareTypes,
            Function<Diagnostic<? extends JavaFileObject>, String> codeForError,
            Mode mode,
            Function<Bindings.Binding, String> expectedAfter) {
        Map<String, List<TextEdit>> editsByFile = TextEdit.byFile(edits);
        Compilation edited = Compilation.analyze(TextEdit.apply(original.sources, edits));
        if (!edited.errors.isEmpty()) {
            var error = edited.errors.getFirst();
            String file = Compilation.fileOf(error);
            int offset = originalOffset(editsByFile, file, error.getStartPosition());
            return new Verification(
                    edited,
                    editsByFile,
                    new Refusal(
                            codeForError.apply(error),
                            "The result would not compile: " + error.getMessage(null),
                            file,
                            offset,
                            offset));
        }
        List<long[]> rewritten = new ArrayList<>();
        List<String> rewrittenFiles = new ArrayList<>();
        for (TextEdit edit : edits) {
            addRewritten(edit, rewritten, rewrittenFiles);
        }
        Map<String, Bindings.Binding> before = Bindings.of(original, Map.of()).record(compareTypes);
        Map<String, Bindings.Binding> after = Bindings.of(edited, editsByFile).record(compareTypes);
        for (var entry : before.entrySet()) {
            Bindings.Binding old = entry.getValue();
            String expected = expectedAfter.apply(old);
            if (IGNORE.equals(expected)) {
                continue;
            }
            Bindings.Binding now = after.get(entry.getKey());
            if (mode == Mode.STRUCTURAL && isRewritten(old, rewritten, rewrittenFiles)) {
                if (now == null || !now.viaCopy()) {
                    continue;
                }
            }
            if (now == null || !matches(expected, now.binding())) {
                return new Verification(
                        edited,
                        editsByFile,
                        new Refusal(
                                RefusalCodes.BINDING_WOULD_CHANGE,
                                "A name here would no longer refer to the same declaration"
                                        + (compareTypes ? " or have the same type" : "") + ": was " + old.binding()
                                        + ", would be " + (now == null ? "nothing" : now.binding()) + ".",
                                old.file(),
                                (int) old.start(),
                                (int) old.start()));
            }
        }
        if (mode == Mode.STRICT) {
            for (var entry : after.entrySet()) {
                if (!before.containsKey(entry.getKey())) {
                    Bindings.Binding now = entry.getValue();
                    return new Verification(
                            edited,
                            editsByFile,
                            new Refusal(
                                    RefusalCodes.BINDING_WOULD_CHANGE,
                                    "A name here would refer to " + now.binding() + ", which it did not before.",
                                    now.file(),
                                    (int) now.start(),
                                    (int) now.start()));
                }
            }
        }
        return new Verification(edited, editsByFile, null);
    }

    /** An expected binding ending in this matches any type: the element alone is compared. */
    public static final String ANY_TYPE = " : *";

    private static boolean matches(String expected, String actual) {
        if (expected.endsWith(ANY_TYPE)) {
            String element = expected.substring(0, expected.length() - ANY_TYPE.length());
            int separator = actual.indexOf(" : ");
            return element.equals(separator < 0 ? actual : actual.substring(0, separator));
        }
        return Objects.equals(expected, actual);
    }

    /** Whether the element part of a binding ("key : type") is {@code key}. */
    public static boolean elementIs(String binding, String key) {
        int separator = binding.indexOf(" : ");
        return (separator < 0 ? binding : binding.substring(0, separator)).equals(key);
    }

    /** Replaces the element part of a binding ("key : type") when it is {@code from}. */
    public static String replaceElement(String binding, String from, String to) {
        int separator = binding.indexOf(" : ");
        String element = separator < 0 ? binding : binding.substring(0, separator);
        String rest = separator < 0 ? "" : binding.substring(separator);
        return element.equals(from) ? to + rest : binding;
    }

    private static void addRewritten(TextEdit edit, List<long[]> ranges, List<String> files) {
        if (edit.end() > edit.start()) {
            ranges.add(new long[] {edit.start(), edit.end()});
            files.add(edit.file());
        }
        for (TextEdit inner : edit.innerEdits()) {
            addRewritten(inner, ranges, files);
        }
    }

    private static boolean isRewritten(Bindings.Binding binding, List<long[]> ranges, List<String> files) {
        for (int i = 0; i < ranges.size(); i++) {
            long[] range = ranges.get(i);
            if (files.get(i).equals(binding.file()) && range[0] <= binding.start() && binding.end() <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private static int originalOffset(Map<String, List<TextEdit>> editsByFile, String file, long editedOffset) {
        if (file == null || editedOffset < 0) {
            return 0;
        }
        EditMap map = EditMap.of(file, editsByFile);
        for (long width = 0; width < 10_000; width++) {
            EditMap.Span span = map.map(editedOffset, editedOffset + width);
            if (span != null) {
                return (int) span.start();
            }
            span = map.map(Math.max(0, editedOffset - width), editedOffset);
            if (span != null) {
                return (int) span.start();
            }
        }
        return 0;
    }
}
