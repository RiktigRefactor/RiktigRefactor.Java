import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int total = 1;
        total = ((java.util.function.IntUnaryOperator) (totalArg) -> { totalArg = totalArg + 1; return totalArg; }).applyAsInt(total);
        if (total != 2) {
            throw new AssertionError(total);
        }
    }
}
