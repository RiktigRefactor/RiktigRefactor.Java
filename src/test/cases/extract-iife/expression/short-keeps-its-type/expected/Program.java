import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        short a = 30000;
        short b = 2;
        String text = "" + ((short) ((java.util.function.Supplier<java.lang.Short>) () -> (short) (a + b)).get());
        if (!text.equals("30002")) {
            throw new AssertionError(text);
        }
    }
}
