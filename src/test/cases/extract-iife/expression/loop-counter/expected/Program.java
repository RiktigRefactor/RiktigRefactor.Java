import java.util.*;

public class Program {
    public static void main(String[] args) throws Exception {
        int sum = 0;
        int[] values = {1, 2, 3};
        int[] squares = new int[3];
        for (int i = 0; i < 3; i++) {
            interface Extracted { int apply(int[] valuesArg, int iArg); }
            squares[i] = ((Extracted) (valuesArg, iArg) -> valuesArg[iArg] * valuesArg[iArg]).apply(values, i);
        }
        if (squares[2] != 9) {
            throw new AssertionError();
        }
    }
}
