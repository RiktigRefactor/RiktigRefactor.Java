public class Program {
    static class Source {
        int /*|*/value() {
            return 1;
        }
    }

    static class Target {
    }

    public static void main(String[] args) {
        if (new Source().value() != 1) {
            throw new AssertionError();
        }
    }
}
