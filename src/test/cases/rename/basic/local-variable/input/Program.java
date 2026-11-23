public class Program {
    public static void main(String[] args) {
        int /*|*/total = 2;
        total = total * 3; // total stays in comments
        if (total != 6) {
            throw new AssertionError(total);
        }
    }
}
