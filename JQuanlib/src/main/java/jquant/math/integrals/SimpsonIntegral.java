package jquant.math.integrals;

import jquant.math.Function;

import static jquant.math.CommonUtil.QL_FAIL;

public class SimpsonIntegral extends TrapezoidIntegral {
    public SimpsonIntegral(double accuracy, int maxIterations) {
        super(accuracy, maxIterations, POLICY.Default);
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        // start from the coarsest trapezoid...
        int N = 1;
        double I = (f.value(a) + f.value(b)) * (b - a) / 2.0, newI;
        increaseNumberOfEvaluations(2);

        double adjI = I, newAdjI;
        // ...and refine it
        int i = 1;
        do {
            newI = super.IntegrationPolicy.integrate(f, a, b, I, N);
            increaseNumberOfEvaluations(N);
            N *= 2;
            newAdjI = (4.0 * newI - I) / 3.0;
            // good enough? Also, don't run away immediately
            if (Math.abs(adjI - newAdjI) <= absoluteAccuracy() && i > 5)
                // ok, exit
                return newAdjI;
            // oh well. Another step.
            I = newI;
            adjI = newAdjI;
            i++;
        } while (i < maxEvaluations());
        QL_FAIL("max number of iterations reached");
        return 0;
    }
}
