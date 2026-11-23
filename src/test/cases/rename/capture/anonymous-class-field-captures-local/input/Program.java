public class Program {
    interface Source {
        int get();
    }

    public static void main(String[] args) {
        int /*|*/x = 5;
        Source source = new Source() {
            int y = 1;

            public int get() {
                return x + y;
            }
        };
        if (source.get() != 6) {
            throw new AssertionError();
        }
    }
}
