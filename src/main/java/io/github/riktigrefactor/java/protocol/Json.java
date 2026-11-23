package io.github.riktigrefactor.java.protocol;

import java.util.List;
import java.util.Map;

/** Writes the small JSON documents of the protocol, so the tool needs no JSON library. */
public final class Json {
    private Json() {}

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, "");
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, String indent) {
        switch (value) {
            case null -> out.append("null");
            case String text -> quote(text, out);
            case Number number -> out.append(number);
            case Boolean bool -> out.append(bool);
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (var entry : map.entrySet()) {
                    out.append(indent).append("  ");
                    quote(String.valueOf(entry.getKey()), out);
                    out.append(": ");
                    write(entry.getValue(), out, indent + "  ");
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                out.append(indent).append('}');
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    out.append(indent).append("  ");
                    write(list.get(i), out, indent + "  ");
                    out.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                out.append(indent).append(']');
            }
            default -> throw new IllegalArgumentException("Can't write " + value.getClass());
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
