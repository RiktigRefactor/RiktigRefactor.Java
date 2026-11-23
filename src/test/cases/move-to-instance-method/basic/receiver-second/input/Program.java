public class Program {
    static class Order {
        final int price;

        Order(int price) {
            this.price = price;
        }
    }

    static class Pricing {
        static int /*|*/total(int qty, Order order) {
            return order.price * qty;
        }
    }

    public static void main(String[] args) {
        Order order = new Order(5);
        int qty = 2;
        if (Pricing.total(qty, order) != 10 || Pricing.total(1, new Order(3)) != 3) {
            throw new AssertionError();
        }
    }
}
