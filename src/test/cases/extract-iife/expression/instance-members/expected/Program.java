public class Program {
    private int count = 2;

    int twice() {
        return count * 2;
    }

    int compute() {
        return ((java.util.function.IntSupplier) () -> this.twice() + count).getAsInt();
    }

    public static void main(String[] args) {
        int result = new Program().compute();
        if (result != 6) {
            throw new AssertionError(result);
        }
    }
}
