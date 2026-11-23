package io.github.riktigrefactor.java.analysis;

import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.RefusalCodes;
import java.util.Map;

/** A workspace compiled without errors, or the refusal that explains why it can't be analyzed. */
public record Prepared(Compilation compilation, Outcome refusal) {
    public static Prepared analyze(Map<String, String> sources) {
        for (var entry : sources.entrySet()) {
            int escape = Lexer.firstUnicodeEscape(entry.getValue());
            if (escape >= 0) {
                return new Prepared(
                        null,
                        Outcome.refuse(
                                RefusalCodes.UNICODE_ESCAPE,
                                "The file contains a Unicode escape, which the tool does not support yet.",
                                entry.getKey(),
                                escape,
                                escape));
            }
        }
        Compilation compilation = Compilation.analyze(sources);
        if (!compilation.errors.isEmpty()) {
            var error = compilation.errors.getFirst();
            int offset = (int) Math.max(0, error.getStartPosition());
            return new Prepared(
                    null,
                    Outcome.refuse(
                            RefusalCodes.INPUT_HAS_ERRORS,
                            "The code does not compile, so its meaning can't be known: " + error.getMessage(null),
                            Compilation.fileOf(error),
                            offset,
                            offset));
        }
        return new Prepared(compilation, null);
    }
}
