package app;

import static lib.Numbers.value;

public class Program {
    public static void main(String[] args) {
        if (value() + value != 3) {
            throw new AssertionError();
        }
    }
}
