import java.util.function.IntUnaryOperator;

public class Program {
    static int /*|*/triple(int x) {
        return x * 3;
    }

    public static void main(String[] args) {
        IntUnaryOperator op = Program::triple;
        if (op.applyAsInt(2) != 6) {
            throw new AssertionError();
        }
    }
}
