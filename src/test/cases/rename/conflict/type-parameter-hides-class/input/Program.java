public class Program {
    static class U {
    }

    static </*|*/T> Object make(T ignored) {
        return new U();
    }

    public static void main(String[] args) {
        if (!(make(1) instanceof U)) {
            throw new AssertionError();
        }
    }
}
