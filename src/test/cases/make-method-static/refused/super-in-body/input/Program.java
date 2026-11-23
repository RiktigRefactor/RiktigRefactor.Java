public class Program {
    static class Base {
        String name() {
            return "base";
        }
    }

    static class Derived extends Base {
        String /*|*/label() {
            return super.name() + "!";
        }
    }

    public static void main(String[] args) {
        if (!new Derived().label().equals("base!")) {
            throw new AssertionError();
        }
    }
}
