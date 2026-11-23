public class Program {
    static int sign(int x) {
        /*[*/if (x > 0) {
            return 1;
        }/*]*/
        return 0;
    }

    public static void main(String[] args) {
        if (sign(5) != 1 || sign(-5) != 0) {
            throw new AssertionError();
        }
    }
}
