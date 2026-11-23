import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int[] values = new int[1];
        /*[*/values[0]/*]*/ = 5;
        if (values[0] != 5) {
            throw new AssertionError();
        }
    }
}
