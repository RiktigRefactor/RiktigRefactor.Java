import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        for (String word : List.of("a", "b")) {
            ((java.util.function.BiConsumer<java.util.List<java.lang.String>, java.lang.String>) (outArg, wordArg) -> { outArg.add(wordArg.toUpperCase()); }).accept(out, word);
        }
        if (!out.equals(List.of("A", "B"))) {
            throw new AssertionError(out);
        }
    }
}
