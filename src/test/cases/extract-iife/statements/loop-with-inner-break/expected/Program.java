import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        List<Integer> seen = new ArrayList<>();
        ((java.lang.Runnable) () -> { for (int i = 0; i < 10; i++) {
            if (i == 3) {
                break;
            }
            seen.add(i);
        } }).run();
        if (!seen.equals(List.of(0, 1, 2))) {
            throw new AssertionError(seen);
        }
    }
}
