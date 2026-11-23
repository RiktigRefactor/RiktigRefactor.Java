public class Program {
    static int maximum = 3;

    public static void main(String[] args) throws Exception {
        String fieldName = "limit";
        if (maximum != 3 || !fieldName.equals("limit")) {
            throw new AssertionError();
        }
    }
}
