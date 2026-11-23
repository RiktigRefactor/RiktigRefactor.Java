import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int price = 3;
        int qty = 4;
        int total = ((java.util.function.IntSupplier) () -> price * qty).getAsInt() + 1;
        if (total != 13) {
            throw new AssertionError(total);
        }
    }
}
