package jquant.instruments;

import jquant.CashFlow;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.Coupon;
import jquant.cashflows.FixedRateCoupon;
import jquant.cashflows.SimpleCashFlow;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Fixed-coupon bond. Coupons accrue on the schedule dates and pay on the
 * adjusted date. The redemption is added on the last coupon payment date, so
 * the pillar and the latest relevant date are the same cash-flow date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/fixedratebond.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bond.cpp
 */
public class FixedRateBond extends Instrument {
    private final int settlementDays_;
    private final Calendar calendar_;
    private final Date issueDate_;
    private final double faceAmount_;
    private final List<CashFlow> cashflows_;
    private double settlementValue_ = Double.NaN;

    public FixedRateBond(int settlementDays,
                         double faceAmount,
                         Schedule schedule,
                         double[] coupons,
                         DayCounter accrualDayCounter,
                         BusinessDayConvention paymentConvention,
                         double redemption,
                         Date issueDate) {
        QL_REQUIRE(schedule != null && schedule.size() > 1, "bond with no coupons");
        QL_REQUIRE(coupons != null && coupons.length > 0, "no coupon rates given");
        settlementDays_ = settlementDays;
        calendar_ = schedule.calendar();
        issueDate_ = issueDate == null ? new Date() : issueDate;
        faceAmount_ = faceAmount;
        cashflows_ = new ArrayList<>();
        int periods = schedule.size() - 1;
        for (int i = 0; i < periods; i++) {
            double rate = i < coupons.length ? coupons[i] : coupons[coupons.length - 1];
            Date start = schedule.date(i);
            Date end = schedule.date(i + 1);
            Date payment = calendar_.adjust(end, paymentConvention);
            cashflows_.add(new FixedRateCoupon(payment, faceAmount, rate, accrualDayCounter,
                    start, end, new Date(), new Date(), new Date()));
        }
        Date redemptionDate = cashflows_.get(cashflows_.size() - 1).date();
        cashflows_.add(new SimpleCashFlow(faceAmount * redemption / 100.0, redemptionDate));
    }

    public List<CashFlow> cashflows() {
        return cashflows_;
    }

    public Date settlementDate() {
        Date evaluation = Settings.instance.evaluationDate().Date();
        Date settlement = calendar_.advance(evaluation, settlementDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        if (TimeUtils.equals(issueDate_, new Date()))
            return settlement;
        return TimeUtils.greater(issueDate_, settlement) ? issueDate_ : settlement;
    }

    public double notional(Date date) {
        Date redemptionDate = cashflows_.get(cashflows_.size() - 1).date();
        if (!TimeUtils.less(date, redemptionDate))
            return 0.0;
        return faceAmount_;
    }

    public double dirtyPrice() {
        double current = notional(settlementDate());
        QL_REQUIRE(current != 0.0, "current notional is zero");
        return settlementValue() * 100.0 / current;
    }

    public double cleanPrice() {
        return dirtyPrice() - accruedAmount(settlementDate());
    }

    public double accruedAmount(Date settlement) {
        double current = notional(settlement);
        if (current == 0.0)
            return 0.0;
        double total = 0.0;
        for (CashFlow cashflow : cashflows_) {
            if (cashflow instanceof Coupon coupon
                    && !cashflow.hasOccurred(settlement, Optional.of(false)))
                total += coupon.accruedAmount(settlement);
        }
        return total * 100.0 / current;
    }

    public double settlementValue() {
        calculate();
        QL_REQUIRE(!Double.isNaN(settlementValue_), "settlement value not provided");
        return settlementValue_;
    }

    @Override
    public boolean isExpired() {
        Date today = Settings.instance.evaluationDate().Date();
        return cashflows_.get(cashflows_.size() - 1).hasOccurred(today, Optional.of(true));
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        QL_REQUIRE(args instanceof DiscountingBondEngine.arguments, "wrong argument type");
        DiscountingBondEngine.arguments arguments = (DiscountingBondEngine.arguments) args;
        arguments.settlementDate = settlementDate();
        arguments.cashflows = cashflows_;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        QL_REQUIRE(r instanceof DiscountingBondEngine.results, "wrong result type");
        settlementValue_ = ((DiscountingBondEngine.results) r).settlementValue;
    }
}
