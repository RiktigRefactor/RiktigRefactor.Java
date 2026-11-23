import java.util.function.ToIntFunction;

public class Program {
    static class Order {
        int price = 3;
        Order next;
    }

    static class Pricing {
        static final int RATE = 2;

        static int /*|*/total(Order order, int qty) {
            if (qty == 0) {
                return 0;
            }
            return order.price * qty * RATE + total(order, qty - 1);
        }
    }

    public static void main(String[] args) {
        Order order = new Order();
        ToIntFunction<Order> one = o -> 6;
        if (Pricing.total(order, 2) != 18 || one.applyAsInt(new Order()) != 6) {
            throw new AssertionError();
        }
    }
}
