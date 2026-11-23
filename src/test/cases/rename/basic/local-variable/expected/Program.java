public class Program {
    public static void main(String[] args) {
        int product = 2;
        product = product * 3; // total stays in comments
        if (product != 6) {
            throw new AssertionError(product);
        }
    }
}
