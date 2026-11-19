package jquant.pricingengines.asian;

import jquant.Average;
import jquant.ContinuousAveragingAsianOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.math.Function;
import jquant.math.integrals.GaussLegendreIntegration;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.Map;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic continuous geometric average price Asian engine in the Heston
 * model.
 *
 * <p>Port of QuantLib
 * {@code ql/experimental/asian/analytic_cont_geom_av_price_heston.{hpp,cpp}}.
 * Implements the solution of Kim &amp; Wee, "Pricing of geometric Asian
 * options under Heston's stochastic volatility model", Quantitative Finance
 * 14:10, 1795-1809 (2014).
 */
public class AnalyticContinuousGeometricAveragePriceAsianHestonEngine
        extends ContinuousAveragingAsianOption.engine {
    private final double v0_, rho_, kappa_, theta_, sigma_;
    private final Handle<YieldTermStructure> dividendYield_;
    private final Handle<YieldTermStructure> riskFreeRate_;
    private final Handle<Quote> s0_;
    private final HestonProcess process_;

    private final double a1_, a2_;
    private double a3_ = 0.0, a4_ = 0.0, a5_ = 0.0;

    private Map<Integer, Complex> fLookupTable_ = new HashMap<>();

    private final int summationCutoff_;
    private final double xiRightLimit_;
    private final GaussLegendreIntegration integrator_;

    public AnalyticContinuousGeometricAveragePriceAsianHestonEngine(
            HestonProcess process,
            int summationCutoff,
            double xiRightLimit) {
        process_ = process;
        summationCutoff_ = summationCutoff;
        xiRightLimit_ = xiRightLimit;
        integrator_ = new GaussLegendreIntegration(128);
        registerWith(process_);

        v0_ = process_.v0();
        rho_ = process_.rho();
        kappa_ = process_.kappa();
        theta_ = process_.theta();
        sigma_ = process_.sigma();
        s0_ = process_.s0();

        riskFreeRate_ = process_.riskFreeRate();
        dividendYield_ = process_.dividendYield();

        a1_ = 2.0 * v0_ / (sigma_ * sigma_);
        a2_ = 2.0 * kappa_ * theta_ / (sigma_ * sigma_);
    }

    public AnalyticContinuousGeometricAveragePriceAsianHestonEngine(
            HestonProcess process) {
        this(process, 50, 100.0);
    }

    private Complex z1_f(Complex s, Complex w, double T) {
        return s.multiply(s).multiply((1 - rho_ * rho_) / (2 * T * T));
    }

    private Complex z2_f(Complex s, Complex w, double T) {
        return s.multiply((2 * rho_ * kappa_ - sigma_) / (2 * sigma_ * T))
                .add(s.multiply(w).multiply((1 - rho_ * rho_) / T));
    }

    private Complex z3_f(Complex s, Complex w, double T) {
        return s.multiply(rho_ / (sigma_ * T))
                .add(w.multiply(0.5 * (2 * rho_ * kappa_ - sigma_) / sigma_))
                .add(w.multiply(w).multiply(0.5 * (1 - rho_ * rho_)));
    }

    private Complex z4_f(Complex s, Complex w) {
        return w.multiply(rho_ / sigma_);
    }

    private Complex f(Complex z1, Complex z2, Complex z3, Complex z4,
                      int n, double tau) {
        Complex result;

        if (n < 2) {
            if (n < 0) {
                result = Complex.ZERO;
            } else if (n == 0) {
                result = Complex.ONE;
            } else {
                result = z4.multiply(sigma_ * sigma_).negate()
                        .add(kappa_).multiply(0.5 * tau);
            }
        } else {
            Complex[] fMinusN = new Complex[4];
            double prefactor = -0.5 * sigma_ * sigma_ * tau * tau
                    / (n * (n - 1));

            for (int offset = 1; offset < 5; offset++) {
                int location = n - offset;
                Complex value = fLookupTable_.get(location);
                if (value != null) {
                    fMinusN[offset - 1] = value;
                } else {
                    fMinusN[offset - 1] = f(z1, z2, z3, z4, location, tau);
                }
            }

            result = z1.multiply(tau * tau).multiply(fMinusN[3])
                    .add(z2.multiply(tau).multiply(fMinusN[2]))
                    .add(z3.subtract(0.5 * kappa_ * kappa_
                                    / (sigma_ * sigma_))
                            .multiply(fMinusN[1]))
                    .multiply(prefactor);
        }

        fLookupTable_.put(n, result);

        return result;
    }

    private Complex[] F_F_tilde(Complex z1, Complex z2, Complex z3,
                                Complex z4, double tau, int cutoff) {
        Complex runningSum1 = Complex.ZERO;
        Complex runningSum2 = Complex.ZERO;

        for (int i = 0; i < cutoff; i++) {
            Complex temp = f(z1, z2, z3, z4, i, tau);
            runningSum1 = runningSum1.add(temp);
            runningSum2 = runningSum2.add(temp.multiply((double) i / tau));
        }

        return new Complex[]{runningSum1, runningSum2};
    }

    public Complex Phi(Complex s, Complex w, double T) {
        return Phi(s, w, T, 0.0, 50);
    }

    public Complex Phi(Complex s, Complex w, double T, double t) {
        return Phi(s, w, T, t, 50);
    }

    public Complex Phi(Complex s, Complex w, double T, double t, int cutoff) {
        double tau = T - t;

        Complex z1 = z1_f(s, w, T);
        Complex z2 = z2_f(s, w, T);
        Complex z3 = z3_f(s, w, T);
        Complex z4 = z4_f(s, w);

        fLookupTable_ = new HashMap<>();
        Complex[] temp = F_F_tilde(z1, z2, z3, z4, tau, cutoff);

        Complex F = temp[0];
        Complex F_tilde = temp[1];

        return F_tilde.multiply(-a1_).divide(F)
                .subtract(F.log().multiply(a2_))
                .add(s.multiply(a3_))
                .add(w.multiply(a4_))
                .add(a5_)
                .exp();
    }

    private class Integrand implements Function {
        private final double t_ = 0.0, T_, K_, logK_;
        private final int cutoff_;
        private final AnalyticContinuousGeometricAveragePriceAsianHestonEngine
                parent_ = AnalyticContinuousGeometricAveragePriceAsianHestonEngine.this;
        private final double xiRightLimit_;
        private final Complex i_ = new Complex(0.0, 1.0);

        Integrand(double T, int cutoff, double K, double xiRightLimit) {
            T_ = T;
            K_ = K;
            logK_ = Math.log(K);
            cutoff_ = cutoff;
            xiRightLimit_ = xiRightLimit;
        }

        @Override
        public double value(double xi) {
            double xiDash = (0.5 + 1e-8 + 0.5 * xi) * xiRightLimit_;

            Complex inner1 = parent_.Phi(Complex.ONE.add(i_.multiply(xiDash)),
                    Complex.ZERO, T_, t_, cutoff_);
            Complex inner2 = parent_.Phi(i_.multiply(xiDash), Complex.ZERO,
                    T_, t_, cutoff_).multiply(-K_);

            return 0.5 * xiRightLimit_
                    * inner1.add(inner2)
                    .multiply(new Complex(0.0, -xiDash * logK_).exp())
                    .divide(i_.multiply(xiDash))
                    .getReal();
        }
    }

    private class DcfIntegrand implements Function {
        private final double t_, T_, denominator_;
        private final Handle<YieldTermStructure> riskFreeRate__;
        private final Handle<YieldTermStructure> dividendYield__;

        DcfIntegrand(double t, double T,
                     Handle<YieldTermStructure> riskFreeRate,
                     Handle<YieldTermStructure> dividendYield) {
            t_ = t;
            T_ = T;
            riskFreeRate__ = riskFreeRate;
            dividendYield__ = dividendYield;
            denominator_ = Math.log(riskFreeRate__.currentLink().discount(t_))
                    - Math.log(dividendYield__.currentLink().discount(t_));
        }

        @Override
        public double value(double u) {
            double uDash = (0.5 + 1e-8 + 0.5 * u) * (T_ - t_) + t_;
            return 0.5 * (T_ - t_)
                    * (-Math.log(riskFreeRate__.currentLink().discount(uDash))
                    + Math.log(dividendYield__.currentLink().discount(uDash))
                    + denominator_);
        }
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.averageType == Average.Type.Geometric,
                "not a geometric average option");
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        double strike = payoff.strike();
        Date exerciseDate = arguments_.exercise.lastDate();

        double expiryTime = process_.time(exerciseDate);
        QL_REQUIRE(expiryTime >= 0.0, "Expiry Date cannot be in the past");

        double expiryDcf = riskFreeRate_.currentLink().discount(expiryTime);
        double expiryDividendDiscount =
                dividendYield_.currentLink().discount(expiryTime);

        // TODO: extend to cover seasoned options (discussed in paper)
        double startTime = 0.0;

        double t = startTime;
        double T = expiryTime;
        double tau = T - t;
        double logS0 = Math.log(s0_.currentLink().value());

        double dcf = riskFreeRate_.currentLink().discount(T)
                / riskFreeRate_.currentLink().discount(t);
        double qdcf = dividendYield_.currentLink().discount(T)
                / dividendYield_.currentLink().discount(t);
        DcfIntegrand dcfIntegrand = new DcfIntegrand(
                t, T, riskFreeRate_, dividendYield_);
        double integratedDcf = integrator_.value(dcfIntegrand);

        a3_ = (tau * logS0 + integratedDcf) / T
                - kappa_ * theta_ * rho_ * tau * tau / (2 * sigma_ * T)
                - rho_ * tau * v0_ / (sigma_ * T);
        a4_ = logS0 * qdcf / dcf - rho_ * v0_ / sigma_
                + rho_ * kappa_ * theta_ * tau / sigma_;
        a5_ = (kappa_ * v0_ + kappa_ * kappa_ * theta_ * tau)
                / (sigma_ * sigma_);

        double term1 = 0.5 * (Phi(Complex.ONE, Complex.ZERO, T, t,
                summationCutoff_).getReal() - strike);

        Integrand integrand = new Integrand(T, summationCutoff_, strike,
                xiRightLimit_);
        double term2 = integrator_.value(integrand) / Math.PI;

        double value = 0.0;
        switch (payoff.optionType()) {
            case Call:
                value = expiryDcf * (term1 + term2);
                break;
            case Put:
                value = expiryDcf * (-term1 + term2);
                break;
            default:
                QL_FAIL("unknown option type");
        }

        results_.value = value;

        results_.additionalResults.put("dcf", expiryDcf);
        results_.additionalResults.put("qf", expiryDividendDiscount);
        results_.additionalResults.put("s0", s0_.currentLink().value());
        results_.additionalResults.put("strike", strike);
        results_.additionalResults.put("expiryTime", expiryTime);
        results_.additionalResults.put("exercise", exerciseDate);

        results_.additionalResults.put("term1", term1);
        results_.additionalResults.put("term2", term2);
        results_.additionalResults.put("xiRightLimit", xiRightLimit_);
        results_.additionalResults.put("summationCutoff", summationCutoff_);

        results_.additionalResults.put("a1", a1_);
        results_.additionalResults.put("a2", a2_);
        results_.additionalResults.put("a3", a3_);
        results_.additionalResults.put("a4", a4_);
        results_.additionalResults.put("a5", a5_);
    }
}
