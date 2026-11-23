import java.util.ArrayList;
import java.util.List;

public class Program {
    static class Order {
        int price = 3;
        final List<String> log = new ArrayList<>();

        int discount() {
            return 1;
        }

        int /*|*/total(int qty) {
            log.add("total");
            Runnable note = () -> this.log.add("lambda");
            note.run();
            return price * qty - discount() + describe(this).length();
        }

        int twiceTotal() {
            return total(2) * 2;
        }
    }

    static String describe(Order order) {
        return "";
    }

    public static void main(String[] args) {
        Order order = new Order();
        int value = order.total(3) + new Order().total(1) + order.twiceTotal();
        if (value != 8 + 2 + 10 || order.log.size() != 4) {
            throw new AssertionError(value + " " + order.log);
        }
    }
}
