public class Program {
    static class A {
        String label() {
            return "A";
        }
    }

    static class B extends A {
        @Override
        String label() {
            return "B";
        }
    }

    public static void main(String[] args) {
        A a = new B();
        if (!a.label().equals("B")) {
            throw new AssertionError();
        }
    }
}
