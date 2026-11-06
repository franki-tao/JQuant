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
 * Discounts each future cash flow on the curve and divides by the settlement
 * discount. Settlement-date flows are excluded.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/bond/discountingbondengine.cpp
 */
public class DiscountingBondEngine implements PricingEngine {
    private final YieldTermStructure discountCurve_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingBondEngine(YieldTermStructure discountCurve) {
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
        results_.settlementValue = Double.NaN;
        if (results_.additionalResults != null)
            results_.additionalResults.clear();
    }

    @Override
    public void calculate() {
        Date settlement = arguments_.settlementDate;
        double settlementDiscount = discountCurve_.discount(settlement, false);
        double npv = 0.0;
        for (CashFlow cashflow : arguments_.cashflows) {
            if (!cashflow.hasOccurred(settlement, Optional.of(false)))
                npv += cashflow.amount() * discountCurve_.discount(cashflow.date(), false)
                        / settlementDiscount;
        }
        results_.value = npv;
        results_.settlementValue = npv;
        results_.valuationDate = discountCurve_.referenceDate();
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
        public Date settlementDate;
        public List<CashFlow> cashflows;

        @Override
        public void validate() {
            QL_REQUIRE(settlementDate != null && jquant.time.TimeUtils.neq(settlementDate, new Date()),
                    "no settlement date provided");
            QL_REQUIRE(cashflows != null && !cashflows.isEmpty(), "no cash flow provided");
        }
    }

    public static class results extends Instrument.results {
        public double settlementValue = Double.NaN;
    }
}
