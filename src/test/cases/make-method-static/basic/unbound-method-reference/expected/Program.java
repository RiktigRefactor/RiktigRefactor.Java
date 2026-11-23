import java.util.function.ToIntFunction;

public class Program {
    static class Item {
        int size = 4;

        static int doubled(Program.Item item) {
            return item.size * 2;
        }
    }

    public static void main(String[] args) {
        ToIntFunction<Item> f = Item::doubled;
        if (f.applyAsInt(new Item()) != 8) {
            throw new AssertionError();
        }
    }
}
