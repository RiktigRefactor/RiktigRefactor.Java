package app;

import static lib.Numbers.twice;

public class Program {
    public static void main(String[] args) {
        if (twice(4) != 8) {
            throw new AssertionError();
        }
    }
}
