package jquant.pricingengines.vanilla;

import jquant.math.distributions.GammaFunction;
import jquant.models.equity.HestonModel;
import jquant.models.shortrate.onefactormodels.HullWhite;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic Heston-Hull-White engine based on the H1-HW approximation.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/analytich1hwengine.{hpp,cpp}}.
 */
public class AnalyticH1HWEngine extends AnalyticHestonHullWhiteEngine {

    private final double rhoSr_;

    public AnalyticH1HWEngine(final HestonModel model,
                              final HullWhite hullWhiteModel,
                              double rhoSr,
                              int integrationOrder) {
        super(model, hullWhiteModel, integrationOrder);
        rhoSr_ = rhoSr;
        QL_REQUIRE(rhoSr_ >= 0.0, "Fourier integration is not stable if "
                + "the equity interest rate correlation is negative");
    }

    public AnalyticH1HWEngine(final HestonModel model,
                              final HullWhite hullWhiteModel,
                              double rhoSr,
                              double relTolerance,
                              int maxEvaluations) {
        super(model, hullWhiteModel, relTolerance, maxEvaluations);
        rhoSr_ = rhoSr;
    }

    @Override
    protected Complex addOnTerm(double u, double t, int j) {
        return super.addOnTerm(u, t, j)
                .add(new Fj_Helper(model_, hullWhiteModel_, rhoSr_, t, 0.0, j).value(u));
    }

    // integration helper class
    private static class Fj_Helper {
        private final int j_;
        private final double lambda_, eta_;
        private final double v0_, kappa_, theta_, gamma_;
        private final double d_;
        private final double rhoSr_;
        private final double term_;

        Fj_Helper(HestonModel hestonModel,
                  HullWhite hullWhiteModel,
                  double rhoSr, double term, double strike, int j) {
            j_ = j;
            lambda_ = hullWhiteModel.a();
            eta_ = hullWhiteModel.sigma();
            v0_ = hestonModel.v0();
            kappa_ = hestonModel.kappa();
            theta_ = hestonModel.theta();
            gamma_ = hestonModel.sigma();
            d_ = 4.0 * kappa_ * theta_ / (gamma_ * gamma_);
            rhoSr_ = rhoSr;
            term_ = term;
        }

        double c(double t) {
            return gamma_ * gamma_ / (4 * kappa_) * (1.0 - Math.exp(-kappa_ * t));
        }

        double lambda(double t) {
            return 4.0 * kappa_ * v0_ * Math.exp(-kappa_ * t)
                    / (gamma_ * gamma_ * (1.0 - Math.exp(-kappa_ * t)));
        }

        double LambdaApprox(double t) {
            return Math.sqrt(c(t) * (lambda(t) - 1.0)
                    + c(t) * d_ * (1.0 + 1.0 / (2.0 * (d_ + lambda(t)))));
        }

        double Lambda(double t) {
            final GammaFunction g = new GammaFunction();
            final int maxIter = 1000;
            final double lambdaT = lambda(t);

            int i = 0;
            double retVal = 0.0, s;

            do {
                double k = (double) i;
                s = Math.exp(k * Math.log(0.5 * lambdaT) + g.logValue(0.5 * (1 + d_) + k)
                        - g.logValue(k + 1) - g.logValue(0.5 * d_ + k));
                retVal += s;
            } while (s > Math.ulp(1.0f) && ++i < maxIter);

            QL_REQUIRE(i < maxIter, "can not calculate Lambda");

            retVal *= Math.sqrt(2 * c(t)) * Math.exp(-0.5 * lambdaT);
            return retVal;
        }

        Complex value(double u) {
            final double gamma2 = gamma_ * gamma_;

            double a, b, c;
            if (8.0 * kappa_ * theta_ / gamma2 > 1.0) {
                a = Math.sqrt(theta_ - gamma2 / (8.0 * kappa_));
                b = Math.sqrt(v0_) - a;
                c = -Math.log((LambdaApprox(1.0) - a) / b);
            } else {
                a = Math.sqrt(gamma2 / (2.0 * kappa_))
                        * Math.exp(new GammaFunction().logValue(0.5 * (d_ + 1.0))
                        - new GammaFunction().logValue(0.5 * d_));

                final double t1 = 0.0;
                final double t2 = 1.0 / kappa_;

                final double Lambda_t1 = Math.sqrt(v0_);
                final double Lambda_t2 = Lambda(t2);

                c = Math.log((Lambda_t2 - a) / (Lambda_t1 - a)) / (t1 - t2);
                b = Math.exp(c * t1) * (Lambda_t1 - a);
            }

            final Complex I4 =
                    new Complex(u * u, ((j_ == 1) ? -u : u))
                            .multiply(-1.0 / lambda_)
                            .multiply(b / c * (1.0 - Math.exp(-c * term_)) + a * term_
                                    + a / lambda_ * (Math.exp(-lambda_ * term_) - 1.0)
                                    + b / (c - lambda_) * Math.exp(-c * term_)
                                    * (1.0 - Math.exp(-term_ * (lambda_ - c))));

            return I4.multiply(eta_ * rhoSr_);
        }
    }
}
