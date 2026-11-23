public class Program {
    static class Box<T> {
        T value;

        Box(T value) {
            this.value = value;
        }

        T /*|*/get() {
            return value;
        }
    }

    public static void main(String[] args) {
        Box<String> box = new Box<>("x");
        String text = box.get();
        if (!text.equals("x")) {
            throw new AssertionError();
        }
    }
}
