import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        Runnable action = /*[*/() -> { }/*]*/;
        action.run();
        if (action == null) {
            throw new AssertionError();
        }
    }
}
