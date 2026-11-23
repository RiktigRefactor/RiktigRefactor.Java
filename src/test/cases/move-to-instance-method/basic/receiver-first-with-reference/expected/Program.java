import java.util.function.ToIntFunction;

public class Program {
    static class Order {
        int price = 3;
        Order next;

        int total(int qty) {
            if (qty == 0) {
                return 0;
            }
            return this.price * qty * Program.Pricing.RATE + this.total(qty - 1);
        }
    }

    static class Pricing {
        static final int RATE = 2;

    }

    public static void main(String[] args) {
        Order order = new Order();
        ToIntFunction<Order> one = o -> 6;
        if (order.total(2) != 18 || one.applyAsInt(new Order()) != 6) {
            throw new AssertionError();
        }
    }
}
