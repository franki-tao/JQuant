package jquant.math.integrals;

import jquant.math.Function;
import jquant.utilities.Null;

public class DiscreteSimpsonIntegrator extends Integrator {
    public DiscreteSimpsonIntegrator(int evaluations) {
        super((double) Null.getValue(Float.class), evaluations);
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        int n = maxEvaluations() - 1;
        double d = (b - a) / n, d2 = d * 2;

        double sum = 0.0, x = a + d;
        for (int i = 1; i < n; i += 2) {//to time 4
            sum += f.value(x);
            x += d2;
        }
        sum *= 2;

        x = a + d2;
        for (int i = 2; i < n - 1; i += 2) {//to time 2
            sum += f.value(x);
            x += d2;
        }
        sum *= 2;

        sum += f.value(a);
        if ((n & 1) != 0)
            sum += 1.5 * f.value(b) + 2.5 * f.value(b - d);
        else
            sum += f.value(b);

        increaseNumberOfEvaluations(maxEvaluations());

        return d / 3 * sum;
    }
}
