package io.github.riktigrefactor.java.analysis;

import java.util.List;

/**
 * Maps offsets in an edited file back to the original file. Offsets strictly inside inserted or replacement text have
 * no original and map to {@link #UNMAPPED}; a start at the beginning of an edit and an end at the end of an edit map to
 * the edit's original range, so a tree that grew to enclose inserted text keeps its original extent.
 */
public final class OffsetMap {
    public static final long UNMAPPED = Long.MIN_VALUE;
    private final List<TextEdit> sortedEdits;

    public OffsetMap(List<TextEdit> sortedEdits) {
        this.sortedEdits = sortedEdits;
    }

    public static OffsetMap identity() {
        return new OffsetMap(List.of());
    }

    /** Whether [start, end) runs from the beginning of one insertion to the end of another: a tree the edits created. */
    public boolean spansInsertions(long start, long end) {
        boolean startsAtInsertion = false;
        long delta = 0;
        for (TextEdit edit : sortedEdits) {
            long newStart = edit.start() + delta;
            long newEnd = newStart + edit.newText().length();
            boolean isInsertion = edit.start() == edit.end();
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

    public long mapStart(long position) {
        if (position < 0) {
            return position;
        }
        long delta = 0;
        for (TextEdit edit : sortedEdits) {
            long newStart = edit.start() + delta;
            long newEnd = newStart + edit.newText().length();
            if (position < newStart) {
                return position - delta;
            }
            if (position < newEnd) {
                return position == newStart ? edit.start() : UNMAPPED;
            }
            delta += edit.newText().length() - (edit.end() - edit.start());
        }
        return position - delta;
    }

    public long mapEnd(long position) {
        if (position < 0) {
            return position;
        }
        long delta = 0;
        for (TextEdit edit : sortedEdits) {
            long newStart = edit.start() + delta;
            long newEnd = newStart + edit.newText().length();
            if (position <= newStart) {
                return position - delta;
            }
            if (position <= newEnd) {
                return position == newEnd ? edit.end() : UNMAPPED;
            }
            delta += edit.newText().length() - (edit.end() - edit.start());
        }
        return position - delta;
    }
}
