public class Program {
    static class Config {
    }

    static class Settings {
        static final String NAME = "shop";
    }

    public static void main(String[] args) {
        if (!Program.Settings.NAME.equals("shop")) {
            throw new AssertionError();
        }
    }
}
