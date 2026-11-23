public class Program {
    public static void main(String[] args) {
        int /*|*/value = 1;
        String text = "\u0041";
        if (value != 1 || !text.equals("A")) {
            throw new AssertionError();
        }
    }
}
