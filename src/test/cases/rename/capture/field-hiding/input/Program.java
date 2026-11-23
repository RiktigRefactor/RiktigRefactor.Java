public class Program {
    static class A {
        int v = 1;
    }

    static class B extends A {
        int /*|*/w = 2;

        int get() {
            return v;
        }
    }

    public static void main(String[] args) {
        if (new B().get() != 1) {
            throw new AssertionError();
        }
    }
}
