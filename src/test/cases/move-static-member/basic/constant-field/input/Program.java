public class Program {
    static class Config {
        static final String /*|*/NAME = "shop";
    }

    static class Settings {
    }

    public static void main(String[] args) {
        if (!Config.NAME.equals("shop")) {
            throw new AssertionError();
        }
    }
}
