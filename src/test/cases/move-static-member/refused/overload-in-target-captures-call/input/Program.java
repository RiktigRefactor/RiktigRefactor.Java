public class Program {
    static class Source {
        static String /*|*/describe(int value) {
            return "moved";
        }
    }

    static class Target {
        static String describe(long value) {
            return "existing";
        }
    }

    public static void main(String[] args) {
        if (!Target.describe(1).equals("existing") || !Source.describe(1).equals("moved")) {
            throw new AssertionError();
        }
    }
}
