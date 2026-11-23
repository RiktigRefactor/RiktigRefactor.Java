import java.util.*;

public class Program {
    static class Holder {
        int base = 2;
        int value = /*[*/base * 3/*]*/;
    }

    public static void main(String[] args) throws Exception {
        if (new Holder().value != 6) {
            throw new AssertionError();
        }
    }
}
