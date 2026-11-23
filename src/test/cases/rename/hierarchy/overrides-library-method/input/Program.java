public class Program {
    static class Point {
        @Override
        public String /*|*/toString() {
            return "point";
        }
    }

    public static void main(String[] args) {
        if (!("" + new Point()).equals("point")) {
            throw new AssertionError();
        }
    }
}
