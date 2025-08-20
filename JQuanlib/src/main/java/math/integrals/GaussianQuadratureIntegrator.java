package math.integrals;

import math.Function;

import static math.MathUtils.NULL_REAL;


//todo 不清楚template <class Integration>具体哪些类型，暂停使用
public class GaussianQuadratureIntegrator extends Integrator {
    //todo 可能存在隐患
    private Integration integration_;

    private int n;

    public GaussianQuadratureIntegrator(int n, Integrator integration_) {
        super(NULL_REAL, n);
        this.n = n;
    }

    public Integration getIntegration() {
        return integration_;
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        final double c1 = 0.5 * (b - a);
        final double c2 = 0.5 * (a + b);
        this.integration_ = new Integration(this.n) {
            @Override
            protected void setF() {
                super.f = new Function() {
                    @Override
                    public double value(double x) {
                        return f.value(c1*x + c2);
                    }
                };
            }
        };

        return this.integration_.value();
    }
}
