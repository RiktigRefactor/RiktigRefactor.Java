public class Program {
    static class Order {
        static String total(Object order, int qty) {
            return "existing";
        }

        String /*|*/total(int qty) {
            return "instance";
        }
    }

    public static void main(String[] args) {
        Order order = new Order();
        if (!Order.total(order, 1).equals("existing") || !order.total(1).equals("instance")) {
            throw new AssertionError();
        }
    }
}
