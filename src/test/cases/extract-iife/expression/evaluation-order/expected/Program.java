import java.util.*;

public class Program {
    static int next(List<Integer> trace, int value) {
        trace.add(value);
        return value;
    }

    public static void main(String[] args) throws Exception {
        List<Integer> trace = new ArrayList<>();
        int result = next(trace, 1) + ((java.util.function.ToIntFunction<java.util.List<java.lang.Integer>>) (traceArg) -> next(traceArg, 2) * next(traceArg, 3)).applyAsInt(trace) + next(trace, 4);
        if (result != 11 || !trace.equals(List.of(1, 2, 3, 4))) {
            throw new AssertionError(trace);
        }
    }
}
