public class Program {
    public static void main(String[] args) {
        int value = 1 /*|*/+ 1;
        if (value != 2) {
            throw new AssertionError();
        }
    }
}
