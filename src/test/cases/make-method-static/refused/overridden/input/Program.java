public class Program {
    static class Shape {
        String /*|*/name() {
            return "shape";
        }
    }

    static class Circle extends Shape {
        @Override
        String name() {
            return "circle";
        }
    }

    public static void main(String[] args) {
        Shape shape = new Circle();
        if (!shape.name().equals("circle")) {
            throw new AssertionError();
        }
    }
}
