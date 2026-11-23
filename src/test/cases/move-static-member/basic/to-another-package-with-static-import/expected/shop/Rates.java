package shop;

import java.util.List;

public class Rates {
    public static int price(int qty) {
        List<Integer> values = List.of(qty, lib.Pricing.RATE);
        return values.get(0) * values.get(1);
    }
}
