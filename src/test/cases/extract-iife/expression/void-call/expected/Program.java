import java.util.*;

public class Program {
    static void log(List<String> trace, String entry) {
        trace.add(entry);
    }

    public static void main(String[] args) throws Exception {
        List<String> trace = new ArrayList<>();
        ((java.util.function.Consumer<java.util.List<java.lang.String>>) (traceArg) -> log(traceArg, "a")).accept(trace);
        log(trace, "b");
        if (!trace.equals(List.of("a", "b"))) {
            throw new AssertionError(trace);
        }
    }
}
