package jquant.pricingengines;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.CashFlows;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Discounts every cash-flow leg on one curve. Each leg NPV is multiplied by
 * its payer sign. BPS is one basis point times the discounted accrual annuity.
 *
 * <p>This is the Java home of the official generic
 * {@code DiscountingSwapEngine}: the optional settlement date, NPV date and
 * {@code includeReferenceDateEvents} flag, the NPV-date discount, and the
 * per-leg start/end discounts follow
 * {@code ql/pricingengines/swap/discountingswapengine.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/pricingengines/swap/discountingswapengine.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/cashflows/cashflows.cpp
 */
public class DiscountingMultiLegSwapEngine implements PricingEngine {
    private final YieldTermStructure discountCurve_;
    private final Handle<YieldTermStructure> discountHandle_;
    private final Boolean includeSettlementDateFlows_;
    private final Date settlementDate_;
    private final Date npvDate_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingMultiLegSwapEngine(YieldTermStructure discountCurve) {
        this(discountCurve, null, null, null, null);
    }

    /**
     * Handle overload. The curve is resolved at calculation time, so a later
     * link of a relinkable handle is seen by the engine.
     */
    public DiscountingMultiLegSwapEngine(Handle<YieldTermStructure> discountCurve) {
        this(null, discountCurve, null, null, null);
        QL_REQUIRE(discountCurve != null, "null discounting term structure handle");
    }

    /**
     * Official four-argument constructor with a curve reference, an optional
     * {@code includeSettlementDateFlows} flag, and optional settlement and NPV
     * dates (empty dates default to the curve reference date).
     */
    public DiscountingMultiLegSwapEngine(YieldTermStructure discountCurve,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate,
                                         Date npvDate) {
        this(discountCurve, null, includeSettlementDateFlows, settlementDate, npvDate);
    }

    /** Handle variant of the official four-argument constructor. */
    public DiscountingMultiLegSwapEngine(Handle<YieldTermStructure> discountCurve,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate,
                                         Date npvDate) {
        this(null, discountCurve, includeSettlementDateFlows, settlementDate, npvDate);
        QL_REQUIRE(discountCurve != null, "null discounting term structure handle");
    }

    private DiscountingMultiLegSwapEngine(YieldTermStructure discountCurve,
                                          Handle<YieldTermStructure> discountHandle,
                                          Boolean includeSettlementDateFlows,
                                          Date settlementDate,
                                          Date npvDate) {
        QL_REQUIRE(discountCurve != null || discountHandle != null,
                "null discount curve");
        discountCurve_ = discountCurve;
        discountHandle_ = discountHandle;
        includeSettlementDateFlows_ = includeSettlementDateFlows;
        // Date is mutable in Java; store owned copies like the official value
        // members.
        settlementDate_ = settlementDate == null ? new Date() : settlementDate.copy();
        npvDate_ = npvDate == null ? new Date() : npvDate.copy();
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
        results_.legNPV = null;
        results_.legBPS = null;
        results_.startDiscounts = null;
        results_.endDiscounts = null;
        results_.npvDateDiscount = Double.NaN;
        if (results_.additionalResults != null)
            results_.additionalResults.clear();
    }

    @Override
    public void calculate() {
        QL_REQUIRE(discountHandle_ == null || !discountHandle_.empty(),
                "discounting term structure handle is empty");
        YieldTermStructure discount = discountCurve();

        Date refDate = discount.referenceDate();

        Date settlementDate;
        if (TimeUtils.equals(settlementDate_, new Date())) {
            settlementDate = refDate;
        } else {
            settlementDate = settlementDate_;
            QL_REQUIRE(TimeUtils.geq(settlementDate, refDate),
                    "settlement date (" + settlementDate + ") before "
                            + "discount curve reference date (" + refDate + ")");
        }

        Date npvDate;
        if (TimeUtils.equals(npvDate_, new Date())) {
            npvDate = refDate;
        } else {
            npvDate = npvDate_;
            QL_REQUIRE(TimeUtils.geq(npvDate, refDate),
                    "npv date (" + npvDate + ") before "
                            + "discount curve reference date (" + refDate + ")");
        }
        results_.valuationDate = npvDate.copy();
        results_.npvDateDiscount = discount.discount(npvDate);

        int n = arguments_.legs.size();
        results_.legNPV = new double[n];
        results_.legBPS = new double[n];
        results_.startDiscounts = new double[n];
        results_.endDiscounts = new double[n];
        results_.value = 0.0;

        boolean includeRefDateFlows = includeSettlementDateFlows_ != null
                ? includeSettlementDateFlows_
                : Settings.instance.includeReferenceDateEvents();

        for (int i = 0; i < n; i++) {
            List<CashFlow> leg = arguments_.legs.get(i);
            try {
                CashFlows.NpvBps npvbps = CashFlows.npvbps(leg, discount,
                        includeRefDateFlows, settlementDate, npvDate);
                results_.legNPV[i] = arguments_.payer[i] * npvbps.npv();
                results_.legBPS[i] = arguments_.payer[i] * npvbps.bps();

                if (!leg.isEmpty()) {
                    Date d1 = CashFlows.startDate(leg);
                    if (TimeUtils.geq(d1, refDate))
                        results_.startDiscounts[i] = discount.discount(d1);
                    else
                        results_.startDiscounts[i] = Double.NaN;

                    Date d2 = CashFlows.maturityDate(leg);
                    if (TimeUtils.geq(d2, refDate))
                        results_.endDiscounts[i] = discount.discount(d2);
                    else
                        results_.endDiscounts[i] = Double.NaN;
                } else {
                    results_.startDiscounts[i] = Double.NaN;
                    results_.endDiscounts[i] = Double.NaN;
                }
            } catch (RuntimeException e) {
                QL_FAIL(ordinal(i + 1) + " leg: " + e.getMessage());
            }
            results_.value += results_.legNPV[i];
        }
        results_.errorEstimate = Double.NaN;
    }

    /** QuantLib {@code io::ordinal}. */
    private static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13)
            return n + "th";
        switch (n % 10) {
            case 1:
                return n + "st";
            case 2:
                return n + "nd";
            case 3:
                return n + "rd";
            default:
                return n + "th";
        }
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
        if (!ObservableSettings.getInstance().isUpdatesEnabled())
            ObservableSettings.getInstance().registerDeferred(observers_);
        else {
            for (Observer observer : Set.copyOf(observers_))
                observer.update();
        }
    }

    public static class arguments implements PricingEngine.arguments {
        public List<List<CashFlow>> legs;
        public double[] payer;

        @Override
        public void validate() {
            QL_REQUIRE(legs != null && !legs.isEmpty(), "no legs");
            QL_REQUIRE(payer != null && payer.length == legs.size(), "payer flags do not match the legs");
            for (List<CashFlow> leg : legs)
                QL_REQUIRE(leg != null && !leg.isEmpty(), "empty leg");
        }
    }

    public static class results extends Instrument.results {
        public double[] legNPV;
        public double[] legBPS;
        public double[] startDiscounts;
        public double[] endDiscounts;
        /** Official default is {@code Null<DiscountFactor>}. */
        public double npvDateDiscount = Double.NaN;
    }
}
