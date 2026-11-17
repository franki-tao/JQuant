package jquant.pricingengines.vanilla;

import jquant.math.integrals.Integrator;
import jquant.math.integrals.TanhSinhIntegral;

/**
 * Legendre-Tanh-Sinh (l,m,n)-eps scheme.
 *
 * <p>Ported from {@code QdFpLegendreTanhSinhScheme} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.cpp}.
 */
public class QdFpLegendreTanhSinhScheme extends QdFpLegendreScheme {
    private final double eps_;

    public QdFpLegendreTanhSinhScheme(int l, int m, int n, double eps) {
        super(l, m, n, 1);
        eps_ = eps;
    }

    @Override
    public Integrator getExerciseBoundaryToPriceIntegrator() {
        return new TanhSinhIntegral(eps_);
    }
}
