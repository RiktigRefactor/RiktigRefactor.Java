public class Program {
    static String describeNumber(int value) {
        return "int";
    }

    static String describe(String value) {
        return "string";
    }

    public static void main(String[] args) {
        if (!describeNumber(1).equals("int") || !describe("a").equals("string")) {
            throw new AssertionError();
        }
    }
}
