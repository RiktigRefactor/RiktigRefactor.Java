package io.github.riktigrefactor.java;

/** Why a refactoring was refused; {@code start} and {@code end} are offsets into {@code file}, which may be null. */
public record Refusal(String code, String message, String file, int start, int end, String suggestion) {
    public Refusal(String code, String message, String file, int start, int end) {
        this(code, message, file, start, end, null);
    }
}
