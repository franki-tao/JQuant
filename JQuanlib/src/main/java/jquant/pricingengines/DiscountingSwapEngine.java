package jquant.pricingengines;

import jquant.CashFlow;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Discounts both swap legs on one curve. A payer NPV is floating minus fixed.
 * The fair rate is the floating present value divided by the fixed annuity,
 * so it does not depend on the rate stored on the fixed coupons.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/swap/discountingswapengine.cpp
 */
public class DiscountingSwapEngine implements PricingEngine {
    private final YieldTermStructure discountCurve_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingSwapEngine(YieldTermStructure discountCurve) {
        QL_REQUIRE(discountCurve != null, "null discount curve");
        discountCurve_ = discountCurve;
        results_.additionalResults = new HashMap<>();
        discountCurve_.registerObserver(new Observer() {
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
        results_.fairRate = Double.NaN;
        results_.fixedLegNpv = Double.NaN;
        results_.floatingLegNpv = Double.NaN;
        if (results_.additionalResults != null)
            results_.additionalResults.clear();
    }

    @Override
    public void calculate() {
        Date settlement = discountCurve_.referenceDate();
        double settlementDiscount = discountCurve_.discount(settlement, false);
        double annuity = 0.0;
        int n = arguments_.fixedPayDates.size();
        for (int i = 0; i < n; i++) {
            Date pay = arguments_.fixedPayDates.get(i);
            if (!jquant.CashFlow.hasOccurred(pay, settlement, Optional.of(false))) {
                double df = discountCurve_.discount(pay, false) / settlementDiscount;
                annuity += arguments_.nominal * arguments_.fixedYearFractions.get(i) * df;
            }
        }
        QL_REQUIRE(annuity != 0.0, "null fixed annuity");
        double floating = 0.0;
        for (CashFlow cashflow : arguments_.floatingLeg) {
            if (!cashflow.hasOccurred(settlement, Optional.of(false)))
                floating += cashflow.amount() * discountCurve_.discount(cashflow.date(), false)
                        / settlementDiscount;
        }
        double fixed = arguments_.fixedRate * annuity;
        results_.fairRate = floating / annuity;
        results_.fixedLegNpv = -arguments_.payerSign * fixed;
        results_.floatingLegNpv = arguments_.payerSign * floating;
        results_.value = arguments_.payerSign * (floating - fixed);
        results_.valuationDate = settlement;
        results_.errorEstimate = Double.NaN;
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
            for (Observer observer : Set.copyOf(observers_))
                observer.update();
        }
    }

    public static class arguments implements PricingEngine.arguments {
        public double fixedRate;
        public double nominal;
        public double payerSign;
        public List<Date> fixedPayDates;
        public List<Double> fixedYearFractions;
        public List<CashFlow> floatingLeg;

        @Override
        public void validate() {
            QL_REQUIRE(fixedPayDates != null && !fixedPayDates.isEmpty(), "no fixed leg");
            QL_REQUIRE(floatingLeg != null && !floatingLeg.isEmpty(), "no floating leg");
            QL_REQUIRE(fixedYearFractions != null && fixedYearFractions.size() == fixedPayDates.size(),
                    "fixed year fractions do not match the fixed dates");
        }
    }

    public static class results extends Instrument.results {
        public double fairRate = Double.NaN;
        public double fixedLegNpv = Double.NaN;
        public double floatingLegNpv = Double.NaN;
    }
}
