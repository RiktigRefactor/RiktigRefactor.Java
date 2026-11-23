package app;

import lib.Greeter;

public class Program {
    public static void main(String[] args) {
        String text = Greeter.welcome("Ada");
        if (!text.equals("Hello, Ada")) {
            throw new AssertionError(text);
        }
    }
}
