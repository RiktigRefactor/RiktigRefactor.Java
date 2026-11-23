class Source {
    private static int secret() {
        return 5;
    }

    static int /*|*/value() {
        return secret();
    }
}
