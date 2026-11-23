import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int a = 1;
        int b = 2;
        int c = a + /*[*/b * 3 + a/*]*/;
        if (c != 8) {
            throw new AssertionError(c);
        }
    }
}
