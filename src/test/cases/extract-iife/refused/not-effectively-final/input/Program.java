import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int count = 0;
        count++;
        int next = /*[*/count + 1/*]*/;
        if (next != 2) {
            throw new AssertionError(next);
        }
    }
}
