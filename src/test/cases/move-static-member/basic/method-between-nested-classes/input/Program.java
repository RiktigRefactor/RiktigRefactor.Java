import java.util.function.IntUnaryOperator;

public class Program {
    static class Pricing {
        static final int RATE = 3;

        static int helper(int x) {
            return x + 1;
        }

        static int /*|*/price(int qty) {
            return helper(qty) * RATE;
        }

        static int twice(int qty) {
            return price(qty) * 2;
        }
    }

    static class Order {
        static int unused() {
            return 0;
        }
    }

    public static void main(String[] args) {
        IntUnaryOperator op = Pricing::price;
        if (Pricing.price(1) != 6 || Pricing.twice(1) != 12 || op.applyAsInt(2) != 9) {
            throw new AssertionError();
        }
    }
}
