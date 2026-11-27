package jquant.math.integrals;

import java.util.function.DoubleUnaryOperator;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Official QuantLib {@code ql/math/integrals/fourierintegration.hpp/.cpp} port
 * (Gauss-Laguerre subset).
 *
 * <p>Fourier integration used by characteristic-function pricing engines.
 * Wraps a quadrature together with the change of variables that maps the
 * semi-infinite Fourier integration range onto the domain the chosen
 * algorithm expects. This port implements the non-adaptive Gauss-Laguerre
 * branch used by {@code AnalyticHestonEngine}'s Gatheral path. The other
 * factory methods (Gauss-Legendre, Gauss-Chebyshev, adaptive and discrete
 * integrators) are not ported because no ported engine uses them.
 */
public class FourierIntegration {

    private enum Algorithm {
        GaussLaguerre
    }

    private final Algorithm intAlgo_;
    private final GaussianQuadrature gaussianQuadrature_;

    private FourierIntegration(Algorithm intAlgo, GaussianQuadrature gaussianQuadrature) {
        intAlgo_ = intAlgo;
        gaussianQuadrature_ = gaussianQuadrature;
    }

    public static FourierIntegration gaussLaguerre() {
        return gaussLaguerre(128);
    }

    public static FourierIntegration gaussLaguerre(int integrationOrder) {
        return new FourierIntegration(Algorithm.GaussLaguerre,
                new GaussLaguerreIntegration(integrationOrder));
    }

    public double calculate(double c_inf, DoubleUnaryOperator f) {
        double retVal;

        switch (intAlgo_) {
            case GaussLaguerre:
                // Official GaussianQuadrature::operator() sums
                // i = Integer(order())-1 downto 0 (gaussianquadratures.hpp).
                retVal = 0.0;
                for (int i = gaussianQuadrature_.order() - 1; i >= 0; --i)
                    retVal += gaussianQuadrature_.weights().get(i)
                            * f.applyAsDouble(gaussianQuadrature_.x().get(i));
                break;
            default:
                QL_FAIL("unknwon integration algorithm");
                retVal = 0.0;
        }

        return retVal;
    }

    public int numberOfEvaluations() {
        return gaussianQuadrature_.order();
    }

    public boolean isAdaptiveIntegration() {
        return false;
    }
}
