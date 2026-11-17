package jquant.pricingengines.vanilla;

import jquant.math.integrals.Integrator;
import jquant.math.integrals.TanhSinhIntegral;

/**
 * Tanh-sinh (m,n)-eps scheme.
 *
 * <p>Ported from {@code QdFpTanhSinhIterationScheme} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.cpp}.
 */
public class QdFpTanhSinhIterationScheme implements QdFpIterationScheme {
    private final int m_, n_;
    private final Integrator integrator_;

    public QdFpTanhSinhIterationScheme(int m, int n, double eps) {
        m_ = m;
        n_ = n;
        integrator_ = new TanhSinhIntegral(eps);
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
        return integrator_;
    }

    @Override
    public Integrator getExerciseBoundaryToPriceIntegrator() {
        return integrator_;
    }
}
