import java.util.ArrayList;
import java.util.List;

public class Program {
    static final List<String> trace = new ArrayList<>();

    static class Order {
        int /*|*/total(int qty) {
            trace.add("body");
            return qty;
        }
    }

    static int argument() {
        trace.add("argument");
        return 1;
    }

    static Order find() {
        return null;
    }

    public static void main(String[] args) {
        try {
            find().total(argument());
        } catch (NullPointerException e) {
            trace.add("npe");
        }
        if (!trace.equals(List.of("argument", "npe"))) {
            throw new AssertionError(trace);
        }
    }
}
