public class Program {
    static class A {
        public int n() {
            return 1;
        }
    }

    interface I {
        int n();
    }

    static class C extends A implements I {
    }

    public static void main(String[] args) {
        I i = new C();
        if (i.n() != 1) {
            throw new AssertionError();
        }
    }
}
