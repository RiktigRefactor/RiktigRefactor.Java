public class Program {
    final int id = 3;

    static int idOf(Program program) {
        return program.id;
    }

    int compute() {
        return /*[*/idOf(this) + id/*]*/;
    }

    public static void main(String[] args) {
        if (new Program().compute() != 6) {
            throw new AssertionError();
        }
    }
}
