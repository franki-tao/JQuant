package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.math.Function;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.integrals.SimpsonIntegral;
import jquant.models.shortrate.onefactormodels.Vasicek;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing of vanilla European options under a stochastic Vasicek interest-rate
 * model. The analytical solution follows
 * <a href="http://hsrm-mathematik.de/WS201516/master/option-pricing/Black-Scholes-Vasicek-Model.pdf">
 * Black-Scholes-Vasicek-Model</a>.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/vanilla/analyticeuropeanvasicekengine.cpp
 */
public class AnalyticBlackVasicekEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess blackProcess_;
    private final Vasicek vasicekProcess_;
    private final SimpsonIntegral simpsonIntegral_;
    private final double correlation_;
    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public AnalyticBlackVasicekEngine(GeneralizedBlackScholesProcess blackProcess,
                                      Vasicek vasicekProcess,
                                      double correlation) {
        blackProcess_ = blackProcess;
        vasicekProcess_ = vasicekProcess;
        simpsonIntegral_ = new SimpsonIntegral(1e-5, 1000);
        correlation_ = correlation;

        Observer observer = new Observer() {
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
        };
        blackProcess_.registerObserver(observer);
        vasicekProcess_.registerObserver(observer);
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
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        double t = 0;
        Date maturity = arguments_.exercise.lastDate();
        DayCounter rfdc = blackProcess_.riskFreeRate().getValue().dayCounter();
        double T = rfdc.yearFraction(
                blackProcess_.riskFreeRate().getValue().referenceDate(),
                maturity, new Date(), new Date());
        double kappa = vasicekProcess_.a();
        // spot net of the dividends paid until maturity
        double S_t = blackProcess_.x0()
                * blackProcess_.dividendYield().getValue().discount(maturity, false);
        double K = payoff.strike();
        double sigma_s = blackProcess_.blackVolatility().getValue().blackVol(maturity, K, false);
        double sigma_r = vasicekProcess_.sigma();
        double r_t = vasicekProcess_.r0();

        double zcb = vasicekProcess_.discountBond(t, T, r_t);
        double epsilon = payoff.optionType() == Option.Type.Call ? 1 : -1;
        double upsilon = simpsonIntegral_.value(
                integrand(sigma_s, sigma_r, correlation_, kappa, T), t, T);
        double d_positive = (Math.log((S_t / K) / zcb) + upsilon / 2) / Math.sqrt(upsilon);
        double d_negative = (Math.log((S_t / K) / zcb) - upsilon / 2) / Math.sqrt(upsilon);
        double n_d1 = f.value(epsilon * d_positive);
        double n_d2 = f.value(epsilon * d_negative);

        results_.value = epsilon * ((S_t * n_d1) - (zcb * K * n_d2));
        results_.valuationDate = blackProcess_.riskFreeRate().getValue().referenceDate();
        results_.errorEstimate = Double.NaN;
    }

    private static double g_k(double t, double kappa) {
        return (1 - Math.exp(-kappa * t)) / kappa;
    }

    private static Function integrand(double sigma_s, double sigma_r, double correlation,
                                      double kappa, double T) {
        return u -> {
            double g = g_k(T - u, kappa);
            return (sigma_s * sigma_s)
                    + (2 * correlation * sigma_s * sigma_r * g)
                    + (sigma_r * sigma_r * g * g);
        };
    }

    @Override
    public void registerObserver(Observer o) {
        observers_.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers_.remove(o);
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
