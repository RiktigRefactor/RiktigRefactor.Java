import java.util.ArrayList;
import java.util.List;

public class Program {
    static final List<String> trace = new ArrayList<>();

    static class Source {
        static {
            trace.add("Source initialized");
        }

        static int /*|*/value() {
            return 1;
        }
    }

    static class Target {
    }

    public static void main(String[] args) {
        int value = Source.value();
        if (value != 1 || !trace.equals(List.of("Source initialized"))) {
            throw new AssertionError(trace);
        }
    }
}
