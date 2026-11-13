package jquant.pricingengines;

import jquant.CashFlow;
import jquant.Handle;
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
    private final Handle<YieldTermStructure> discountHandle_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingSwapEngine(YieldTermStructure discountCurve) {
        this(discountCurve, null);
    }

    /**
     * Handle overload. The curve is resolved at calculation time, so a later
     * link of a relinkable handle is seen by the engine (the official engine
     * takes a {@code Handle<YieldTermStructure>}).
     */
    public DiscountingSwapEngine(Handle<YieldTermStructure> discountCurve) {
        this(null, discountCurve);
        QL_REQUIRE(discountCurve != null, "null discounting term structure handle");
    }

    private DiscountingSwapEngine(YieldTermStructure discountCurve,
                                  Handle<YieldTermStructure> discountHandle) {
        QL_REQUIRE(discountCurve != null || discountHandle != null,
                "null discount curve");
        discountCurve_ = discountCurve;
        discountHandle_ = discountHandle;
        results_.additionalResults = new HashMap<>();
        Observable source = discountHandle != null ? discountHandle.get() : discountCurve;
        source.registerObserver(new Observer() {
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

    private YieldTermStructure discountCurve() {
        return discountHandle_ != null ? discountHandle_.currentLink() : discountCurve_;
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
        results_.fixedLegBPS = Double.NaN;
        results_.floatingLegBPS = Double.NaN;
        if (results_.additionalResults != null)
            results_.additionalResults.clear();
    }

    @Override
    public void calculate() {
        YieldTermStructure discount = discountCurve();
        Date settlement = discount.referenceDate();
        double settlementDiscount = discount.discount(settlement, false);
        double annuity = 0.0;
        int n = arguments_.fixedPayDates.size();
        for (int i = 0; i < n; i++) {
            Date pay = arguments_.fixedPayDates.get(i);
            if (!jquant.CashFlow.hasOccurred(pay, settlement, Optional.of(false))) {
                double df = discount.discount(pay, false) / settlementDiscount;
                annuity += arguments_.nominal * arguments_.fixedYearFractions.get(i) * df;
            }
        }
        QL_REQUIRE(annuity != 0.0, "null fixed annuity");
        double floating = 0.0;
        double floatingAnnuity = 0.0;
        for (int i = 0; i < arguments_.floatingLeg.size(); i++) {
            CashFlow cashflow = arguments_.floatingLeg.get(i);
            if (!cashflow.hasOccurred(settlement, Optional.of(false))) {
                double df = discount.discount(cashflow.date(), false) / settlementDiscount;
                floating += cashflow.amount() * df;
                if (arguments_.floatingNominalAccruals != null
                        && i < arguments_.floatingNominalAccruals.size())
                    floatingAnnuity += arguments_.floatingNominalAccruals.get(i) * df;
            }
        }
        double fixed = arguments_.fixedRate * annuity;
        double basisPoint = 1.0e-4;
        results_.fairRate = floating / annuity;
        results_.fixedLegNpv = -arguments_.payerSign * fixed;
        results_.floatingLegNpv = arguments_.payerSign * floating;
        results_.fixedLegBPS = -arguments_.payerSign * basisPoint * annuity;
        results_.floatingLegBPS = arguments_.payerSign * basisPoint * floatingAnnuity;
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
        /** Nominal times accrual period, one entry per floating cash flow. */
        public List<Double> floatingNominalAccruals;

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
        public double fixedLegBPS = Double.NaN;
        public double floatingLegBPS = Double.NaN;
    }
}
