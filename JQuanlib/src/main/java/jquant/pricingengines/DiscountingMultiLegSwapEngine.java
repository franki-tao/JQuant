package jquant.pricingengines;

import jquant.CashFlow;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.cashflows.Coupon;
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
 * Discounts every cash-flow leg on one curve. Each leg NPV is multiplied by
 * its payer sign. BPS is one basis point times the discounted accrual annuity.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/pricingengines/swap/discountingswapengine.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/cashflows/cashflows.cpp
 */
public class DiscountingMultiLegSwapEngine implements PricingEngine {
    private static final double BASIS_POINT = 1.0e-4;
    private final YieldTermStructure discountCurve_;
    private final arguments arguments_ = new arguments();
    private final results results_ = new results();
    private final Set<Observer> observers_ = new HashSet<>();

    public DiscountingMultiLegSwapEngine(YieldTermStructure discountCurve) {
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
        results_.legNPV = null;
        results_.legBPS = null;
        if (results_.additionalResults != null)
            results_.additionalResults.clear();
    }

    @Override
    public void calculate() {
        Date settlement = discountCurve_.referenceDate();
        double settlementDiscount = discountCurve_.discount(settlement, false);
        int n = arguments_.legs.size();
        results_.legNPV = new double[n];
        results_.legBPS = new double[n];
        results_.value = 0.0;
        for (int i = 0; i < n; i++) {
            double npv = 0.0;
            double bps = 0.0;
            for (CashFlow cashflow : arguments_.legs.get(i)) {
                if (cashflow.hasOccurred(settlement, Optional.empty()) || cashflow.tradingExCoupon(settlement))
                    continue;
                double df = discountCurve_.discount(cashflow.date(), false) / settlementDiscount;
                npv += cashflow.amount() * df;
                if (cashflow instanceof Coupon coupon)
                    bps += coupon.nominal() * coupon.accrualPeriod() * df;
            }
            results_.legNPV[i] = arguments_.payer[i] * npv;
            results_.legBPS[i] = arguments_.payer[i] * BASIS_POINT * bps;
            results_.value += results_.legNPV[i];
        }
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
    }
}
