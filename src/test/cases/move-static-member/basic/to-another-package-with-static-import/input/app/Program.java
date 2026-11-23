package app;

import static lib.Pricing.price;

public class Program {
    public static void main(String[] args) {
        if (price(2) != 8) {
            throw new AssertionError();
        }
    }
}
