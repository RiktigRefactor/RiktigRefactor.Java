import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        char letter = 'a';
        String text = "" + /*[*/Character.toUpperCase(letter)/*]*/;
        if (!text.equals("A")) {
            throw new AssertionError(text);
        }
    }
}
