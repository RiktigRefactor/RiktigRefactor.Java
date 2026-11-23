package io.github.riktigrefactor.java;

import io.github.riktigrefactor.java.analysis.TextEdit;
import java.util.List;

public sealed interface Outcome {
    record Success(List<TextEdit> edits, List<Warning> warnings) implements Outcome {}

    record Refused(Refusal reason) implements Outcome {}

    static Outcome refuse(String code, String message, String file, int start, int end) {
        return new Refused(new Refusal(code, message, file, start, end));
    }

    static Outcome refuse(String code, String message, String file, int start, int end, String suggestion) {
        return new Refused(new Refusal(code, message, file, start, end, suggestion));
    }
}
