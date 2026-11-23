public class Program {
    interface Op {
        int run(int x);
    }

    static int helper(int x) {
        return x * 3;
    }

    public static void main(String[] args) {
        Op increment = x -> x + 1;
        Op triple = Program::helper;
        if (increment.run(1) != 2 || triple.run(2) != 6) {
            throw new AssertionError();
        }
    }
}
