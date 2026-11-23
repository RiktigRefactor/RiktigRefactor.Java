public class Program {
    public static void main(String[] args) {
        int /*|*/limit = 1;
        int bigger = Math.max(limit, 2);
        if (bigger != 2) {
            throw new AssertionError(bigger);
        }
    }
}
