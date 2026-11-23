public class Program {
    static class Holder<E> {
        E value;

        Holder(E value) {
            this.value = value;
        }

        <U> U map(java.util.function.Function<E, U> f) {
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
