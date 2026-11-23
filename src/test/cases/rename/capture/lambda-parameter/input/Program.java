import java.util.function.IntUnaryOperator;

public class Program {
    static int /*|*/base = 10;

    public static void main(String[] args) {
        IntUnaryOperator add = v -> v + base;
        if (add.applyAsInt(1) != 11) {
            throw new AssertionError();
        }
    }
}
