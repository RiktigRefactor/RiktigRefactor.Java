import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        for (String word : List.of("a", "b")) {
            /*[*/out.add(word.toUpperCase());/*]*/
        }
        if (!out.equals(List.of("A", "B"))) {
            throw new AssertionError(out);
        }
    }
}
