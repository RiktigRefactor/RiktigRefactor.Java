public class Program {
    int /*|*/outer = 5;

    class Inner {
        int inner = 1;

        int sum() {
            return outer + inner;
        }
    }

    public static void main(String[] args) {
        if (new Program().new Inner().sum() != 6) {
            throw new AssertionError();
        }
    }
}
