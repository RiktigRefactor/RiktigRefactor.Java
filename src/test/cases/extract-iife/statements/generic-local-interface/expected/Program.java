import java.util.*;

public class Program {
    static <T extends Comparable<T>> T first(List<T> items) {
        T result = null;
        int offset = 0;
        interface Extracted<T extends java.lang.Comparable<T>> { T apply(T resultArg, java.util.List<T> itemsArg, int offsetArg); }
        result = ((Extracted<T>) (resultArg, itemsArg, offsetArg) -> { resultArg = itemsArg.get(offsetArg);
        resultArg.compareTo(itemsArg.get(offsetArg + 1)); return resultArg; }).apply(result, items, offset);
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (!first(List.of("x", "y")).equals("x")) {
            throw new AssertionError();
        }
    }
}
