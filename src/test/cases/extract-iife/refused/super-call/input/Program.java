public class Program {
    static class Base {
        String name() {
            return "base";
        }
    }

    static class Derived extends Base {
        @Override
        String name() {
            return "derived";
        }

        String both() {
            return /*[*/super.name() + name()/*]*/;
        }
    }

    public static void main(String[] args) {
        if (!new Derived().both().equals("basederived")) {
            throw new AssertionError();
        }
    }
}
