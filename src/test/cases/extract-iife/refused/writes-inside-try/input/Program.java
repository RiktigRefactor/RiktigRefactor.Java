import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int attempts = 0;
        try {
            /*[*/attempts++;
            Integer.parseInt("x");/*]*/
        } catch (NumberFormatException e) {
            if (attempts != 1) {
                throw new AssertionError(attempts);
            }
            return;
        }
        throw new AssertionError("no exception");
    }
}
