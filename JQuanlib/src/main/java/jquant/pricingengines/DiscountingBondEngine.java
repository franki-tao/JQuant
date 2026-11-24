package jquant.pricingengines;

import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.CashFlows;
import jquant.instruments.Bond;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official discounting bond engine. {@code results.value} is the NPV as of
 * the curve reference date; {@code results.settlementValue} is the NPV as of
 * the bond settlement date, always excluding settlement-date and ex-coupon
 * flows, via {@link CashFlows#npv}. The engine still takes a bare curve, not
 * a relinkable Handle.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/bond/discountingbondengine.cpp
 */
public class DiscountingBondEngine implements PricingEngine {
    private final YieldTermStructure discountCurve_;
    /** Official {@code std::optional<bool>}; {@code null} means use Settings. */
    private final Boolean includeSettlementDateFlows_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingBondEngine(YieldTermStructure discountCurve) {
        this(discountCurve, null);
    }

    public DiscountingBondEngine(YieldTermStructure discountCurve,
                                 Boolean includeSettlementDateFlows) {
        QL_REQUIRE(discountCurve != null, "null discount curve");
        discountCurve_ = discountCurve;
        includeSettlementDateFlows_ = includeSettlementDateFlows;
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
        results_.valuationDate = discountCurve_.referenceDate();

        boolean includeRefDateFlows = includeSettlementDateFlows_ != null
                ? includeSettlementDateFlows_
                : Settings.instance.includeReferenceDateEvents();

        results_.value = CashFlows.npv(arguments_.cashflows,
                discountCurve_,
                includeRefDateFlows,
                results_.valuationDate,
                results_.valuationDate);

        // Official: a bond's cashflow on settlement date is never taken into
        // account, so settlementValue may need a second NPV.
        if (!includeRefDateFlows
                && TimeUtils.equals(results_.valuationDate, arguments_.settlementDate)) {
            results_.settlementValue = results_.value;
        } else {
            results_.settlementValue = CashFlows.npv(arguments_.cashflows,
                    discountCurve_,
                    false,
                    arguments_.settlementDate,
                    arguments_.settlementDate);
        }
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

    public static class arguments extends Bond.arguments {
    }

    public static class results extends Bond.results {
    }
}
