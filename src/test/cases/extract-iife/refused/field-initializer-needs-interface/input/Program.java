public class Program {
    static char letter = 'a';
    static String text = "" + /*[*/Character.toUpperCase(letter)/*]*/;

    public static void main(String[] args) {
        if (!text.equals("A")) {
            throw new AssertionError(text);
        }
    }
}
