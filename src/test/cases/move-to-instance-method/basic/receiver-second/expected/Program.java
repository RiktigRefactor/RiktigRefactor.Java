public class Program {
    static class Order {
        final int price;

        Order(int price) {
            this.price = price;
        }

        int total(int qty) {
            return this.price * qty;
        }
    }

    static class Pricing {
    }

    public static void main(String[] args) {
        Order order = new Order(5);
        int qty = 2;
        if (order.total(qty) != 10 || new Order(3).total(1) != 3) {
            throw new AssertionError();
        }
    }
}
