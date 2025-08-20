package math;

import math.distributions.GammaFunction;

public class Factorial {
    public Factorial() {
    }

    public static final double[] firstFactorials = {
            1.0, 1.0, 2.0, 6.0, 24.0, 120.0, 720.0, 5040.0, 40320.0, 362880.0,
            3628800.0, 39916800.0, 479001600.0, 6227020800.0, 87178291200.0,
            1307674368000.0, 20922789888000.0, 355687428096000.0, 6402373705728000.0,
            121645100408832000.0, 2432902008176640000.0
    };

    public static final int tabulated = firstFactorials.length - 1;

    public static double get(int i) {
        if (i<=tabulated) {
            return firstFactorials[i];
        } else {
            return Math.exp(new GammaFunction().logValue(i+1));
        }
    }

    public static double ln(int i) {
        if (i<=tabulated) {
            return Math.log(firstFactorials[i]);
        } else {
            return new GammaFunction().logValue(i+1);
        }
    }
}
