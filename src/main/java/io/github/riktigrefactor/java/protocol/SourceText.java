package io.github.riktigrefactor.java.protocol;

/** Converts between offsets in a string and 1-based line and column, where columns count Unicode scalar values. */
public final class SourceText {
    private SourceText() {}

    public record Position(int line, int column) {}

    public static Position position(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                line++;
                lineStart = i + 1;
            }
        }
        return new Position(line, text.codePointCount(lineStart, offset) + 1);
    }

    /** The offset of a line and column, or -1 if the text has no such position. */
    public static int offset(String text, int line, int column) {
        int lineStart = 0;
        for (int current = 1; current < line; current++) {
            int newline = nextLineStart(text, lineStart);
            if (newline < 0) {
                return -1;
            }
            lineStart = newline;
        }
        int lineEnd = nextLineStart(text, lineStart);
        lineEnd = lineEnd < 0 ? text.length() : lineEnd;
        try {
            int offset = text.offsetByCodePoints(lineStart, column - 1);
            return offset <= lineEnd ? offset : -1;
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    private static int nextLineStart(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (c == '\r') {
                return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
            }
        }
        return -1;
    }
}
