package jquant.pricingengines;

import jquant.Handle;
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
 * flows, via {@link CashFlows#npv}.
 *
 * <p>The engine stores a {@link Handle} to the discount curve and observes the
 * Handle's Link, as official does. Relinking the same Handle (for example a
 * {@link jquant.RelinkableHandle} shared with the caller, or any copy of that
 * same Link) notifies the engine, which notifies the bond; no manual
 * {@code setPricingEngine} or extra recalculation is needed. The engine does
 * not subscribe directly to the curve instance held by the Link: the Link
 * already unregisters the previous curve and registers the new one, so a
 * direct subscription would either miss relinks or double-notify.
 *
 * <p>Java's {@code Handle} has no copy constructor that shares the {@code Link}.
 * Only the <em>same</em> {@code Handle}/{@code RelinkableHandle} instance is
 * shared between caller and engine; {@code new Handle<>(curve)} always creates
 * a new Link pointing at that curve. Accordingly, the bare-curve constructors
 * wrap the supplied curve in a new Handle and do not follow a later
 * {@code linkTo} on some other handle. Quote changes on that same curve
 * instance still notify through the new Handle's Link.
 *
 * <p>An empty Handle is legal at construction and is diagnosed in
 * {@link #calculate()} with the official message; a null Handle object is
 * rejected.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/bond/discountingbondengine.cpp
 */
public class DiscountingBondEngine implements PricingEngine {
    private final Handle<YieldTermStructure> discountCurve_;
    /** Official {@code std::optional<bool>}; {@code null} means use Settings. */
    private final Boolean includeSettlementDateFlows_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingBondEngine(YieldTermStructure discountCurve) {
        this(discountCurve, null);
    }

    /**
     * Compatibility constructor. The curve is wrapped in a <b>new</b>
     * {@code Handle} with {@code registerAsObserver=true}, matching the
     * previous bare-curve behaviour for quote changes on this curve instance.
     * It is not a copy of any caller's RelinkableHandle link, so relinking a
     * different handle will not move this engine.
     */
    public DiscountingBondEngine(YieldTermStructure discountCurve,
                                 Boolean includeSettlementDateFlows) {
        this(wrapCurve(discountCurve), includeSettlementDateFlows);
    }

    public DiscountingBondEngine(Handle<YieldTermStructure> discountCurve) {
        this(discountCurve, null);
    }

    /**
     * Official-shaped constructor. The supplied Handle is stored as-is so its
     * existing Link is shared and observed; relinking that handle notifies
     * the engine. An empty Handle is accepted and checked in
     * {@link #calculate()}.
     */
    public DiscountingBondEngine(Handle<YieldTermStructure> discountCurve,
                                 Boolean includeSettlementDateFlows) {
        QL_REQUIRE(discountCurve != null, "null discounting term structure handle");
        discountCurve_ = discountCurve;
        includeSettlementDateFlows_ = includeSettlementDateFlows;
        results_.additionalResults = new HashMap<>();
        discountCurve_.get().registerObserver(new Observer() {
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

    private static Handle<YieldTermStructure> wrapCurve(YieldTermStructure discountCurve) {
        QL_REQUIRE(discountCurve != null, "null discount curve");
        return new Handle<>(discountCurve, true);
    }

    /** Official {@code discountCurve()}: the stored Handle (same Link). */
    public Handle<YieldTermStructure> discountCurve() {
        return discountCurve_;
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
        QL_REQUIRE(!discountCurve_.empty(),
                "discounting term structure handle is empty");

        YieldTermStructure curve = discountCurve_.currentLink();
        results_.valuationDate = curve.referenceDate();

        boolean includeRefDateFlows = includeSettlementDateFlows_ != null
                ? includeSettlementDateFlows_
                : Settings.instance.includeReferenceDateEvents();

        results_.value = CashFlows.npv(arguments_.cashflows,
                curve,
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
                    curve,
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
