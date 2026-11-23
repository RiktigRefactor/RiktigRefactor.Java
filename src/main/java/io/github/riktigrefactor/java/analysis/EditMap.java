package io.github.riktigrefactor.java.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps the extent of a tree in an edited file back to the original text.
 *
 * <p>A tree in unedited text maps by the shift of the edits before it. A tree that encloses edits keeps its original
 * extent. A tree inside a copy edit maps to where the copied text came from. A tree exactly spanning a replacement maps
 * to the replaced range. Any other tree inside edited text has no original and maps to null.
 */
public final class EditMap {
    /** An original extent; {@code viaCopy} when the tree lies in text that a copy edit moved. */
    public record Span(String file, long start, long end, boolean viaCopy) {}

    private final String file;
    private final List<TextEdit> sortedEdits;

    public EditMap(String file, List<TextEdit> sortedEdits) {
        this.file = file;
        this.sortedEdits = sortedEdits;
    }

    public static EditMap of(String file, Map<String, List<TextEdit>> editsByFile) {
        return new EditMap(file, editsByFile.getOrDefault(file, List.of()));
    }

    public Span map(long start, long end) {
        if (start < 0) {
            return new Span(file, start, end, false);
        }
        if (end < 0) {
            Span point = map(start, start);
            return point == null ? null : new Span(point.file(), point.start(), end, point.viaCopy());
        }
        long delta = 0;
        int index = 0;
        long originalStart = Long.MIN_VALUE;
        for (; index < sortedEdits.size(); index++) {
            TextEdit edit = sortedEdits.get(index);
            long newStart = edit.start() + delta;
            long newEnd = newStart + edit.newText().length();
            if (start < newStart) {
                originalStart = start - delta;
                break;
            }
            if (start == newStart && end > newEnd) {
                originalStart = edit.start();
                break;
            }
            if (start < newEnd || (start == newStart && end <= newEnd)) {
                return inside(edit, newStart, newEnd, start, end);
            }
            delta += edit.newText().length() - (edit.end() - edit.start());
        }
        if (originalStart == Long.MIN_VALUE) {
            originalStart = start - delta;
        }
        for (; index < sortedEdits.size(); index++) {
            TextEdit edit = sortedEdits.get(index);
            long newStart = edit.start() + delta;
            long newEnd = newStart + edit.newText().length();
            if (end <= newStart) {
                return new Span(file, originalStart, end - delta, false);
            }
            if (end < newEnd) {
                return null;
            }
            if (end == newEnd) {
                return new Span(file, originalStart, edit.end(), false);
            }
            delta += edit.newText().length() - (edit.end() - edit.start());
        }
        return new Span(file, originalStart, end - delta, false);
    }

    /** A tree starting inside an edit's new text, at or after its start. */
    private Span inside(TextEdit edit, long newStart, long newEnd, long start, long end) {
        if (edit.isCopy()) {
            long coreStart = newStart + edit.prefixLength();
            long coreEnd = coreStart + edit.coreLength();
            if (start < coreStart || end > coreEnd) {
                return null;
            }
            List<TextEdit> shifted = new ArrayList<>();
            for (TextEdit inner : edit.innerEdits()) {
                shifted.add(new TextEdit(
                        inner.file(),
                        inner.start() - edit.originStart(),
                        inner.end() - edit.originStart(),
                        inner.newText()));
            }
            shifted.sort(java.util.Comparator.comparingInt(TextEdit::start));
            Span inner = new EditMap(edit.originFile(), shifted).map(start - coreStart, end - coreStart);
            if (inner == null) {
                return null;
            }
            return new Span(
                    edit.originFile(), inner.start() + edit.originStart(), inner.end() + edit.originStart(), true);
        }
        boolean isReplacement = edit.end() > edit.start();
        if (isReplacement && start == newStart && end == newEnd) {
            return new Span(file, edit.start(), edit.end(), false);
        }
        return null;
    }

    /** Whether [start, end) runs from the beginning of one plain insertion to the end of another: a tree the edits created. */
    public boolean spansInsertions(long start, long end) {
        boolean startsAtInsertion = false;
        long delta = 0;
        for (TextEdit edit : sortedEdits) {
            long newStart = edit.start() + delta;
            long newEnd = newStart + edit.newText().length();
            boolean isInsertion = edit.start() == edit.end() && !edit.isCopy();
            if (isInsertion && start == newStart) {
                startsAtInsertion = true;
            }
            if (isInsertion && startsAtInsertion && end == newEnd) {
                return true;
            }
            delta += edit.newText().length() - (edit.end() - edit.start());
        }
        return false;
    }
}
