import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int total = 1;
        /*[*/total = total + 1;/*]*/
        if (total != 2) {
            throw new AssertionError(total);
        }
    }
}
