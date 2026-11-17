package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

/**
 * Base class for the QD+ and QD fixed-point American engines.
 *
 * <p>Ported from {@code detail::QdPutCallParityEngine} in
 * {@code ql/pricingengines/vanilla/qdplusamericanengine.cpp}. Puts are priced
 * directly; calls are priced through put-call parity by swapping spot and
 * strike.
 */
abstract class QdPutCallParityEngine implements PricingEngine {
    protected final GeneralizedBlackScholesProcess process_;
    protected final Option.arguments arguments_ = new Option.arguments();
    protected EuropeanOption.results results_ = newResults();
    private final Set<Observer> observers_ = new HashSet<>();

    QdPutCallParityEngine(GeneralizedBlackScholesProcess process) {
        process_ = process;
        if (process_ != null) {
            process_.registerObserver(new Observer() {
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
    }

    protected abstract double calculatePut(double S, double K, double r, double q,
                                           double vol, double T);

    @Override
    public void calculate() {
        results_ = newResults();

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.American,
                "not an American option");

        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        final double spot = process_.x0();
        QL_REQUIRE(spot >= 0.0, "negative underlying given");

        final Date maturity = arguments_.exercise.lastDate();
        final double T = process_.time(maturity);
        final double S = spot;
        final double K = payoff.strike();
        // Extrapolation is enabled explicitly: QuantLib term structures
        // allow it by default, and the official engine also queries the
        // curves with zero strikes and very long maturities.
        final double r = -Math.log(process_.riskFreeRate().getValue()
                .discount(maturity, true)) / T;
        final double q = -Math.log(process_.dividendYield().getValue()
                .discount(maturity, true)) / T;
        final double vol = process_.blackVolatility().getValue()
                .blackVol(T, K, true);

        QL_REQUIRE(S >= 0, "zero or positive underlying value is required");
        QL_REQUIRE(K >= 0, "zero or positive strike is required");
        QL_REQUIRE(vol >= 0, "zero or positive volatility is required");

        if (payoff.optionType() == Option.Type.Put) {
            results_.value = calculatePutWithEdgeCases(S, K, r, q, vol, T);
        } else if (payoff.optionType() == Option.Type.Call) {
            results_.value = calculatePutWithEdgeCases(K, S, q, r, vol, T);
        } else {
            QL_FAIL("unknown option type");
        }

        // the holder can always exercise, so the value is at least the payoff
        results_.value = Math.max(results_.value, payoff.value(S));
    }

    private double calculatePutWithEdgeCases(double S, double K, double r,
                                             double q, double vol, double T) {
        if (close(K, 0.0)) {
            return 0.0;
        }

        if (close(S, 0.0)) {
            return Math.max(K, K * Math.exp(-r * T));
        }

        if (r <= 0.0 && r <= q) {
            return Math.max(0.0, new BlackCalculator(Option.Type.Put, K,
                    S * Math.exp((r - q) * T), vol * Math.sqrt(T),
                    Math.exp(-r * T)).value());
        }

        if (close(vol, 0.0)) {
            java.util.function.DoubleUnaryOperator intrinsic =
                    t -> Math.max(0.0,
                            K * Math.exp(-r * t) - S * Math.exp(-q * t));
            final double npv0 = intrinsic.applyAsDouble(0.0);
            final double npvT = intrinsic.applyAsDouble(T);
            final double extremT = jquant.math.MathUtils.close_enough(r, q)
                    ? jquant.math.MathUtils.QL_MAX_REAL
                    : Math.log(r * K / (q * S)) / (r - q);

            if (extremT > 0.0 && extremT < T) {
                return Math.max(npv0, Math.max(npvT,
                        intrinsic.applyAsDouble(extremT)));
            } else {
                return Math.max(npv0, npvT);
            }
        }

        return calculatePut(S, K, r, q, vol, T);
    }

    @Override
    public arguments getArguments() {
        return arguments_;
    }

    @Override
    public results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_ = newResults();
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

    private static EuropeanOption.results newResults() {
        EuropeanOption.results results = new EuropeanOption.results();
        results.delta = Double.NaN;
        results.gamma = Double.NaN;
        results.theta = Double.NaN;
        results.thetaPerDay = Double.NaN;
        results.vega = Double.NaN;
        results.rho = Double.NaN;
        results.dividendRho = Double.NaN;
        results.elasticity = Double.NaN;
        results.strikeSensitivity = Double.NaN;
        results.additionalResults = new HashMap<>();
        return results;
    }
}
