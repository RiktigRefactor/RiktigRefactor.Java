import java.util.ArrayList;
import java.util.List;

public class Program {
    static final List<String> trace = new ArrayList<>();

    static class Order {
        Order() {
            trace.add("order");
        }
    }

    static class Pricing {
        static int /*|*/total(int qty, Order order) {
            return qty;
        }
    }

    static int quantity() {
        trace.add("qty");
        return 1;
    }

    public static void main(String[] args) {
        Pricing.total(quantity(), new Order());
        if (!trace.equals(List.of("qty", "order"))) {
            throw new AssertionError(trace);
        }
    }
}
