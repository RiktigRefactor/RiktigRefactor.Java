package app;

import static lib.Numbers.doubled;

public class Program {
    public static void main(String[] args) {
        if (doubled(4) != 8) {
            throw new AssertionError();
        }
    }
}
