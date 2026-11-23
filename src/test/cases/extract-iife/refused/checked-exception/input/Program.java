public class Program {
    static int parse(String text) throws Exception {
        if (text.isEmpty()) {
            throw new Exception("empty");
        }
        return text.length();
    }

    public static void main(String[] args) throws Exception {
        int length = /*[*/parse("abc")/*]*/;
        if (length != 3) {
            throw new AssertionError(length);
        }
    }
}
