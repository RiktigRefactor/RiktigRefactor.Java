import java.util.*;

public class Program {
    static int next(List<Integer> trace, int value) {
        trace.add(value);
        return value;
    }

    public static void main(String[] args) throws Exception {
        List<Integer> trace = new ArrayList<>();
        int result = next(trace, 1) + /*[*/next(trace, 2) * next(trace, 3)/*]*/ + next(trace, 4);
        if (result != 11 || !trace.equals(List.of(1, 2, 3, 4))) {
            throw new AssertionError(trace);
        }
    }
}
