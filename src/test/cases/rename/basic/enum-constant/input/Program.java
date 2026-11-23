public class Program {
    enum Color { /*|*/RED, GREEN }

    static int code(Color color) {
        switch (color) {
            case RED:
                return 1;
            default:
                return 2;
        }
    }

    public static void main(String[] args) {
        if (code(Color.RED) != 1 || code(Color.GREEN) != 2) {
            throw new AssertionError();
        }
    }
}
