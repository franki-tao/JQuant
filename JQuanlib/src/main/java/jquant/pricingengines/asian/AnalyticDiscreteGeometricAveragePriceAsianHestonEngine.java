package jquant.pricingengines.asian;

import jquant.Average;
import jquant.DiscreteAveragingAsianOption;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic discrete geometric average price Asian engine in the Heston model.
 *
 * <p>Port of QuantLib
 * {@code ql/experimental/asian/analytic_discr_geom_av_price_heston.{hpp,cpp}}.
 * Implements Kim, Kim, Kim &amp; Wee, "A Recursive Method for Discretely
 * Monitored Geometric Asian Option Prices", Bull. Korean Math. Soc. 53,
 * 733-749 (2016).
 */
public class AnalyticDiscreteGeometricAveragePriceAsianHestonEngine
        extends DiscreteAveragingAsianOption.engine {
    private final double v0_, rho_, kappa_, theta_, sigma_, logS0_;
    private final Handle<YieldTermStructure> dividendYield_;
    private final Handle<YieldTermStructure> riskFreeRate_;
    private final Handle<Quote> s0_;
    private final HestonProcess process_;

    private Map<Integer, Complex> omegaTildeLookupTable_ = new HashMap<>();

    private final double xiRightLimit_;
    private final GaussLegendreIntegration integrator_;

    private double tr_t_;
    private double Tr_T_;
    private List<Double> tkr_tk_;

    public AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(
            HestonProcess process, double xiRightLimit) {
        process_ = process;
        xiRightLimit_ = xiRightLimit;
        integrator_ = new GaussLegendreIntegration(128);
        registerWith(process_);

        v0_ = process_.v0();
        rho_ = process_.rho();
        kappa_ = process_.kappa();
        theta_ = process_.theta();
        sigma_ = process_.sigma();
        s0_ = process_.s0();
        logS0_ = Math.log(s0_.currentLink().value());

        riskFreeRate_ = process_.riskFreeRate();
        dividendYield_ = process_.dividendYield();
    }

    public AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(
            HestonProcess process) {
        this(process, 100.0);
    }

    private static Complex cosh(Complex z) {
        return z.exp().add(z.negate().exp()).multiply(0.5);
    }

    private static Complex sinh(Complex z) {
        return z.exp().subtract(z.negate().exp()).multiply(0.5);
    }

    private Complex F(Complex z1, Complex z2, double tau) {
        Complex temp = Complex.valueOf(kappa_ * kappa_
                - 2.0 * z1.getReal() * sigma_ * sigma_,
                -2.0 * z1.getImaginary() * sigma_ * sigma_).sqrt();
        if (Math.abs(kappa_ * kappa_ - 2.0 * sigma_ * sigma_) < 1e-8) {
            return Complex.ONE.add(
                    z2.multiply(sigma_ * sigma_).negate().add(kappa_)
                            .multiply(0.5));
        } else {
            return cosh(temp.multiply(0.5 * tau))
                    .add(z2.multiply(sigma_ * sigma_).negate().add(kappa_)
                            .multiply(sinh(temp.multiply(0.5 * tau)))
                            .divide(temp));
        }
    }

    private Complex F_tilde(Complex z1, Complex z2, double tau) {
        Complex temp = Complex.valueOf(kappa_ * kappa_
                - 2.0 * z1.getReal() * sigma_ * sigma_,
                -2.0 * z1.getImaginary() * sigma_ * sigma_).sqrt();
        return temp.multiply(sinh(temp.multiply(0.5 * tau))).multiply(0.5)
                .add(z2.multiply(sigma_ * sigma_).negate().add(kappa_)
                        .multiply(cosh(temp.multiply(0.5 * tau)))
                        .multiply(0.5));
    }

    private Complex z(Complex s, Complex w, int k, int n) {
        double k_ = k;
        double n_ = n;
        Complex base = s.multiply(n_ - k_ + 1).add(w.multiply(n_));
        Complex term1 = base.multiply(2 * rho_ * kappa_ - sigma_)
                .divide(2 * sigma_ * n_);
        Complex term2 = base.multiply(base).multiply(1 - rho_ * rho_)
                .divide(2 * n_ * n_);

        return term1.add(term2);
    }

    private Complex omega(Complex s, Complex w, int k, int kStar, int n) {
        if (k == kStar) {
            return Complex.ZERO;
        } else if (k == n + 1) {
            return w.multiply(rho_).divide(sigma_);
        } else {
            return s.multiply(rho_).divide(sigma_ * n);
        }
    }

    private Complex a(Complex s, Complex w, double t, double T, int kStar,
                      List<Double> t_n) {
        double kStar_ = kStar;
        double n_ = t_n.size();
        double temp = -rho_ * kappa_ * theta_ / sigma_;

        double summation = 0.0;
        double summation2 = 0.0;
        for (int i = kStar + 1; i <= t_n.size(); i++) {
            summation += t_n.get(i - 1);
            summation2 += tkr_tk_.get(i - 1);
        }
        Complex term1 = s.multiply((n_ - kStar_) / n_).add(w)
                .multiply(logS0_ - rho_ * v0_ / sigma_ - t * temp - tr_t_);
        Complex term2 = s.multiply(summation / n_).add(w.multiply(T))
                .multiply(temp)
                .add(w.multiply(Tr_T_))
                .add(s.multiply(summation2 / n_));

        return term1.add(term2);
    }

    private Complex omega_tilde(Complex s, Complex w, int k, int kStar,
                                int n, List<Double> tauK) {
        Complex omega_k = omega(s, w, k, kStar, n);
        if (k == n + 1) {
            return omega_k;
        } else {
            double dTauk = tauK.get(k + 1) - tauK.get(k);
            Complex z_kp1 = z(s, w, k + 1, n);

            Complex omega_kp1;
            Complex value = omegaTildeLookupTable_.get(k + 1);
            if (value != null) {
                omega_kp1 = value;
            } else {
                omega_kp1 = omega_tilde(s, w, k + 1, kStar, n, tauK);
            }

            Complex ratio = F_tilde(z_kp1, omega_kp1, dTauk)
                    .divide(F(z_kp1, omega_kp1, dTauk));
            Complex result = omega_k
                    .add(kappa_ / (sigma_ * sigma_))
                    .subtract(ratio.multiply(2.0 / (sigma_ * sigma_)));

            omegaTildeLookupTable_.put(k, result);

            return result;
        }
    }

    public Complex Phi(Complex s, Complex w, double t, double T, int kStar,
                       List<Double> t_n, List<Double> tauK) {
        omegaTildeLookupTable_ = new HashMap<>();

        int n = t_n.size();
        Complex aTerm = a(s, w, t, T, kStar, t_n);
        Complex omegaTerm = omega_tilde(s, w, kStar, kStar, n, tauK)
                .multiply(v0_);
        double term3 = kappa_ * kappa_ * theta_ * (T - t)
                / (sigma_ * sigma_);

        Complex summation = Complex.ZERO;
        for (int i = kStar + 1; i <= n + 1; i++) {
            double dTau = tauK.get(i) - tauK.get(i - 1);
            Complex z_k = z(s, w, i, n);
            Complex omega_tilde_k = omega_tilde(s, w, i, kStar, n, tauK);

            summation = summation.add(F(z_k, omega_tilde_k, dTau).log());
        }
        Complex term4 = summation.multiply(
                2 * kappa_ * theta_ / (sigma_ * sigma_));

        return aTerm.add(omegaTerm).add(term3).subtract(term4).exp();
    }

    private class Integrand implements Function {
        private final double t_, T_, K_, logK_;
        private final int kStar_;
        private final List<Double> t_n_, tauK_;
        private final AnalyticDiscreteGeometricAveragePriceAsianHestonEngine
                parent_ = AnalyticDiscreteGeometricAveragePriceAsianHestonEngine.this;
        private final double xiRightLimit_;
        private final Complex i_ = new Complex(0.0, 1.0);

        Integrand(double t, double T, int kStar, List<Double> t_n,
                  List<Double> tauK, double K, double xiRightLimit) {
            t_ = t;
            T_ = T;
            K_ = K;
            logK_ = Math.log(K);
            kStar_ = kStar;
            t_n_ = new ArrayList<>(t_n);
            tauK_ = new ArrayList<>(tauK);
            xiRightLimit_ = xiRightLimit;
        }

        @Override
        public double value(double xi) {
            double xiDash = (0.5 + 1e-8 + 0.5 * xi) * xiRightLimit_;

            Complex inner1 = parent_.Phi(Complex.ONE.add(i_.multiply(xiDash)),
                    Complex.ZERO, t_, T_, kStar_, t_n_, tauK_);
            Complex inner2 = parent_.Phi(i_.multiply(xiDash), Complex.ZERO,
                    t_, T_, kStar_, t_n_, tauK_).multiply(-K_);

            return 0.5 * xiRightLimit_
                    * inner1.add(inner2)
                    .multiply(new Complex(0.0, -xiDash * logK_).exp())
                    .divide(i_.multiply(xiDash))
                    .getReal();
        }
    }

    @Override
    public void calculate() {
        /*
         * This engine cannot really check for the averageType==Geometric
         * since it can be used as control variate for the Arithmetic version.
         */
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        double runningLog;
        int pastFixings;
        if (arguments_.averageType == Average.Type.Geometric) {
            QL_REQUIRE(arguments_.runningAccumulator > 0.0,
                    "positive running product required: "
                            + arguments_.runningAccumulator + " not allowed");
            runningLog = Math.log(arguments_.runningAccumulator);
            pastFixings = arguments_.pastFixings;
        } else {
            runningLog = 0.0;
            pastFixings = 0;
        }

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        double strike = payoff.strike();
        Date exerciseDate = arguments_.exercise.lastDate();

        double expiryTime = process_.time(exerciseDate);
        QL_REQUIRE(expiryTime >= 0.0, "Expiry Date cannot be in the past");

        double expiryDcf = riskFreeRate_.currentLink().discount(expiryTime);

        double startTime = 0.0;
        List<Double> fixingTimes = new ArrayList<>();
        List<Double> tauK;
        for (Date fixingDate : arguments_.fixingDates) {
            fixingTimes.add(process_.time(fixingDate));
        }
        Collections.sort(fixingTimes);
        tauK = new ArrayList<>(fixingTimes);

        tauK.add(0, startTime);
        tauK.add(expiryTime);

        for (int i = 0; i < pastFixings; i++) {
            fixingTimes.add(0, -1.0);
            tauK.add(0, -1.0);
        }

        int kStar = pastFixings;

        tr_t_ = 0.0;
        Tr_T_ = 0.0;
        tkr_tk_ = new ArrayList<>();
        tr_t_ = -Math.log(riskFreeRate_.currentLink().discount(startTime)
                / dividendYield_.currentLink().discount(startTime));
        Tr_T_ = -Math.log(riskFreeRate_.currentLink().discount(expiryTime)
                / dividendYield_.currentLink().discount(expiryTime));
        for (double fixingTime : fixingTimes) {
            if (fixingTime < 0) {
                tkr_tk_.add(1.0);
            } else {
                tkr_tk_.add(-Math.log(
                        riskFreeRate_.currentLink().discount(fixingTime)
                                / dividendYield_.currentLink()
                                .discount(fixingTime)));
            }
        }

        double prefactor = Math.exp(runningLog / fixingTimes.size());
        double adjustedStrike = strike / prefactor;

        double term1 = 0.5 * (Phi(Complex.ONE, Complex.ZERO, startTime,
                expiryTime, kStar, fixingTimes, tauK).getReal()
                - adjustedStrike);

        Integrand integrand = new Integrand(startTime, expiryTime, kStar,
                fixingTimes, tauK, adjustedStrike, xiRightLimit_);
        double term2 = integrator_.value(integrand) / Math.PI;

        double value = 0.0;
        switch (payoff.optionType()) {
            case Call:
                value = expiryDcf * prefactor * (term1 + term2);
                break;
            case Put:
                value = expiryDcf * prefactor * (-term1 + term2);
                break;
            default:
                QL_FAIL("unknown option type");
        }

        results_.value = value;

        results_.additionalResults.put("dcf", expiryDcf);
        results_.additionalResults.put("s0", s0_.currentLink().value());
        results_.additionalResults.put("strike", strike);
        results_.additionalResults.put("expiryTime", expiryTime);
        results_.additionalResults.put("term1", term1);
        results_.additionalResults.put("term2", term2);
        results_.additionalResults.put("xiRightLimit", xiRightLimit_);
        results_.additionalResults.put("fixingTimes", fixingTimes);
        results_.additionalResults.put("tauK", tauK);
        results_.additionalResults.put("adjustedStrike", adjustedStrike);
        results_.additionalResults.put("prefactor", prefactor);
        results_.additionalResults.put("kStar", kStar);
    }
}
