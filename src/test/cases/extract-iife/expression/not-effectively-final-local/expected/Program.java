import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int count = 0;
        count++;
        int next = ((java.util.function.IntUnaryOperator) (countArg) -> countArg + 1).applyAsInt(count);
        if (next != 2) {
            throw new AssertionError(next);
        }
    }
}
