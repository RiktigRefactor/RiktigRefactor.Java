import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        short a = 30000;
        short b = 2;
        interface Extracted { short apply(short aArg, short bArg); }
        String text = "" + ((Extracted) (aArg, bArg) -> (short) (aArg + bArg)).apply(a, b);
        if (!text.equals("30002")) {
            throw new AssertionError(text);
        }
    }
}
