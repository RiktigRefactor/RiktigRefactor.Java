public class Program {
    static int /*|*/limit = 3;

    public static void main(String[] args) throws Exception {
        String fieldName = "limit";
        if (limit != 3 || !fieldName.equals("limit")) {
            throw new AssertionError();
        }
    }
}
