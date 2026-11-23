import java.util.*;

public class Program {
    static class Holder {
        int base = 2;
        int value = ((java.util.function.ToIntFunction<Program.Holder>) (self) -> self.base * 3).applyAsInt(this);
    }

    public static void main(String[] args) throws Exception {
        if (new Holder().value != 6) {
            throw new AssertionError();
        }
    }
}
