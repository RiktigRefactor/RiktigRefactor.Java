import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        String name = "Ada";
        String greeting = /*[*/"Hello, " + name.toUpperCase()/*]*/;
        if (!greeting.equals("Hello, ADA")) {
            throw new AssertionError(greeting);
        }
    }
}
