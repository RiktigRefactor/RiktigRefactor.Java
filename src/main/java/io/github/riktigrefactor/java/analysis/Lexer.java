package io.github.riktigrefactor.java.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits Java source into tokens, skipping whitespace and comments. Used only to find the exact position of a name
 * inside a tree whose extent the compiler reports; all meaning comes from the compiler.
 */
public final class Lexer {
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("(?<!\\\\)(?:\\\\\\\\)*\\\\u+[0-9a-fA-F]{4}");

    private Lexer() {}

    /** Unicode escapes are translated before lexing, so source offsets no longer match the text; such files are refused. */
    public static int firstUnicodeEscape(String text) {
        var matcher = UNICODE_ESCAPE.matcher(text);
        if (matcher.find()) {
            return text.indexOf("\\u", matcher.start());
        }
        return -1;
    }

    private static boolean endsWithExponentMarker(String numberSoFar) {
        char last = numberSoFar.charAt(numberSoFar.length() - 1);
        boolean hexadecimal = numberSoFar.startsWith("0x") || numberSoFar.startsWith("0X");
        return hexadecimal ? (last == 'p' || last == 'P') : (last == 'e' || last == 'E');
    }

    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (text.startsWith("//", i)) {
                while (i < n && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                    i++;
                }
            } else if (text.startsWith("/*", i)) {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
            } else if (text.startsWith("\"\"\"", i)) {
                int start = i;
                i += 3;
                while (i < n && !text.startsWith("\"\"\"", i)) {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                i = Math.min(n, i + 3);
                tokens.add(new Token(Token.Kind.LITERAL, start, i, text.substring(start, i)));
            } else if (c == '"' || c == '\'') {
                int start = i;
                i++;
                while (i < n && text.charAt(i) != c && text.charAt(i) != '\n') {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                i = Math.min(n, i + 1);
                tokens.add(new Token(Token.Kind.LITERAL, start, i, text.substring(start, i)));
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
                int start = i;
                i++;
                while (i < n) {
                    char d = text.charAt(i);
                    if (Character.isLetterOrDigit(d) || d == '_' || d == '.') {
                        i++;
                    } else if ((d == '+' || d == '-') && endsWithExponentMarker(text.substring(start, i))) {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(Token.Kind.LITERAL, start, i, text.substring(start, i)));
            } else if (Character.isJavaIdentifierStart(text.codePointAt(i))) {
                int start = i;
                i += Character.charCount(text.codePointAt(i));
                while (i < n && Character.isJavaIdentifierPart(text.codePointAt(i))) {
                    i += Character.charCount(text.codePointAt(i));
                }
                tokens.add(new Token(Token.Kind.IDENTIFIER, start, i, text.substring(start, i)));
            } else {
                tokens.add(new Token(Token.Kind.OPERATOR, i, i + 1, String.valueOf(c)));
                i++;
            }
        }
        return tokens;
    }
}
