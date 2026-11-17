package jquant.math.integrals;

import jquant.math.Function;
import jquant.math.tanh_sinh;

import static jquant.math.MathUtils.NULL_SIZE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;

/**
 * Tanh-sinh quadrature.
 *
 * <p>Ported from {@code ql/math/integrals/tanhsinhintegral.hpp}. The
 * constructor defaults are those of the Boost-backed official implementation.
 */
public class TanhSinhIntegral extends Integrator {
    private final double relTolerance_;
    private final int maxRefinements_;
    private final tanh_sinh tanh_sinh_;

    public TanhSinhIntegral() {
        this(Math.sqrt(QL_EPSILON), 15, Double.MIN_VALUE * 4);
    }

    public TanhSinhIntegral(double relTolerance) {
        this(relTolerance, 15, Double.MIN_VALUE * 4);
    }

    public TanhSinhIntegral(double relTolerance, int maxRefinements,
                            double minComplement) {
        super(QL_MAX_REAL, NULL_SIZE);
        relTolerance_ = relTolerance;
        maxRefinements_ = maxRefinements;
        tanh_sinh_ = new tanh_sinh(maxRefinements, minComplement);
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        return tanh_sinh_.integrate(x -> {
            increaseNumberOfEvaluations(1);
            return f.value(x);
        }, a, b, maxRefinements_, relTolerance_, null);
    }
}
