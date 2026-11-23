public class Program {
    static int parse(String text) throws Exception {
        if (text.isEmpty()) {
            throw new Exception("empty");
        }
        return text.length();
    }

    public static void main(String[] args) throws Exception {
        interface Extracted { int apply() throws java.lang.Exception; }
        int length = ((Extracted) () -> parse("abc")).apply();
        if (length != 3) {
            throw new AssertionError(length);
        }
    }
}
