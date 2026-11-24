public class Program {
    static class Order {
    }

    static class SpecialOrder extends Order {
        int total(int qty) {
            return -1;
        }
    }

    static class Pricing {
        static int /*|*/total(Order order, int qty) {
            return qty;
        }
    }

    public static void main(String[] args) {
        SpecialOrder special = new SpecialOrder();
        if (Pricing.total(special, 2) != 2 || special.total(2) != -1) {
            throw new AssertionError();
        }
    }
}
