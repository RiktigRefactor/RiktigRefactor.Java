public class Program {
    int base = 10;

    class Inner {
        int plus(int x) {
            return /*[*/base + x/*]*/;
        }
    }

    public static void main(String[] args) {
        if (new Program().new Inner().plus(1) != 11) {
            throw new AssertionError();
        }
    }
}
