package math.integrals;

import math.Function;
import utilities.Null;


public class DiscreteTrapezoidIntegrator extends Integrator {
    public DiscreteTrapezoidIntegrator(int evaluations) {
        super((double) Null.getValue(Float.class), evaluations);
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        int n = maxEvaluations() - 1;
        double d = (b - a) / n;

        double sum = f.value(a) * 0.5;

        for (int i = 0; i < n - 1; ++i) {
            a += d;
            sum += f.value(a);
        }
        sum += f.value(b) * 0.5;

        increaseNumberOfEvaluations(maxEvaluations());

        return d * sum;
    }
}
