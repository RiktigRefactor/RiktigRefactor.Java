public class Program {
    static class A {
        String /*|*/hello() {
            return "A";
        }
    }

    static class B extends A {
        String greet() {
            return "B";
        }
    }

    public static void main(String[] args) {
        A a = new B();
        if (!a.hello().equals("A")) {
            throw new AssertionError();
        }
    }
}
