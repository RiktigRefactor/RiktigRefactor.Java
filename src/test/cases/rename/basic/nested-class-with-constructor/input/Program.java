public class Program {
    static class /*|*/Box {
        final int value;

        Box(int value) {
            this.value = value;
        }

        Box doubled() {
            return new Box(value * 2);
        }
    }

    public static void main(String[] args) {
        Box box = new Box(4).doubled();
        Program.Box same = box;
        if (same.value != 8) {
            throw new AssertionError(same.value);
        }
    }
}
