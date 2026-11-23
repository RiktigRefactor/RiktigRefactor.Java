public class Program {
    interface Box<T> {
        T /*|*/get();
    }

    static class IntBox implements Box<Integer> {
        @Override
        public Integer get() {
            return 42;
        }
    }

    public static void main(String[] args) {
        Box<Integer> box = new IntBox();
        if (box.get() != 42 || new IntBox().get() != 42) {
            throw new AssertionError();
        }
    }
}
