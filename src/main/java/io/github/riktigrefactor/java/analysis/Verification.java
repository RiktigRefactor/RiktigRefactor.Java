package io.github.riktigrefactor.java.analysis;

import io.github.riktigrefactor.java.Refusal;
import io.github.riktigrefactor.java.RefusalCodes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** Compiles edited text and checks it against the original: it must compile, and every name must bind as before. */
public final class Verification {
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
        Map<String, List<TextEdit>> editsByFile = TextEdit.byFile(edits);
        Compilation edited = Compilation.analyze(TextEdit.apply(original.sources, edits));
        if (!edited.errors.isEmpty()) {
            var error = edited.errors.getFirst();
            String file = Compilation.fileOf(error);
            int offset = originalOffset(editsByFile, file, error.getStartPosition());
            return new Verification(edited, editsByFile, new Refusal(
                    codeForError.apply(error),
                    "The result would not compile: " + error.getMessage(null),
                    file, offset, offset));
        }
        Map<String, Bindings.Binding> before = Bindings.of(original, Map.of()).record(compareTypes);
        Map<String, Bindings.Binding> after = Bindings.of(edited, editsByFile).record(compareTypes);
        for (var entry : before.entrySet()) {
            Bindings.Binding old = entry.getValue();
            Bindings.Binding now = after.get(entry.getKey());
            if (now == null || !Objects.equals(old.binding(), now.binding())) {
                return new Verification(edited, editsByFile, new Refusal(
                        RefusalCodes.BINDING_WOULD_CHANGE,
                        "A name here would no longer refer to the same declaration"
                                + (compareTypes ? " or have the same type" : "") + ": was " + old.binding()
                                + ", would be " + (now == null ? "nothing" : now.binding()) + ".",
                        old.file(), (int) old.start(), (int) old.start()));
            }
        }
        for (var entry : after.entrySet()) {
            if (!before.containsKey(entry.getKey())) {
                Bindings.Binding now = entry.getValue();
                return new Verification(edited, editsByFile, new Refusal(
                        RefusalCodes.BINDING_WOULD_CHANGE,
                        "A name here would refer to " + now.binding() + ", which it did not before.",
                        now.file(), (int) now.start(), (int) now.start()));
            }
        }
        return new Verification(edited, editsByFile, null);
    }

    private static int originalOffset(Map<String, List<TextEdit>> editsByFile, String file, long editedOffset) {
        if (file == null || editedOffset < 0) {
            return 0;
        }
        OffsetMap map = new OffsetMap(editsByFile.getOrDefault(file, List.of()));
        long original = map.mapStart(editedOffset);
        if (original == OffsetMap.UNMAPPED) {
            original = map.mapEnd(editedOffset);
        }
        if (original == OffsetMap.UNMAPPED) {
            for (TextEdit edit : editsByFile.get(file)) {
                original = edit.start();
            }
        }
        return (int) original;
    }
}
