public class Program {
    static class Box<T> {
        T value;

        Box(T value) {
            this.value = value;
        }

        static <T> T get(Program.Box<T> box) {
            return box.value;
        }
    }

    public static void main(String[] args) {
        Box<String> box = new Box<>("x");
        String text = Program.Box.get(box);
        if (!text.equals("x")) {
            throw new AssertionError();
        }
    }
}
