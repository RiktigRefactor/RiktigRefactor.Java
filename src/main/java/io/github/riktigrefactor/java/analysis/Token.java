package io.github.riktigrefactor.java.analysis;

public record Token(Kind kind, int start, int end, String text) {
    public enum Kind {
        IDENTIFIER,
        LITERAL,
        OPERATOR
    }

    public boolean isIdentifier(String name) {
        return kind == Kind.IDENTIFIER && text.equals(name);
    }
}
