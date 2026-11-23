public class Program {
    interface Box<T> {
        T value();
    }

    static class IntBox implements Box<Integer> {
        @Override
        public Integer value() {
            return 42;
        }
    }

    public static void main(String[] args) {
        Box<Integer> box = new IntBox();
        if (box.value() != 42 || new IntBox().value() != 42) {
            throw new AssertionError();
        }
    }
}
