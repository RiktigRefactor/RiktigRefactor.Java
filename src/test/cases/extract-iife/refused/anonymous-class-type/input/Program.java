import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        var counter = new Object() {
            int value = 7;
        };
        int value = /*[*/counter/*]*/.value;
        if (value != 7) {
            throw new AssertionError(value);
        }
    }
}
