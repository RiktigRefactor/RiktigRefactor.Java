import java.util.*;

public class Program {
    static final List<String> trace = new ArrayList<>();
    static int counter = 0;

    public static void main(String[] args) throws Exception {
        /*[*/trace.add("first");
        counter += 2;
        trace.add("second");/*]*/
        trace.add("third");
        if (counter != 2 || !trace.equals(List.of("first", "second", "third"))) {
            throw new AssertionError(trace);
        }
    }
}
