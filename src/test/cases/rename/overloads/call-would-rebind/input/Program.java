public class Program {
    static String f(Object value) {
        return "object";
    }

    static String /*|*/g(String value) {
        return "string";
    }

    public static void main(String[] args) {
        if (!f("x").equals("object") || !g("x").equals("string")) {
            throw new AssertionError();
        }
    }
}
