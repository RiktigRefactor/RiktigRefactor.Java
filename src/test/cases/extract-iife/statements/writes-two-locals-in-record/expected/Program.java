import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int a = 1;
        int b = 0;
        record ExtractedResult(int a, int b) {}
        interface Extracted { ExtractedResult apply(int aArg, int bArg); }
        var extractedResult = ((Extracted) (aArg, bArg) -> { aArg++;
        bArg = aArg * 2; return new ExtractedResult(aArg, bArg); }).apply(a, b);
        a = extractedResult.a();
        b = extractedResult.b();
        if (a != 2 || b != 4) {
            throw new AssertionError(a + " " + b);
        }
    }
}
