import java.util.ArrayList;
import java.util.List;

public class Program {
    static final List<String> trace = new ArrayList<>();

    static class Order {
    }

    static class Pricing {
        static int /*|*/total(Order order, int qty) {
            trace.add("body");
            return qty;
        }
    }

    static Order find() {
        return null;
    }

    public static void main(String[] args) {
        Pricing.total(find(), 1);
        if (!trace.equals(List.of("body"))) {
            throw new AssertionError(trace);
        }
    }
}
