import java.util.*;

public class Program {
    static class Holder {
        int base = 2;
        int value = ((java.util.function.IntSupplier) () -> base * 3).getAsInt();
    }

    public static void main(String[] args) throws Exception {
        if (new Holder().value != 6) {
            throw new AssertionError();
        }
    }
}
