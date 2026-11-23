import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int a = 1;
        int b = 0;
        /*[*/a++;
        b = a * 2;/*]*/
        if (a != 2 || b != 4) {
            throw new AssertionError(a + " " + b);
        }
    }
}
