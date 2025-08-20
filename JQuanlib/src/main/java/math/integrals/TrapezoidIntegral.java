package math.integrals;

import math.Function;
import math.IntegrationPolicyImpl;

import static math.CommonUtil.QL_FAIL;

public class TrapezoidIntegral extends Integrator {
    public enum POLICY {Default, MidPoint}

    protected IntegrationPolicyImpl IntegrationPolicy;

    public TrapezoidIntegral(double accuracy,
                             int maxIterations, POLICY policy) {
        super(accuracy, maxIterations);
        initial(policy);
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        // start from the coarsest trapezoid...
        int N = 1;
        double I = (f.value(a) + f.value(b)) * (b - a) / 2.0, newI;
        increaseNumberOfEvaluations(2);
        // ...and refine it
        int i = 1;
        do {
            newI = IntegrationPolicy.integrate(f, a, b, I, N);
            increaseNumberOfEvaluations(N * (IntegrationPolicy.nbEvalutions() - 1));
            N *= IntegrationPolicy.nbEvalutions();
            // good enough? Also, don't run away immediately
            if (Math.abs(I - newI) <= absoluteAccuracy() && i > 5)
                // ok, exit
                return newI;
            // oh well. Another step.
            I = newI;
            i++;
        } while (i < maxEvaluations());
        QL_FAIL("max number of iterations reached");
        return 0;
    }

    private void initial(POLICY policy) {
        if (policy == POLICY.Default) {
            IntegrationPolicy = new IntegrationPolicyImpl() {
                @Override
                public double integrate(Function f, double a, double b, double I, int N) {
                    double sum = 0.0;
                    double dx = (b - a) / N;
                    double x = a + dx / 2.0;
                    for (int i = 0; i < N; x += dx, ++i)
                        sum += f.value(x);
                    return (I + dx * sum) / 2.0;
                }

                @Override
                public int nbEvalutions() {
                    return 2;
                }
            };
        } else if (policy == POLICY.MidPoint) {
            IntegrationPolicy = new IntegrationPolicyImpl() {
                @Override
                public double integrate(Function f, double a, double b, double I, int N) {
                    double sum = 0.0;
                    double dx = (b - a) / N;
                    double x = a + dx / 6.0;
                    double D = 2.0 * dx / 3.0;
                    for (int i = 0; i < N; x += dx, ++i)
                        sum += f.value(x) + f.value(x + D);
                    return (I + dx * sum) / 3.0;
                }

                @Override
                public int nbEvalutions() {
                    return 3;
                }
            };
        } else {
            QL_FAIL("error policy!");
        }
    }
}

