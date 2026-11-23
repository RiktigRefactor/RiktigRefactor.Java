public class Program {
    static class A {
        String /*|*/name() {
            return "A";
        }
    }

    static class B extends A {
        @Override
        String name() {
            return "B";
        }
    }

    public static void main(String[] args) {
        A a = new B();
        if (!a.name().equals("B")) {
            throw new AssertionError();
        }
    }
}
