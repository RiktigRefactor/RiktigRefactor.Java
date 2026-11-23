import java.util.function.IntUnaryOperator;

public class Program {
    public static void main(String[] args) {
        int offset = 1;
        IntUnaryOperator add = /*|*/v -> v + offset;
        if (add.applyAsInt(1) != 2) {
            throw new AssertionError();
        }
    }
}
