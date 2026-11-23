package app;

import static lib.Util.label;

public class Program {
    static String /*|*/other() {
        return "member";
    }

    public static void main(String[] args) {
        if (!label().equals("imported") || !other().equals("member")) {
            throw new AssertionError();
        }
    }
}
