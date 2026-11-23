import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        for (String word : List.of("a", "b")) {
            ((java.lang.Runnable) () -> { out.add(word.toUpperCase()); }).run();
        }
        if (!out.equals(List.of("A", "B"))) {
            throw new AssertionError(out);
        }
    }
}
