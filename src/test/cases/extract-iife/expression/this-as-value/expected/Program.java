public class Program {
    final int id = 3;

    static int idOf(Program program) {
        return program.id;
    }

    int compute() {
        return ((java.util.function.ToIntFunction<Program>) (self) -> idOf(self) + self.id).applyAsInt(this);
    }

    public static void main(String[] args) {
        if (new Program().compute() != 6) {
            throw new AssertionError();
        }
    }
}
