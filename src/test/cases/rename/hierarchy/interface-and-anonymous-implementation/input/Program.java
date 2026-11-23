public class Program {
    interface Shape {
        int corners();
    }

    static class Square implements Shape {
        @Override
        public int /*|*/corners() {
            return 4;
        }
    }

    public static void main(String[] args) {
        Shape triangle = new Shape() {
            public int corners() {
                return 3;
            }
        };
        Shape square = new Square();
        if (triangle.corners() + square.corners() != 7) {
            throw new AssertionError();
        }
    }
}
