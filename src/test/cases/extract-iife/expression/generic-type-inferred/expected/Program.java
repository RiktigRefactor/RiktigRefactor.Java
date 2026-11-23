import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> names = ((java.util.function.Supplier<java.util.ArrayList<java.lang.String>>) () -> new ArrayList<>(List.of("a", "b"))).get();
        names.add("c");
        if (names.size() != 3) {
            throw new AssertionError(names);
        }
    }
}
