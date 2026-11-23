public class Program {
    public static void main(String[] args) {
        if (Source.value() != 5) {
            throw new AssertionError();
        }
    }
}
