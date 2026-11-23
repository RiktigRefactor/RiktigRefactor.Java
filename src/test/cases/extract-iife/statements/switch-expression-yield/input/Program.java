import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        int day = 2;
        /*[*/String name = switch (day) {
            case 1 -> "one";
            default -> {
                yield "other";
            }
        };
        out.add(name);/*]*/
        if (!out.equals(List.of("other"))) {
            throw new AssertionError(out);
        }
    }
}
