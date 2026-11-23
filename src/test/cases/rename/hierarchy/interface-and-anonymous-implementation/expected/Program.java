public class Program {
    interface Shape {
        int vertices();
    }

    static class Square implements Shape {
        @Override
        public int vertices() {
            return 4;
        }
    }

    public static void main(String[] args) {
        Shape triangle = new Shape() {
            public int vertices() {
                return 3;
            }
        };
        Shape square = new Square();
        if (triangle.vertices() + square.vertices() != 7) {
            throw new AssertionError();
        }
    }
}
