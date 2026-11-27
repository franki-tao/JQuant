package jquant.pricingengines.vanilla;

import jquant.Compounding;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.TimeGrid;
import jquant.math.integrals.FourierIntegration;
import jquant.models.equity.PiecewiseTimeDependentHestonModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.time.Frequency;
import jquant.time.Date;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.close_enough;

/**
 * Analytic piecewise constant time dependent Heston-model engine.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/analyticptdhestonengine.{hpp,cpp}}.
 *
 * <p>References:
 * Heston, Steven L., 1993. A Closed-Form Solution for Options with
 * Stochastic Volatility with Applications to Bond and Currency Options.
 * The Review of Financial Studies, Volume 6, Issue 2, 327-343.
 *
 * <p>J. Gatheral, The Volatility Surface: A Practitioner's Guide,
 * Wiley Finance.
 *
 * <p>A. Elices, Models with time-dependent parameters using transform
 * methods: application to Heston's model,
 * http://arxiv.org/pdf/0708.2020
 */
public class AnalyticPTDHestonEngine implements PricingEngine {

    public enum ComplexLogFormula { Gatheral, AndersenPiterbarg }

    private final PiecewiseTimeDependentHestonModel model_;
    private int evaluations_;
    private final ComplexLogFormula cpxLog_;
    private final FourierIntegration integration_;
    private final double andersenPiterbargEpsilon_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    // Simple to use constructor: Using adaptive
    // Gauss-Lobatto integration and Gatheral's version of complex log.
    // Be aware: using a too large number for maxEvaluations might result
    // in a stack overflow as the Lobatto integration is a recursive
    // algorithm.
    public AnalyticPTDHestonEngine(
            PiecewiseTimeDependentHestonModel model,
            double relTolerance, int maxEvaluations) {
        this(model, ComplexLogFormula.Gatheral,
                FourierIntegration.gaussLobatto(
                        relTolerance, NULL_REAL, maxEvaluations, false),
                NULL_REAL);
    }

    // Constructor using Laguerre integration
    // and Gatheral's version of complex log.
    public AnalyticPTDHestonEngine(
            PiecewiseTimeDependentHestonModel model,
            int integrationOrder) {
        this(model, ComplexLogFormula.Gatheral,
                FourierIntegration.gaussLaguerre(integrationOrder),
                NULL_REAL);
    }

    public AnalyticPTDHestonEngine(PiecewiseTimeDependentHestonModel model) {
        this(model, 144);
    }

    // Constructor giving full control over Fourier integration algorithm
    public AnalyticPTDHestonEngine(
            PiecewiseTimeDependentHestonModel model,
            ComplexLogFormula cpxLog,
            FourierIntegration itg) {
        this(model, cpxLog, itg, 1e-8);
    }

    public AnalyticPTDHestonEngine(
            PiecewiseTimeDependentHestonModel model,
            ComplexLogFormula cpxLog,
            FourierIntegration itg,
            double andersenPiterbargEpsilon) {
        model_ = model;
        evaluations_ = 0;
        cpxLog_ = cpxLog;
        integration_ = itg;
        andersenPiterbargEpsilon_ = andersenPiterbargEpsilon;

        results_.additionalResults = new HashMap<>();
        model_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                notifyObservers();
            }
        });
    }

    @Override
    public Option.arguments getArguments() {
        return arguments_;
    }

    @Override
    public EuropeanOption.results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.value = Double.NaN;
        results_.errorEstimate = Double.NaN;
        results_.valuationDate = new Date();
        results_.delta = Double.NaN;
        results_.gamma = Double.NaN;
        results_.theta = Double.NaN;
        results_.thetaPerDay = Double.NaN;
        results_.vega = Double.NaN;
        results_.rho = Double.NaN;
        results_.dividendRho = Double.NaN;
        results_.elasticity = Double.NaN;
        results_.strikeSensitivity = Double.NaN;
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        // this is an european option pricer
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        // plain vanilla
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-striked payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        final double v0 = model_.v0();
        final double spotPrice = model_.s0();
        QL_REQUIRE(spotPrice > 0.0, "negative or null underlying given");

        final double strike = payoff.strike();
        final double term
                = model_.riskFreeRate().getValue().dayCounter().yearFraction(
                model_.riskFreeRate().getValue().referenceDate(),
                arguments_.exercise.lastDate());

        QL_REQUIRE(term < model_.timeGrid().back()
                        || close_enough(term, model_.timeGrid().back()),
                "maturity (" + term + ") is too large, time grid is bounded by "
                        + model_.timeGrid().back());

        final double riskFreeDiscount = model_.riskFreeRate().getValue().discount(
                arguments_.exercise.lastDate());
        final double dividendDiscount = model_.dividendYield().getValue().discount(
                arguments_.exercise.lastDate());

        // average values
        final TimeGrid timeGrid = model_.timeGrid();
        QL_REQUIRE(timeGrid.size() > 1, "at least two model points needed");

        final int n = timeGrid.size() - 1;
        double kappaAvg = 0.0, thetaAvg = 0.0, sigmaAvg = 0.0, rhoAvg = 0.0;

        for (int i = 1; i <= n; ++i) {
            final double t = 0.5 * (timeGrid.get(i - 1) + timeGrid.get(i));
            kappaAvg += model_.kappa(t);
            thetaAvg += model_.theta(t);
            sigmaAvg += model_.sigma(t);
            rhoAvg += model_.rho(t);
        }
        kappaAvg /= n;
        thetaAvg /= n;
        sigmaAvg /= n;
        rhoAvg /= n;

        evaluations_ = 0;

        switch (cpxLog_) {
            case Gatheral: {
                final double c_inf = Math.min(0.2, Math.max(0.0001,
                        Math.sqrt(1.0 - rhoAvg * rhoAvg) / sigmaAvg))
                        * (v0 + kappaAvg * thetaAvg * term);

                final double p1 = integration_.calculate(c_inf,
                        new Fj_Helper(model_, term, strike, 1)) / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                final double p2 = integration_.calculate(c_inf,
                        new Fj_Helper(model_, term, strike, 2)) / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                switch (payoff.optionType()) {
                    case Call:
                        results_.value = spotPrice * dividendDiscount * (p1 + 0.5)
                                - strike * riskFreeDiscount * (p2 + 0.5);
                        break;
                    case Put:
                        results_.value = spotPrice * dividendDiscount * (p1 - 0.5)
                                - strike * riskFreeDiscount * (p2 - 0.5);
                        break;
                    default:
                        QL_FAIL("unknown option type");
                }
            }
            break;
            case AndersenPiterbarg: {
                QL_REQUIRE(term <= timeGrid.back(),
                        "maturity (" + term + ") is too large, "
                                + "time grid is bounded by " + timeGrid.back());

                final double t05 = 0.5 * timeGrid.at(1);

                final Complex D_u_inf
                        = new Complex(
                        Math.sqrt(1.0 - model_.rho(t05) * model_.rho(t05)),
                        model_.rho(t05)).negate()
                        .divide(model_.sigma(t05));

                final int lastI = lowerBound(timeGrid, term);

                Complex C_u_inf = new Complex(0.0, 0.0);
                for (int i = 0; i < lastI; ++i) {
                    final double begin = timeGrid.get(i);
                    final double end = Math.min(term, timeGrid.get(i + 1));
                    final double tau = end - begin;
                    final double t = 0.5 * (end + begin);

                    final double kappa = model_.kappa(t);
                    final double theta = model_.theta(t);
                    final double sigma = model_.sigma(t);
                    final double rho = model_.rho(t);

                    C_u_inf = C_u_inf.add(
                            new Complex(Math.sqrt(1 - rho * rho), rho)
                                    .multiply(-kappa * theta * tau / sigma));
                }

                final double ratio = riskFreeDiscount / dividendDiscount;

                final double fwdPrice = spotPrice / ratio;

                final double epsilon = andersenPiterbargEpsilon_
                        * M_PI / (Math.sqrt(strike * fwdPrice) * riskFreeDiscount);

                final double c_inf = -C_u_inf.add(D_u_inf.multiply(v0)).getReal();

                final DoubleSupplier uM = () ->
                        FourierIntegration.andersenPiterbargIntegrationLimit(
                                c_inf, epsilon, v0, term);

                final double vAvg
                        = (1 - Math.exp(-kappaAvg * term)) * (v0 - thetaAvg)
                        / (kappaAvg * term) + thetaAvg;

                final double bsPrice
                        = new BlackCalculator(Option.Type.Call, strike,
                        fwdPrice, Math.sqrt(vAvg * term),
                        riskFreeDiscount).value();

                final double h_cv = integration_.calculate(c_inf,
                        new AP_Helper(term, spotPrice, strike,
                                ratio, Math.sqrt(vAvg), this), uM, 1.0)
                        * Math.sqrt(strike * fwdPrice) * riskFreeDiscount / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                switch (payoff.optionType()) {
                    case Call:
                        results_.value = bsPrice + h_cv;
                        break;
                    case Put:
                        results_.value = bsPrice + h_cv
                                - riskFreeDiscount * (fwdPrice - strike);
                        break;
                    default:
                        QL_FAIL("unknown option type");
                }
            }
            break;

            default:
                QL_FAIL("unknown complex log formula");
        }
    }

    public int numberOfEvaluations() {
        return evaluations_;
    }

    // normalized characteristic function
    public Complex chF(final Complex z, double t) {
        return lnChF(z, t).exp();
    }

    // log of the normalized characteristic function
    public Complex lnChF(final Complex z, double T) {
        final double v0 = model_.v0();

        Complex D = new Complex(0.0, 0.0);
        Complex C = new Complex(0.0, 0.0);

        final TimeGrid timeGrid = model_.timeGrid();
        final double lastModelTime = timeGrid.back();

        QL_REQUIRE(T <= lastModelTime,
                "maturity (" + T + ") is too large, "
                        + "time grid is bounded by " + lastModelTime);

        final int lastI = lowerBound(timeGrid, T);

        for (int i = lastI - 1; i >= 0; --i) {
            final double begin = timeGrid.get(i);
            final double end = Math.min(T, timeGrid.get(i + 1));
            final double tau = end - begin;

            final double t = 0.5 * (end + begin);
            final double kappa = model_.kappa(t);
            final double sigma = model_.sigma(t);
            final double theta = model_.theta(t);
            final double rho = model_.rho(t);

            final double sigma2 = sigma * sigma;

            final Complex k
                    = new Complex(kappa, 0.0).add(
                    new Complex(rho * sigma * z.getImaginary(),
                            -rho * sigma * z.getReal()));

            final Complex d = k.multiply(k)
                    .add(z.multiply(z)
                            .add(new Complex(-z.getImaginary(), z.getReal()))
                            .multiply(sigma2))
                    .sqrt();

            final Complex g = k.subtract(d).divide(k.add(d));

            final Complex gt = k.subtract(d).subtract(D.multiply(sigma2))
                    .divide(k.add(d).subtract(D.multiply(sigma2)));

            C = C.add(
                    new Complex(kappa * theta / sigma2, 0.0)
                            .multiply(k.subtract(d).multiply(tau)
                                    .subtract(new Complex(1.0, 0.0).subtract(
                                                    gt.multiply(d.multiply(-tau).exp()))
                                            .divide(new Complex(1.0, 0.0).subtract(gt))
                                            .log()
                                            .multiply(2.0))));

            D = k.add(d).divide(sigma2)
                    .multiply(g.subtract(gt.multiply(d.multiply(-tau).exp())))
                    .divide(new Complex(1.0, 0.0)
                            .subtract(gt.multiply(d.multiply(-tau).exp())));
        }

        return D.multiply(v0).add(C);
    }

    // first index i with timeGrid[i] >= t (std::lower_bound)
    private static int lowerBound(TimeGrid grid, double t) {
        int low = 0;
        int high = grid.size();
        while (low < high) {
            final int mid = (low + high) / 2;
            if (grid.get(mid) < t) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    // helper class for integration
    private static class Fj_Helper implements DoubleUnaryOperator {
        private final int j_;
        private final double term_;
        private final double v0_, x_, sx_;

        private final double[] r_, q_;
        private final PiecewiseTimeDependentHestonModel model_;

        private final TimeGrid timeGrid_;

        Fj_Helper(PiecewiseTimeDependentHestonModel model,
                  double term, double strike, int j) {
            j_ = j;
            term_ = term;

            v0_ = model.v0();
            x_ = Math.log(model.s0());
            sx_ = Math.log(strike);
            r_ = new double[model.timeGrid().size() - 1];
            q_ = new double[model.timeGrid().size() - 1];
            model_ = model;
            timeGrid_ = model.timeGrid();

            for (int i = 0; i < timeGrid_.size() - 1; ++i) {
                final double begin = Math.min(term_, timeGrid_.get(i));
                final double end = Math.min(term_, timeGrid_.get(i + 1));
                r_[i] = model.riskFreeRate().getValue()
                        .forwardRate(begin, end, Compounding.Continuous,
                                Frequency.NO_FREQUENCY, false).rate();
                q_[i] = model.dividendYield().getValue()
                        .forwardRate(begin, end, Compounding.Continuous,
                                Frequency.NO_FREQUENCY, false).rate();
            }
        }

        @Override
        public double applyAsDouble(double phi) {

            // avoid numeric overflow for phi->0.
            // todo: use l'Hospital's rule use to get lim_{phi->0}
            phi = Math.max((double) Math.ulp(1.0f), phi);

            Complex D = new Complex(0.0, 0.0);
            Complex C = new Complex(0.0, 0.0);

            for (int i = timeGrid_.size() - 1; i > 0; --i) {
                final double begin = timeGrid_.get(i - 1);
                if (begin < term_) {
                    final double end = Math.min(term_, timeGrid_.get(i));
                    final double tau = end - begin;
                    final double t = 0.5 * (end + begin);

                    final double rho = model_.rho(t);
                    final double sigma = model_.sigma(t);
                    final double kappa = model_.kappa(t);
                    final double theta = model_.theta(t);

                    final double sigma2 = sigma * sigma;
                    final double t0 = kappa - ((j_ == 1) ? (rho * sigma) : 0.0);
                    final double rpsig = rho * sigma * phi;

                    final Complex t1 = new Complex(t0, -rpsig);
                    final Complex d = t1.multiply(t1)
                            .subtract(new Complex(-phi, (j_ == 1) ? 1.0 : -1.0)
                                    .multiply(sigma2 * phi))
                            .sqrt();
                    final Complex g = t1.subtract(d).divide(t1.add(d));
                    final Complex gt = t1.subtract(d).subtract(D.multiply(sigma2))
                            .divide(t1.add(d).subtract(D.multiply(sigma2)));

                    D = t1.add(d).divide(sigma2)
                            .multiply(g.subtract(gt.multiply(d.multiply(-tau).exp())))
                            .divide(new Complex(1.0, 0.0)
                                    .subtract(gt.multiply(d.multiply(-tau).exp())));

                    final Complex lng
                            = new Complex(1.0, 0.0)
                            .subtract(gt.multiply(d.multiply(-tau).exp()))
                            .divide(new Complex(1.0, 0.0).subtract(gt))
                            .log();

                    C = new Complex(kappa * theta / sigma2, 0.0)
                            .multiply(t1.subtract(d).multiply(tau)
                                    .subtract(lng.multiply(2.0)))
                            .add(new Complex(0.0, phi * (r_[i - 1] - q_[i - 1]) * tau))
                            .add(C);
                }
            }
            return D.multiply(v0_).add(C)
                    .add(new Complex(0.0, phi * (x_ - sx_)))
                    .exp().getImaginary() / phi;
        }
    }

    private static class AP_Helper implements DoubleUnaryOperator {
        private final double term_;
        private final double sigmaBS_;
        private final double x_, sx_, dd_;
        private final AnalyticPTDHestonEngine enginePtr_;

        AP_Helper(double term, double s0, double strike, double ratio,
                  double sigmaBS,
                  AnalyticPTDHestonEngine enginePtr) {
            term_ = term;
            sigmaBS_ = sigmaBS;
            x_ = Math.log(s0);
            sx_ = Math.log(strike);
            dd_ = x_ - Math.log(ratio);
            enginePtr_ = enginePtr;

            QL_REQUIRE(enginePtr != null, "pricing engine required");
        }

        @Override
        public double applyAsDouble(double u) {
            final Complex z = new Complex(u, -0.5);

            final Complex phiBS
                    = new Complex(-0.5 * sigmaBS_ * sigmaBS_ * term_, 0.0)
                    .multiply(z.multiply(z)
                            .add(new Complex(-z.getImaginary(), z.getReal())))
                    .exp();

            return new Complex(0.0, u * (dd_ - sx_)).exp()
                    .multiply(phiBS.subtract(enginePtr_.chF(z, term_)))
                    .divide(u * u + 0.25).getReal();
        }
    }

    @Override
    public void registerObserver(Observer observer) {
        observers_.add(observer);
    }

    @Override
    public void unregisterObserver(Observer observer) {
        observers_.remove(observer);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_)) {
                observer.update();
            }
        }
    }
}
