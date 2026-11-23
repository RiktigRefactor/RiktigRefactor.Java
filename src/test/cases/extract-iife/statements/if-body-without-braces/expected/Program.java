import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        if (args.length == 0)
            ((java.util.function.Consumer<java.util.List<java.lang.String>>) (outArg) -> { outArg.add("none"); }).accept(out);
        if (!out.equals(List.of("none"))) {
            throw new AssertionError(out);
        }
    }
}
