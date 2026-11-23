package app;

import lib.Outer.Inner;

public class Program {
    public static void main(String[] args) {
        Inner inner = new Inner();
        if (inner.value() != 7) {
            throw new AssertionError();
        }
    }
}
