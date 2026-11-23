import java.util.ArrayList;
import java.util.List;

public class Program {
    static class Order {
        int price = 3;
        final List<String> log = new ArrayList<>();

        int discount() {
            return 1;
        }

        static int total(Program.Order order, int qty) {
            order.log.add("total");
            Runnable note = () -> order.log.add("lambda");
            note.run();
            return order.price * qty - order.discount() + describe(order).length();
        }

        int twiceTotal() {
            return Program.Order.total(this, 2) * 2;
        }
    }

    static String describe(Order order) {
        return "";
    }

    public static void main(String[] args) {
        Order order = new Order();
        int value = Program.Order.total(order, 3) + Program.Order.total(new Order(), 1) + order.twiceTotal();
        if (value != 8 + 2 + 10 || order.log.size() != 4) {
            throw new AssertionError(value + " " + order.log);
        }
    }
}
