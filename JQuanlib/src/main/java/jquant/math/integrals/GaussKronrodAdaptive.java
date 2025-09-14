package jquant.math.integrals;

import jquant.math.Function;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_SIZE;

public class GaussKronrodAdaptive extends Integrator {

    private final double[] g7w = {0.417959183673469,
            0.381830050505119,
            0.279705391489277,
            0.129484966168870};

    private final double[] k15w = {0.209482141084728,
            0.204432940075298,
            0.190350578064785,
            0.169004726639267,
            0.140653259715525,
            0.104790010322250,
            0.063092092629979,
            0.022935322010529};
    private final double[] k15t = {0.000000000000000,
            0.207784955007898,
            0.405845151377397,
            0.586087235467691,
            0.741531185599394,
            0.864864423359769,
            0.949107912342758,
            0.991455371120813};

    public GaussKronrodAdaptive(double tolerance,
                                int maxFunctionEvaluations) {
        super(tolerance, maxFunctionEvaluations);
        QL_REQUIRE(maxFunctionEvaluations >= 15,
                "required maxEvaluations (" + maxFunctionEvaluations +
                        ") not allowed. It must be >= 15");
    }

    public GaussKronrodAdaptive(double tolerance) {
        this(tolerance, NULL_SIZE);
    }

    private double integrateRecursively(Function f, double a, double b, double tolerance) {
        double halflength = (b - a) / 2;
        double center = (a + b) / 2;

        double g7; // will be result of G7 integral
        double k15; // will be result of K15 integral

        double t, fsum; // t (abscissa) and f(t)
        double fc = f.value(center);
        g7 = fc * g7w[0];
        k15 = fc * k15w[0];

        // calculate g7 and half of k15
        Integer j, j2;
        for (j = 1, j2 = 2; j < 4; j++, j2 += 2) {
            t = halflength * k15t[j2];
            fsum = f.value(center - t) + f.value(center + t);
            g7 += fsum * g7w[j];
            k15 += fsum * k15w[j2];
        }

        // calculate other half of k15
        for (j2 = 1; j2 < 8; j2 += 2) {
            t = halflength * k15t[j2];
            fsum = f.value(center - t) + f.value(center + t);
            k15 += fsum * k15w[j2];
        }

        // multiply by (a - b) / 2
        g7 = halflength * g7;
        k15 = halflength * k15;

        // 15 more function evaluations have been used
        increaseNumberOfEvaluations(15);

        // error is <= k15 - g7
        // if error is larger than tolerance then split the interval
        // in two and integrate recursively
        if (Math.abs(k15 - g7) < tolerance) {
            return k15;
        } else {
            QL_REQUIRE(numberOfEvaluations() + 30 <=
                            maxEvaluations(),
                    "maximum number of function evaluations " +
                            "exceeded");
            return integrateRecursively(f, a, center, tolerance / 2)
                    + integrateRecursively(f, center, b, tolerance / 2);
        }
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        return integrateRecursively(f, a, b, absoluteAccuracy());
    }
}
