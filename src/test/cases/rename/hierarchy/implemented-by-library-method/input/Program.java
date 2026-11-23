import java.util.ArrayList;

public class Program {
    interface Sized {
        int /*|*/size();
    }

    abstract static class Names extends ArrayList<String> implements Sized {
    }

    public static void main(String[] args) {
        Sized sized = new Names() {
        };
        if (sized.size() != 0) {
            throw new AssertionError();
        }
    }
}
