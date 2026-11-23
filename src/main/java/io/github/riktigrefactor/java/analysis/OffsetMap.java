package io.github.riktigrefactor.java.analysis;

import java.util.List;

/**
 * Maps offsets in an edited file back to the original file. Offsets inside inserted text have no original and map to
 * {@link #UNMAPPED}; a replaced name maps its start and end to the start and end of the original name.
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
                boolean isReplacement = edit.end() > edit.start();
                return position == newStart && isReplacement ? edit.start() : UNMAPPED;
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
                boolean isReplacement = edit.end() > edit.start();
                return position == newEnd && isReplacement ? edit.end() : UNMAPPED;
            }
            delta += edit.newText().length() - (edit.end() - edit.start());
        }
        return position - delta;
    }
}
