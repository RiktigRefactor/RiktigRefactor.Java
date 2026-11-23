import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> names = /*[*/new ArrayList<>(List.of("a", "b"))/*]*/;
        names.add("c");
        if (names.size() != 3) {
            throw new AssertionError(names);
        }
    }
}
