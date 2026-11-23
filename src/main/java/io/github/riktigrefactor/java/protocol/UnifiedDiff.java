package io.github.riktigrefactor.java.protocol;

import io.github.riktigrefactor.java.analysis.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A unified diff, without context lines, of the lines that a set of edits touches. */
public final class UnifiedDiff {
    private UnifiedDiff() {}

    public static String of(Map<String, String> sources, List<TextEdit> edits) {
        StringBuilder out = new StringBuilder();
        for (var entry : TextEdit.byFile(edits).entrySet()) {
            String text = sources.get(entry.getKey());
            out.append("--- a/").append(entry.getKey()).append('\n');
            out.append("+++ b/").append(entry.getKey()).append('\n');
            appendHunks(text, entry.getValue(), out);
        }
        return out.toString();
    }

    private static void appendHunks(String text, List<TextEdit> edits, StringBuilder out) {
        List<Integer> lineStarts = new ArrayList<>(List.of(0));
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineStarts.add(i + 1);
            }
        }
        int lineDelta = 0;
        int i = 0;
        while (i < edits.size()) {
            int firstLine = lineOf(lineStarts, edits.get(i).start());
            int lastLine = lineOf(lineStarts, Math.max(edits.get(i).start(), edits.get(i).end() - 1));
            int j = i + 1;
            while (j < edits.size() && lineOf(lineStarts, edits.get(j).start()) <= lastLine + 1) {
                lastLine = Math.max(lastLine, lineOf(lineStarts, Math.max(edits.get(j).start(), edits.get(j).end() - 1)));
                j++;
            }
            int chunkStart = lineStarts.get(firstLine);
            int chunkEnd = lastLine + 1 < lineStarts.size() ? lineStarts.get(lastLine + 1) : text.length();
            StringBuilder replaced = new StringBuilder();
            int copied = chunkStart;
            for (TextEdit edit : edits.subList(i, j)) {
                replaced.append(text, copied, edit.start()).append(edit.newText());
                copied = edit.end();
            }
            replaced.append(text, copied, chunkEnd);
            List<String> oldLines = lines(text.substring(chunkStart, chunkEnd));
            List<String> newLines = lines(replaced.toString());
            out.append("@@ -").append(firstLine + 1).append(',').append(oldLines.size())
                    .append(" +").append(firstLine + 1 + lineDelta).append(',').append(newLines.size()).append(" @@\n");
            oldLines.forEach(line -> out.append('-').append(line).append('\n'));
            newLines.forEach(line -> out.append('+').append(line).append('\n'));
            lineDelta += newLines.size() - oldLines.size();
            i = j;
        }
    }

    private static int lineOf(List<Integer> lineStarts, int offset) {
        int low = 0;
        int high = lineStarts.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (lineStarts.get(middle) <= offset) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static List<String> lines(String chunk) {
        List<String> result = new ArrayList<>(List.of(chunk.split("\r?\n", -1)));
        if (!result.isEmpty() && result.getLast().isEmpty()) {
            result.removeLast();
        }
        return result;
    }
}
