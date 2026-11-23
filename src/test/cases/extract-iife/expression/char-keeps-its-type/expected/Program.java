import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        char letter = 'a';
        String text = "" + ((char) ((java.util.function.Supplier<java.lang.Character>) () -> Character.toUpperCase(letter)).get());
        if (!text.equals("A")) {
            throw new AssertionError(text);
        }
    }
}
