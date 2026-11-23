import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        char letter = 'x';
        String text = "" + (args.length == 0 ? letter : /*[*/0/*]*/);
        if (!text.equals("x")) {
            throw new AssertionError(text);
        }
    }
}
