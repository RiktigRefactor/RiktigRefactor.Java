import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        /*[*/class Point {
            int x = 3;
        }/*]*/
        if (new Point().x != 3) {
            throw new AssertionError();
        }
    }
}
