public class Program {
    static String take(long value) {
        return "long";
    }

    static String /*|*/other(Integer value) {
        return "Integer";
    }

    public static void main(String[] args) {
        if (!other(1).equals("Integer") || !take(1).equals("long")) {
            throw new AssertionError();
        }
    }
}
