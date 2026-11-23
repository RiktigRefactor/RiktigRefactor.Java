public class Program {
    private int count = 2;

    int twice() {
        return count * 2;
    }

    int compute() {
        return /*[*/this.twice() + count/*]*/;
    }

    public static void main(String[] args) {
        int result = new Program().compute();
        if (result != 6) {
            throw new AssertionError(result);
        }
    }
}
