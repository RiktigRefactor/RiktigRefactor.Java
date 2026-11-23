public class Program {
    static int x = 1;

    public static void main(String[] args) {
        int y = 2;
        if (/*|*/x + y != 3) {
            throw new AssertionError();
        }
    }
}
