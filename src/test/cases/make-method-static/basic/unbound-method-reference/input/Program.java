import java.util.function.ToIntFunction;

public class Program {
    static class Item {
        int size = 4;

        int /*|*/doubled() {
            return size * 2;
        }
    }

    public static void main(String[] args) {
        ToIntFunction<Item> f = Item::doubled;
        if (f.applyAsInt(new Item()) != 8) {
            throw new AssertionError();
        }
    }
}
