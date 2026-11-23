import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        char letter = 'a';
        interface Extracted { char apply(char letterArg); }
        String text = "" + ((Extracted) (letterArg) -> Character.toUpperCase(letterArg)).apply(letter);
        if (!text.equals("A")) {
            throw new AssertionError(text);
        }
    }
}
