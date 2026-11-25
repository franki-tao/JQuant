package jquant.instruments;

import jquant.CashFlow;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! interest-rate swap of arbitrary cash-flow legs
/*! The payer flag of each leg multiplies that leg's NPV. QuantLib
    {@code ql/instruments/swap.hpp}. VanillaSwap keeps its own engine.
*/
public class Swap extends Instrument {
    public enum Type {
        Receiver(-1),
        Payer(1);

        private final int sign;

        Type(int sign) {
            this.sign = sign;
        }

        public int sign() {
            return sign;
        }
    }

    protected final List<List<CashFlow>> legs_ = new ArrayList<>();
    protected final double[] payer_;
    protected double[] legNPV_;
    protected double[] legBPS_;
    protected double[] startDiscounts_;
    protected double[] endDiscounts_;
    protected double npvDateDiscount_;

    protected Swap(int legs) {
        payer_ = new double[legs];
        legNPV_ = new double[legs];
        legBPS_ = new double[legs];
        startDiscounts_ = new double[legs];
        endDiscounts_ = new double[legs];
        for (int i = 0; i < legs; i++)
            legs_.add(new ArrayList<>());
        additionalResults_ = new HashMap<>();
    }

    public Swap(List<CashFlow> firstLeg, List<CashFlow> secondLeg) {
        this(2);
        legs_.set(0, firstLeg);
        legs_.set(1, secondLeg);
        payer_[0] = -1.0;
        payer_[1] = 1.0;
        registerLegs();
    }

    protected void registerLegs() {
        for (List<CashFlow> leg : legs_) {
            for (CashFlow cashflow : leg)
                registerWith(cashflow);
        }
    }

    /** Clears cached leg and swap results so the next NPV reads current fixings and curves. */
    public void deepUpdate() {
        for (List<CashFlow> leg : legs_) {
            for (CashFlow cashflow : leg)
                cashflow.update();
        }
        calculated_ = false;
    }

    public int numberOfLegs() {
        return legs_.size();
    }

    public List<CashFlow> leg(int j) {
        QL_REQUIRE(j >= 0 && j < legs_.size(), "leg " + j + " doesn't exist");
        return legs_.get(j);
    }

    public double legNPV(int j) {
        calculate();
        QL_REQUIRE(j >= 0 && j < legNPV_.length && !Double.isNaN(legNPV_[j]), "result not available");
        return legNPV_[j];
    }

    public double legBPS(int j) {
        calculate();
        QL_REQUIRE(j >= 0 && j < legBPS_.length && !Double.isNaN(legBPS_[j]), "result not available");
        return legBPS_[j];
    }

    public double startDiscounts(int j) {
        QL_REQUIRE(j >= 0 && j < legs_.size(), "leg # " + j + " doesn't exist!");
        calculate();
        QL_REQUIRE(!Double.isNaN(startDiscounts_[j]), "result not available");
        return startDiscounts_[j];
    }

    public double endDiscounts(int j) {
        QL_REQUIRE(j >= 0 && j < legs_.size(), "leg # " + j + " doesn't exist!");
        calculate();
        QL_REQUIRE(!Double.isNaN(endDiscounts_[j]), "result not available");
        return endDiscounts_[j];
    }

    public double npvDateDiscount() {
        calculate();
        QL_REQUIRE(!Double.isNaN(npvDateDiscount_), "result not available");
        return npvDateDiscount_;
    }

    public Date startDate() {
        Date start = null;
        for (List<CashFlow> leg : legs_) {
            for (CashFlow cashflow : leg) {
                Date candidate = cashflow instanceof jquant.cashflows.Coupon coupon
                        ? coupon.accrualStartDate() : cashflow.date();
                if (start == null || TimeUtils.less(candidate, start))
                    start = candidate;
            }
        }
        return start;
    }

    public Date maturityDate() {
        Date maturity = null;
        for (List<CashFlow> leg : legs_) {
            for (CashFlow cashflow : leg) {
                if (maturity == null || TimeUtils.greater(cashflow.date(), maturity))
                    maturity = cashflow.date();
            }
        }
        return maturity;
    }

    @Override
    public boolean isExpired() {
        Date today = new Date();
        for (List<CashFlow> leg : legs_) {
            for (CashFlow cashflow : leg) {
                if (!cashflow.hasOccurred(today, Optional.empty()))
                    return false;
            }
        }
        return true;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        Arrays.fill(legBPS_, 0.0);
        Arrays.fill(legNPV_, 0.0);
        Arrays.fill(startDiscounts_, 0.0);
        Arrays.fill(endDiscounts_, 0.0);
        npvDateDiscount_ = 0.0;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        QL_REQUIRE(args instanceof DiscountingMultiLegSwapEngine.arguments, "wrong argument type");
        DiscountingMultiLegSwapEngine.arguments arguments = (DiscountingMultiLegSwapEngine.arguments) args;
        arguments.legs = legs_;
        arguments.payer = payer_;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        QL_REQUIRE(r instanceof DiscountingMultiLegSwapEngine.results, "wrong result type");
        DiscountingMultiLegSwapEngine.results results = (DiscountingMultiLegSwapEngine.results) r;

        if (results.legNPV != null && results.legNPV.length > 0) {
            QL_REQUIRE(results.legNPV.length == legNPV_.length,
                    "wrong number of leg NPV returned");
            legNPV_ = results.legNPV.clone();
        } else {
            Arrays.fill(legNPV_, Double.NaN);
        }

        if (results.legBPS != null && results.legBPS.length > 0) {
            QL_REQUIRE(results.legBPS.length == legBPS_.length,
                    "wrong number of leg BPS returned");
            legBPS_ = results.legBPS.clone();
        } else {
            Arrays.fill(legBPS_, Double.NaN);
        }

        if (results.startDiscounts != null && results.startDiscounts.length > 0) {
            QL_REQUIRE(results.startDiscounts.length == startDiscounts_.length,
                    "wrong number of leg start discounts returned");
            startDiscounts_ = results.startDiscounts.clone();
        } else {
            Arrays.fill(startDiscounts_, Double.NaN);
        }

        if (results.endDiscounts != null && results.endDiscounts.length > 0) {
            QL_REQUIRE(results.endDiscounts.length == endDiscounts_.length,
                    "wrong number of leg end discounts returned");
            endDiscounts_ = results.endDiscounts.clone();
        } else {
            Arrays.fill(endDiscounts_, Double.NaN);
        }

        npvDateDiscount_ = results.npvDateDiscount;
    }
}
