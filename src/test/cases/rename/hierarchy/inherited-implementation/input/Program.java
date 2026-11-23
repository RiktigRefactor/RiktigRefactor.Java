public class Program {
    static class A {
        public int m() {
            return 1;
        }
    }

    interface I {
        int /*|*/m();
    }

    static class C extends A implements I {
    }

    public static void main(String[] args) {
        I i = new C();
        if (i.m() != 1) {
            throw new AssertionError();
        }
    }
}
