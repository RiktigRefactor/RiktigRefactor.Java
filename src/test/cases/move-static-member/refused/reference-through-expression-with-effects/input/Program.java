import java.util.ArrayList;
import java.util.List;

public class Program {
    static final List<String> trace = new ArrayList<>();

    static class Pricing {
        static int /*|*/rate() {
            return 2;
        }
    }

    static class Target {
    }

    static Pricing make() {
        trace.add("make");
        return null;
    }

    public static void main(String[] args) {
        int rate = make().rate();
        if (rate != 2 || !trace.equals(List.of("make"))) {
            throw new AssertionError(trace);
        }
    }
}
