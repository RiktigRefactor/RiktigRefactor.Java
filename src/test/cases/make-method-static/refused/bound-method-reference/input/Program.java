import java.util.function.IntSupplier;

public class Program {
    static class Counter {
        int count = 2;

        int /*|*/next() {
            return ++count;
        }
    }

    public static void main(String[] args) {
        Counter counter = new Counter();
        IntSupplier next = counter::next;
        if (next.getAsInt() != 3) {
            throw new AssertionError();
        }
    }
}
