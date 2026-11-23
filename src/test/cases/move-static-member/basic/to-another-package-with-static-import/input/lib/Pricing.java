package lib;

import java.util.List;

public class Pricing {
    public static final int RATE = 4;

    public static int /*|*/price(int qty) {
        List<Integer> values = List.of(qty, RATE);
        return values.get(0) * values.get(1);
    }
}
