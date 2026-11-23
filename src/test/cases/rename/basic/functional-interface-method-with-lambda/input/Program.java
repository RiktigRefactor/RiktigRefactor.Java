public class Program {
    interface Op {
        int /*|*/apply(int x);
    }

    static int helper(int x) {
        return x * 3;
    }

    public static void main(String[] args) {
        Op increment = x -> x + 1;
        Op triple = Program::helper;
        if (increment.apply(1) != 2 || triple.apply(2) != 6) {
            throw new AssertionError();
        }
    }
}
