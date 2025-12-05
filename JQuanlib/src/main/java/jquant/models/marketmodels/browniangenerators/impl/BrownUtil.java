package jquant.models.marketmodels.browniangenerators.impl;

import java.util.List;

public class BrownUtil {
    public static void fillByFactor(List<List<Integer>> M,
                                    int factors, int steps) {
        int counter = 0;
        for (int i = 0; i < factors; ++i)
            for (int j = 0; j < steps; ++j)
                M.get(i).set(j, counter++);
    }

    public static void fillByStep(List<List<Integer>> M,
                                  int factors, int steps) {
        int counter = 0;
        for (int j = 0; j < steps; ++j)
            for (int i = 0; i < factors; ++i)
                M.get(i).set(j, counter++);
    }

    // variate 2 is used for the second factor's full path
    public static void fillByDiagonal(List<List<Integer>> M,
                                      int factors, int steps) {
        // starting position of the current diagonal
        int i0 = 0, j0 = 0;
        // current position
        int i = 0, j = 0;
        int counter = 0;
        while (counter < factors * steps) {
            M.get(i).set(j, counter++);
            if (i == 0 || j == steps - 1) {
                // we completed a diagonal and have to start a new one
                if (i0 < factors - 1) {
                    // we start the path of the next factor
                    i0 = i0 + 1;
                    j0 = 0;
                } else {
                    // we move along the path of the last factor
                    i0 = factors - 1;
                    j0 = j0 + 1;
                }
                i = i0;
                j = j0;
            } else {
                // we move along the diagonal
                i = i - 1;
                j = j + 1;
            }
        }
    }
}
