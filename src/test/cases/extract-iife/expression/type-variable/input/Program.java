import java.util.*;

public class Program {
    static <T> T identity(T value) {
        return /*[*/value/*]*/;
    }

    public static void main(String[] args) throws Exception {
        if (!identity("x").equals("x")) {
            throw new AssertionError();
        }
    }
}
