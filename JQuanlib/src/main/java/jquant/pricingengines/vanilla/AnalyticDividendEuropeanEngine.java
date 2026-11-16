package jquant.pricingengines.vanilla;

import jquant.Compounding;
import jquant.Exercise;
import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic pricing engine for European options with discrete dividends.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/vanilla/analyticdividendeuropeanengine.cpp
 */
public class AnalyticDividendEuropeanEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final List<? extends Dividend> dividends_;
    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public AnalyticDividendEuropeanEngine(GeneralizedBlackScholesProcess process,
                                          List<? extends Dividend> dividends) {
        process_ = process;
        dividends_ = dividends;
        results_.additionalResults = new HashMap<>();
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

    public GeneralizedBlackScholesProcess process() {
        return process_;
    }

    public List<? extends Dividend> dividends() {
        return dividends_;
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
        Date exerciseDate = arguments_.exercise.lastDate();

        YieldTermStructure riskFree = process_.riskFreeRate().getValue();
        YieldTermStructure dividendYield = process_.dividendYield().getValue();

        Date settlementDate = riskFree.referenceDate();
        double riskless = 0.0;
        for (Dividend dividend : dividends_) {
            Date cashFlowDate = dividend.date();
            if (TimeUtils.geq(cashFlowDate, settlementDate)
                    && TimeUtils.leq(cashFlowDate, exerciseDate)) {
                riskless += dividend.amount()
                        * riskFree.discount(cashFlowDate, false)
                        / dividendYield.discount(cashFlowDate, false);
            }
        }

        double spot = process_.stateVariable().getValue().value() - riskless;
        QL_REQUIRE(spot > 0.0,
                "negative or null underlying after subtracting dividends");

        double dividendDiscount = dividendYield.discount(exerciseDate, false);
        double riskFreeDiscount = riskFree.discount(exerciseDate, false);
        double forwardPrice = spot * dividendDiscount / riskFreeDiscount;

        double variance = process_.blackVolatility().getValue()
                .blackVariance(exerciseDate, payoff.strike(), false);

        BlackCalculator black = new BlackCalculator(
                payoff, forwardPrice, Math.sqrt(variance), riskFreeDiscount);

        results_.value = black.value();
        results_.delta = black.delta(spot);
        results_.gamma = black.gamma(spot);

        DayCounter rfdc = riskFree.dayCounter();
        DayCounter dydc = dividendYield.dayCounter();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        double t = voldc.yearFraction(
                process_.blackVolatility().getValue().referenceDate(),
                exerciseDate, new Date(), new Date());
        results_.vega = black.vega(t);

        double deltaTheta = 0.0, deltaRho = 0.0;
        for (Dividend dividend : dividends_) {
            Date d = dividend.date();
            if (TimeUtils.geq(d, settlementDate)
                    && TimeUtils.leq(d, exerciseDate)) {
                deltaTheta -= dividend.amount()
                        * (riskFree.zeroRate(d, rfdc, Compounding.Continuous,
                                Frequency.ANNUAL, false).rate()
                        - dividendYield.zeroRate(d, dydc, Compounding.Continuous,
                                Frequency.ANNUAL, false).rate())
                        * riskFree.discount(d, false)
                        / dividendYield.discount(d, false);

                double tDividend = process_.time(d);
                deltaRho += dividend.amount() * tDividend
                        * riskFree.discount(tDividend, false)
                        / dividendYield.discount(tDividend, false);
            }
        }
        t = process_.time(exerciseDate);
        try {
            results_.theta = black.theta(spot, t)
                    + deltaTheta * black.delta(spot);
        } catch (RuntimeException e) {
            results_.theta = Double.NaN;
        }

        results_.rho = black.rho(t)
                + deltaRho * black.delta(spot);
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
