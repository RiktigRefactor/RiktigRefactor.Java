public class Program {
    enum Color { CRIMSON, GREEN }

    static int code(Color color) {
        switch (color) {
            case CRIMSON:
                return 1;
            default:
                return 2;
        }
    }

    public static void main(String[] args) {
        if (code(Color.CRIMSON) != 1 || code(Color.GREEN) != 2) {
            throw new AssertionError();
        }
    }
}
