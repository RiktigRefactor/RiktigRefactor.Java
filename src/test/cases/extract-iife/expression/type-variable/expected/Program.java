import java.util.*;

public class Program {
    static <T> T identity(T value) {
        return ((java.util.function.Supplier<T>) () -> value).get();
    }

    public static void main(String[] args) throws Exception {
        if (!identity("x").equals("x")) {
            throw new AssertionError();
        }
    }
}
