import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<String> out = new ArrayList<>();
        if (args.length == 0)
            /*[*/out.add("none");/*]*/
        if (!out.equals(List.of("none"))) {
            throw new AssertionError(out);
        }
    }
}
