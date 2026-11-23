package app;

import lib.Outer.Nested;

public class Program {
    public static void main(String[] args) {
        Nested inner = new Nested();
        if (inner.value() != 7) {
            throw new AssertionError();
        }
    }
}
