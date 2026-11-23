import java.util.function.IntUnaryOperator;

public class Program {
    static class Pricing {
        static final int RATE = 3;

        static int helper(int x) {
            return x + 1;
        }

        static int twice(int qty) {
            return Program.Order.price(qty) * 2;
        }
    }

    static class Order {
        static int unused() {
            return 0;
        }

        static int price(int qty) {
            return Program.Pricing.helper(qty) * Program.Pricing.RATE;
        }
    }

    public static void main(String[] args) {
        IntUnaryOperator op = Program.Order::price;
        if (Program.Order.price(1) != 6 || Pricing.twice(1) != 12 || op.applyAsInt(2) != 9) {
            throw new AssertionError();
        }
    }
}
