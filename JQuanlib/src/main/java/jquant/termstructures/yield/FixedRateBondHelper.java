package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.instruments.FixedRateBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstraps a discount curve from a fixed-rate bond's clean price. The pillar
 * is the redemption payment date, which is also the last coupon date, so one
 * pass of the log-linear bootstrap is enough.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bondhelpers.cpp
 */
public class FixedRateBondHelper extends RateHelper {
    private final FixedRateBond bond_;

    public FixedRateBondHelper(Handle<Quote> price,
                               int settlementDays,
                               double faceAmount,
                               Schedule schedule,
                               double[] coupons,
                               DayCounter accrualDayCounter,
                               BusinessDayConvention paymentConvention,
                               double redemption,
                               Date issueDate) {
        super(price);
        bond_ = new FixedRateBond(settlementDays, faceAmount, schedule, coupons,
                accrualDayCounter, paymentConvention, redemption, issueDate);
        Date last = bond_.cashflows().get(bond_.cashflows().size() - 1).date();
        pillarDate_ = last;
        maturityDate_ = last;
        latestRelevantDate_ = last;
        earliestDate_ = bond_.settlementDate();
        QL_REQUIRE(!TimeUtils.less(pillarDate_, latestRelevantDate_),
                "bond pillar is before the last cash flow");
    }

    public FixedRateBond bond() {
        return bond_;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
        bond_.setPricingEngine(new DiscountingBondEngine(termStructure));
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        bond_.recalculate();
        return bond_.cleanPrice();
    }
}
