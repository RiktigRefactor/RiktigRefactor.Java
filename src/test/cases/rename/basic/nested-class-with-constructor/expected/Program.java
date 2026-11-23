public class Program {
    static class Crate {
        final int value;

        Crate(int value) {
            this.value = value;
        }

        Crate doubled() {
            return new Crate(value * 2);
        }
    }

    public static void main(String[] args) {
        Crate box = new Crate(4).doubled();
        Program.Crate same = box;
        if (same.value != 8) {
            throw new AssertionError(same.value);
        }
    }
}
