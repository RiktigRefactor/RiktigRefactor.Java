import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (int i = 0; i < 3; i++) {
            /*[*/if (i == 1) {
                continue;
            }/*]*/
            count++;
        }
        if (count != 2) {
            throw new AssertionError(count);
        }
    }
}
