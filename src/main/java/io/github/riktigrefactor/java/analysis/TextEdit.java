package io.github.riktigrefactor.java.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces the characters [start, end) of a file, in the coordinates of the original text.
 *
 * <p>A copy edit's new text is {@code prefix + (original text of originFile [originStart, originEnd) with innerEdits
 * applied) + suffix}; the re-binding check maps trees in the copied text back to where they were, so moved code is
 * still checked.
 */
public record TextEdit(
        String file,
        int start,
        int end,
        String newText,
        String originFile,
        int originStart,
        int originEnd,
        int prefixLength,
        List<TextEdit> innerEdits) {

    public TextEdit(String file, int start, int end, String newText) {
        this(file, start, end, newText, null, -1, -1, 0, List.of());
    }

    public static TextEdit insert(String file, int at, String text) {
        return new TextEdit(file, at, at, text);
    }

    /** Replaces [start, end) of file with prefix + a copy of the origin range (edited by innerEdits) + suffix. */
    public static TextEdit copy(
            Map<String, String> sources,
            String file,
            int start,
            int end,
            String originFile,
            int originStart,
            int originEnd,
            String prefix,
            String suffix,
            List<TextEdit> innerEdits) {
        String core = applyWithin(sources.get(originFile), originStart, originEnd, innerEdits);
        return new TextEdit(
                file,
                start,
                end,
                prefix + core + suffix,
                originFile,
                originStart,
                originEnd,
                prefix.length(),
                List.copyOf(innerEdits));
    }

    public boolean isCopy() {
        return originFile != null;
    }

    /** The length of the copied (and inner-edited) text inside a copy edit's new text. */
    public int coreLength() {
        int length = originEnd - originStart;
        for (TextEdit inner : innerEdits) {
            length += inner.newText.length() - (inner.end - inner.start);
        }
        return length;
    }

    /** The text of [from, to) of {@code text} after applying the edits, which must lie within that range. */
    static String applyWithin(String text, int from, int to, List<TextEdit> edits) {
        List<TextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(TextEdit::start).thenComparingInt(TextEdit::end));
        StringBuilder builder = new StringBuilder();
        int copied = from;
        for (TextEdit edit : sorted) {
            if (edit.start < copied || edit.end > to) {
                throw new IllegalArgumentException("Inner edit outside the copied range or overlapping");
            }
            builder.append(text, copied, edit.start).append(edit.newText);
            copied = edit.end;
        }
        builder.append(text, copied, to);
        return builder.toString();
    }

    public static Map<String, String> apply(Map<String, String> sources, List<TextEdit> edits) {
        Map<String, String> result = new LinkedHashMap<>(sources);
        for (var entry : byFile(edits).entrySet()) {
            String text = sources.get(entry.getKey());
            result.put(entry.getKey(), applyWithin(text, 0, text.length(), entry.getValue()));
        }
        return result;
    }

    public static Map<String, List<TextEdit>> byFile(List<TextEdit> edits) {
        Map<String, List<TextEdit>> result = new LinkedHashMap<>();
        for (TextEdit edit : edits) {
            result.computeIfAbsent(edit.file, f -> new ArrayList<>()).add(edit);
        }
        for (var list : result.values()) {
            list.sort(Comparator.comparingInt(TextEdit::start).thenComparingInt(TextEdit::end));
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).start < list.get(i - 1).end) {
                    throw new IllegalArgumentException("Overlapping edits in " + list.get(i).file);
                }
            }
        }
        return result;
    }
}
