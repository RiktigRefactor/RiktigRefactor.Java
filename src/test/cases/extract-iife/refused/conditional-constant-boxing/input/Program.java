import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        Integer value = args.length == 0 ? /*[*/1/*]*/ : null;
        if (value != 1) {
            throw new AssertionError();
        }
    }
}
