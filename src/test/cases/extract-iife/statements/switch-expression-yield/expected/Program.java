import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        int day = 2;
        interface Extracted { void apply(int dayArg, java.util.List<java.lang.String> outArg); }
        ((Extracted) (dayArg, outArg) -> { String name = switch (dayArg) {
            case 1 -> "one";
            default -> {
                yield "other";
            }
        };
        outArg.add(name); }).apply(day, out);
        if (!out.equals(List.of("other"))) {
            throw new AssertionError(out);
        }
    }
}
