import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        /*[*/int a = 1;/*]*/
        int b = a + 1;
        if (b != 2) {
            throw new AssertionError(b);
        }
    }
}
