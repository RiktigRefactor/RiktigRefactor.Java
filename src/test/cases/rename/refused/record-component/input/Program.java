public class Program {
    record Point(int /*|*/x, int y) {
    }

    public static void main(String[] args) {
        if (!new Point(1, 2).toString().equals("Point[x=1, y=2]")) {
            throw new AssertionError();
        }
    }
}
