import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        String name = "Ada";
        String greeting = ((java.util.function.Function<java.lang.String, java.lang.String>) (nameArg) -> "Hello, " + nameArg.toUpperCase()).apply(name);
        if (!greeting.equals("Hello, ADA")) {
            throw new AssertionError(greeting);
        }
    }
}
