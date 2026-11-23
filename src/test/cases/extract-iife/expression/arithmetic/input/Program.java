import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int price = 3;
        int qty = 4;
        int total = /*[*/price * qty/*]*/ + 1;
        if (total != 13) {
            throw new AssertionError(total);
        }
    }
}
