public class Program {
    static String /*|*/describe(int value) {
        return "int";
    }

    static String describe(String value) {
        return "string";
    }

    public static void main(String[] args) {
        if (!describe(1).equals("int") || !describe("a").equals("string")) {
            throw new AssertionError();
        }
    }
}
