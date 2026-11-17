package jquant.pricingengines.vanilla;

import jquant.math.integrals.GaussLegendreIntegrator;
import jquant.math.integrals.Integrator;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Gauss-Legendre (l,m,n)-p scheme.
 *
 * <p>Ported from {@code QdFpLegendreScheme} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.cpp}.
 */
public class QdFpLegendreScheme implements QdFpIterationScheme {
    private final int m_, n_;
    private final Integrator fpIntegrator_;
    private final Integrator exerciseBoundaryIntegrator_;

    public QdFpLegendreScheme(int l, int m, int n, int p) {
        m_ = m;
        n_ = n;
        fpIntegrator_ = new GaussLegendreIntegrator(l);
        exerciseBoundaryIntegrator_ = new GaussLegendreIntegrator(p);

        QL_REQUIRE(m_ > 0, "at least one fixed point iteration step is needed");
        QL_REQUIRE(n_ > 0, "at least one interpolation point is needed");
    }

    @Override
    public int getNumberOfChebyshevInterpolationNodes() {
        return n_;
    }

    @Override
    public int getNumberOfNaiveFixedPointSteps() {
        return m_ - 1;
    }

    @Override
    public int getNumberOfJacobiNewtonFixedPointSteps() {
        return 1;
    }

    @Override
    public Integrator getFixedPointIntegrator() {
        return fpIntegrator_;
    }

    @Override
    public Integrator getExerciseBoundaryToPriceIntegrator() {
        return exerciseBoundaryIntegrator_;
    }
}
