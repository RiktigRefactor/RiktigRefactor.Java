public class Program {
    abstract static class Base {
        int size() {
            return 0;
        }

        abstract int test();
    }

    static int /*|*/count() {
        return 5;
    }

    public static void main(String[] args) {
        Base base = new Base() {
            @Override
            int test() {
                return count();
            }
        };
        if (base.test() != 5) {
            throw new AssertionError();
        }
    }
}
