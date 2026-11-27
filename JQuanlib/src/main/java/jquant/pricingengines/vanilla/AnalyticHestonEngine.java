package jquant.pricingengines.vanilla;

import jquant.Option;
import jquant.math.integrals.FourierIntegration;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;
import jquant.time.Date;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Official QuantLib {@code ql/pricingengines/vanilla/analytichestonengine.hpp/.cpp} port
 * (Gatheral and branch-correction Fourier paths).
 *
 * <p>Integration detail: Two algebraically equivalent formulations of the
 * complex logarithm of the Heston model exist. Gatherals [2005] (also
 * Duffie, Pan and Singleton [2000], and Schoutens, Simons and Tistaert
 * [2004]) version does not cause discoutinuities whereas the original
 * version (e.g. Heston [1993]) needs some sort of "branch correction" to
 * work properly. Gatheral's version does also work with adaptive
 * integration routines and should be preferred over the original Heston
 * version.
 *
 * <p>This port implements the {@link ComplexLogFormula#Gatheral} and
 * {@link ComplexLogFormula#BranchCorrection} paths. The control-variate
 * formulations (Andersen-Piterbarg, asymptotic, angled contour, optimal CV)
 * are not ported because no ported test uses them; {@link #priceVanillaPayoff}
 * raises "unknown complex log formula" for those enumerators.
 */
public class AnalyticHestonEngine {

    public enum ComplexLogFormula {
        // Gatheral form of characteristic function w/o control variate
        Gatheral,
        // old branch correction form of the characteristic function w/o control variate
        BranchCorrection,
        // Gatheral form with Andersen-Piterbarg control variate
        AndersenPiterbarg,
        // same as AndersenPiterbarg, but a slightly better control variate
        AndersenPiterbargOptCV,
        // Gatheral form with asymptotic expansion of the characteristic function as control variate
        AsymptoticChF,
        // angled contour shift integral with control variate
        AngledContour,
        // angled contour shift integral w/o control variate
        AngledContourNoCV,
        // auto selection of best control variate algorithm from above
        OptimalCV
    }

    private final HestonModel model_;
    private int evaluations_;
    private final ComplexLogFormula cpxLog_;
    private final FourierIntegration integration_;
    private final double andersenPiterbargEpsilon_;
    private final double alpha_;

    public AnalyticHestonEngine(HestonModel model,
                                ComplexLogFormula cpxLog,
                                FourierIntegration integration) {
        this(model, cpxLog, integration, 1e-25, -0.5);
    }

    public AnalyticHestonEngine(HestonModel model,
                                ComplexLogFormula cpxLog,
                                FourierIntegration integration,
                                double andersenPiterbargEpsilon,
                                double alpha) {
        model_ = model;
        evaluations_ = 0;
        cpxLog_ = cpxLog;
        integration_ = integration;
        andersenPiterbargEpsilon_ = andersenPiterbargEpsilon;
        alpha_ = alpha;
        QL_REQUIRE(cpxLog_ != ComplexLogFormula.BranchCorrection
                        || !integration.isAdaptiveIntegration(),
                "Branch correction does not work in conjunction "
                        + "with adaptive integration methods");
    }

    public int numberOfEvaluations() {
        return evaluations_;
    }

    public double priceVanillaPayoff(PlainVanillaPayoff payoff, Date maturity) {
        HestonProcess process = model_.process();
        // Official uses YieldTermStructure::discount(const Date&), not discount(time(date)).
        double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(maturity)
                / process.riskFreeRate().getValue().discount(maturity);

        return priceVanillaPayoff(payoff, process.time(maturity), fwd);
    }

    public double priceVanillaPayoff(PlainVanillaPayoff payoff, double maturity) {
        HestonProcess process = model_.process();
        double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(maturity)
                / process.riskFreeRate().getValue().discount(maturity);

        return priceVanillaPayoff(payoff, maturity, fwd);
    }

    private double priceVanillaPayoff(PlainVanillaPayoff payoff,
                                      double maturity, double fwd) {
        double value;

        HestonProcess process = model_.process();
        double dr = process.riskFreeRate().getValue().discount(maturity);

        double strike = payoff.strike();
        double spot = process.s0().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        double df = spot / fwd;
        double dd = dr / df;

        double kappa = model_.kappa();
        double sigma = model_.sigma();
        double theta = model_.theta();
        double rho = model_.rho();
        double v0 = model_.v0();

        evaluations_ = 0;

        switch (cpxLog_) {
            case Gatheral:
            case BranchCorrection: {
                double c_inf = Math.min(0.2, Math.max(0.0001,
                        Math.sqrt(1.0 - rho * rho) / sigma)) * (v0 + kappa * theta * maturity);

                double p1 = integration_.calculate(c_inf,
                        new Fj_Helper(kappa, theta, sigma, v0, spot, rho, this,
                                cpxLog_, maturity, strike, df, 1)) / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                double p2 = integration_.calculate(c_inf,
                        new Fj_Helper(kappa, theta, sigma, v0, spot, rho, this,
                                cpxLog_, maturity, strike, df, 2)) / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                switch (payoff.optionType()) {
                    case Call:
                        value = spot * dd * (p1 + 0.5)
                                - strike * dr * (p2 + 0.5);
                        break;
                    case Put:
                        value = spot * dd * (p1 - 0.5)
                                - strike * dr * (p2 - 0.5);
                        break;
                    default:
                        QL_FAIL("unknown option type");
                        value = 0.0;
                }
            }
            break;
            default:
                QL_FAIL("unknown complex log formula");
                value = 0.0;
        }

        return value;
    }

    // call back for extended stochastic volatility
    // plus jump diffusion engines like bates model
    protected Complex addOnTerm(double phi, double t, int j) {
        return new Complex(0.0, 0.0);
    }

    // helper class for integration
    private class Fj_Helper implements java.util.function.DoubleUnaryOperator {
        private final int j_;
        private final double kappa_, theta_, sigma_, v0_;
        private final ComplexLogFormula cpxLog_;

        // helper variables
        private final double term_;
        private final double x_, sx_, dd_;
        private final double sigma2_, rsigma_;
        private final double t0_;

        // log branch counter
        private int b_ = 0;     // log branch counter
        private double g_km1_ = 0; // imag part of last log value

        private final AnalyticHestonEngine engine_;

        Fj_Helper(double kappa, double theta,
                  double sigma, double v0, double s0, double rho,
                  AnalyticHestonEngine engine,
                  ComplexLogFormula cpxLog,
                  double term, double strike, double ratio, int j) {
            j_ = j;
            kappa_ = kappa;
            theta_ = theta;
            sigma_ = sigma;
            v0_ = v0;
            cpxLog_ = cpxLog;
            term_ = term;
            x_ = Math.log(s0);
            sx_ = Math.log(strike);
            dd_ = x_ - Math.log(ratio);
            sigma2_ = sigma_ * sigma_;
            rsigma_ = rho * sigma_;
            t0_ = kappa - ((j == 1) ? rho * sigma : 0.0);

            engine_ = engine;
        }

        @Override
        public double applyAsDouble(double phi) {
            double rpsig = rsigma_ * phi;

            Complex t1 = new Complex(t0_, -rpsig);
            Complex d = t1.multiply(t1)
                    .subtract(new Complex(-phi, (j_ == 1) ? 1.0 : -1.0)
                            .multiply(sigma2_ * phi))
                    .sqrt();
            Complex ex = d.multiply(-term_).exp();
            Complex addOnTerm =
                    engine_ != null ? engine_.addOnTerm(phi, term_, j_) : new Complex(0.0);

            if (cpxLog_ == ComplexLogFormula.Gatheral) {
                if (phi != 0.0) {
                    if (sigma_ > 1e-5) {
                        Complex p = t1.subtract(d).divide(t1.add(d));
                        Complex g = new Complex(1.0).subtract(p.multiply(ex))
                                .divide(new Complex(1.0).subtract(p))
                                .log();

                        return t1.subtract(d).multiply(new Complex(1.0).subtract(ex))
                                .multiply(v0_)
                                .divide(new Complex(1.0).subtract(ex.multiply(p))
                                        .multiply(sigma2_))
                                .add(t1.subtract(d).multiply(term_)
                                        .subtract(g.multiply(2.0))
                                        .multiply(kappa_ * theta_ / sigma2_))
                                .add(new Complex(0.0, phi * (dd_ - sx_)))
                                .add(addOnTerm)
                                .exp().getImaginary() / phi;
                    } else {
                        Complex td = new Complex(-phi, (j_ == 1) ? 1.0 : -1.0)
                                .multiply(phi)
                                .divide(t1.multiply(2.0));
                        Complex p = td.multiply(sigma2_).divide(t1.add(d));
                        Complex g = p.multiply(new Complex(1.0).subtract(ex));

                        return td.multiply(new Complex(1.0).subtract(ex))
                                .multiply(v0_)
                                .divide(new Complex(1.0).subtract(p.multiply(ex)))
                                .add(td.multiply(term_)
                                        .subtract(g.multiply(2.0 / sigma2_))
                                        .multiply(kappa_ * theta_))
                                .add(new Complex(0.0, phi * (dd_ - sx_)))
                                .add(addOnTerm)
                                .exp().getImaginary() / phi;
                    }
                } else {
                    // use l'Hospital's rule to get lim_{phi->0}
                    if (j_ == 1) {
                        double kmr = rsigma_ - kappa_;
                        if (Math.abs(kmr) > 1e-7) {
                            return dd_ - sx_
                                    + (Math.exp(kmr * term_) * kappa_ * theta_
                                    - kappa_ * theta_ * (kmr * term_ + 1.0)) / (2 * kmr * kmr)
                                    - v0_ * (1.0 - Math.exp(kmr * term_)) / (2.0 * kmr);
                        } else
                            // \kappa = \rho * \sigma
                            return dd_ - sx_ + 0.25 * kappa_ * theta_ * term_ * term_
                                    + 0.5 * v0_ * term_;
                    } else {
                        return dd_ - sx_
                                - (Math.exp(-kappa_ * term_) * kappa_ * theta_
                                + kappa_ * theta_ * (kappa_ * term_ - 1.0)) / (2 * kappa_ * kappa_)
                                - v0_ * (1.0 - Math.exp(-kappa_ * term_)) / (2 * kappa_);
                    }
                }
            } else if (cpxLog_ == ComplexLogFormula.BranchCorrection) {
                Complex p = t1.add(d).divide(t1.subtract(d));

                // next term: g = std::log((1.0 - p*std::exp(d*term_))/(1.0 - p))
                Complex g;

                // the exp of the following expression is needed.
                Complex e = p.log().add(d.multiply(term_));

                // does it fit to the machine precision?
                if (Math.exp(-e.getReal()) > QL_EPSILON) {
                    g = new Complex(1.0).subtract(p.divide(ex))
                            .divide(new Complex(1.0).subtract(p))
                            .log();
                } else {
                    // use a "big phi" approximation
                    g = d.multiply(term_)
                            .add(p.divide(p.subtract(new Complex(1.0))).log());

                    if (g.getImaginary() > M_PI || g.getImaginary() <= -M_PI) {
                        // get back to principal branch of the complex logarithm
                        double im = g.getImaginary() % (2 * M_PI);
                        if (im > M_PI)
                            im -= 2 * M_PI;
                        else if (im <= -M_PI)
                            im += 2 * M_PI;

                        g = new Complex(g.getReal(), im);
                    }
                }

                // be careful here as we have to use a log branch correction
                // to deal with the discontinuities of the complex logarithm.
                // the principal branch is not always the correct one.
                // (s. A. Sepp, chapter 4)
                // remark: there is still the change that we miss a branch
                // if the order of the integration is not high enough.
                double tmp = g.getImaginary() - g_km1_;
                if (tmp <= -M_PI)
                    ++b_;
                else if (tmp > M_PI)
                    --b_;

                g_km1_ = g.getImaginary();
                g = g.add(new Complex(0.0, 2 * b_ * M_PI));

                return t1.add(d).multiply(ex.subtract(new Complex(1.0)))
                        .multiply(v0_)
                        .divide(ex.subtract(p).multiply(sigma2_))
                        .add(t1.add(d).multiply(term_)
                                .subtract(g.multiply(2.0))
                                .multiply(kappa_ * theta_ / sigma2_))
                        .add(new Complex(0.0, phi * (dd_ - sx_)))
                        .add(addOnTerm)
                        .exp().getImaginary() / phi;
            } else {
                QL_FAIL("unknown complex logarithm formula");
                return 0.0;
            }
        }
    }
}
