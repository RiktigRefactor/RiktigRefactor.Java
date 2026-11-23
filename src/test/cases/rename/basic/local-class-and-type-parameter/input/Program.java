public class Program {
    static class Holder</*|*/T> {
        T value;

        Holder(T value) {
            this.value = value;
        }

        <U> U map(java.util.function.Function<T, U> f) {
            return f.apply(value);
        }
    }

    public static void main(String[] args) {
        class Doubler {
            int apply(int x) {
                return x * 2;
            }
        }
        Holder<Integer> holder = new Holder<>(4);
        int result = holder.map(x -> new Doubler().apply(x));
        if (result != 8) {
            throw new AssertionError(result);
        }
    }
}
