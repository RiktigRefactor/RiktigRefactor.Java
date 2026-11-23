import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        String name = "Ada";
        String greeting = ((java.util.function.Supplier<java.lang.String>) () -> "Hello, " + name.toUpperCase()).get();
        if (!greeting.equals("Hello, ADA")) {
            throw new AssertionError(greeting);
        }
    }
}
