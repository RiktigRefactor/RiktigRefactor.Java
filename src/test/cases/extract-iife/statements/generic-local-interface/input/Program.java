import java.util.*;

public class Program {
    static <T extends Comparable<T>> T first(List<T> items) {
        T result = null;
        int offset = 0;
        /*[*/result = items.get(offset);
        result.compareTo(items.get(offset + 1));/*]*/
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (!first(List.of("x", "y")).equals("x")) {
            throw new AssertionError();
        }
    }
}
