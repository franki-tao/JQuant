package math.integrals;

import math.Function;
import math.tanh_sinh;

import static math.MathUtils.NULL_SIZE;
import static math.MathUtils.QL_MAX_REAL;

public class TanhSinhIntegral extends Integrator {
    private double relTolerance_;
    private tanh_sinh tanh_sinh;

    public TanhSinhIntegral(
            double relTolerance,
            int maxRefinements,
            double minComplement) {
        super(QL_MAX_REAL, NULL_SIZE);
        this.tanh_sinh = new tanh_sinh(maxRefinements, minComplement);
        relTolerance_ = relTolerance;

    }

    //todo 暂未实现
    @Override
    protected double integrate(Function f, double a, double b) {
        return this.tanh_sinh.integrate(f, a, b, 15, relTolerance_, null);
    }
}
