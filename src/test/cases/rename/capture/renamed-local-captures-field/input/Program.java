public class Program {
    static int count = 10;

    public static void main(String[] args) {
        int /*|*/a = 1;
        if (a + count != 11) {
            throw new AssertionError();
        }
    }
}
