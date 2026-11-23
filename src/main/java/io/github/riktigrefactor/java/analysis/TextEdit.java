package io.github.riktigrefactor.java.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replaces the characters [start, end) of a file, in the coordinates of the original text. */
public record TextEdit(String file, int start, int end, String newText) {
    public static Map<String, String> apply(Map<String, String> sources, List<TextEdit> edits) {
        Map<String, String> result = new LinkedHashMap<>(sources);
        for (var entry : byFile(edits).entrySet()) {
            String text = sources.get(entry.getKey());
            StringBuilder builder = new StringBuilder();
            int copied = 0;
            for (TextEdit edit : entry.getValue()) {
                builder.append(text, copied, edit.start).append(edit.newText);
                copied = edit.end;
            }
            builder.append(text, copied, text.length());
            result.put(entry.getKey(), builder.toString());
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
