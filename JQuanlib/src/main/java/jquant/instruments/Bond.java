package jquant.instruments;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.CashFlows;
import jquant.cashflows.Coupon;
import jquant.cashflows.SimpleCashFlow;
import jquant.pricingengines.bond.BondFunctions;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

/**
 * Base class for bond instruments.
 *
 * <p>Prices are per 100 of the current notional. {@link BondFunctions} adapts
 * the {@link CashFlows} analytics to that convention; this class only owns the
 * cash flows, the notional schedule and the settlement value returned by the
 * pricing engine.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bond.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bond.cpp
 */
public class Bond extends Instrument {

    /**
     * A bond price, either clean or dirty.
     *
     * <p>The default instance has no amount and reports {@link #isValid()}
     * false, matching {@code Bond::Price()}.
     */
    public static class Price {
        public enum Type {
            DIRTY,
            CLEAN
        }

        private final double amount_;
        private final Type type_;

        /** Invalid price, used as the official default argument. */
        public Price() {
            this(Double.NaN, Type.CLEAN);
        }

        public Price(double amount, Type type) {
            amount_ = amount;
            type_ = type;
        }

        public double amount() {
            QL_REQUIRE(!Double.isNaN(amount_), "no amount given");
            return amount_;
        }

        public Type type() {
            return type_;
        }

        public boolean isValid() {
            return !Double.isNaN(amount_);
        }
    }

    public static class arguments implements PricingEngine.arguments {
        public Date settlementDate;
        public List<CashFlow> cashflows;
        public Calendar calendar;

        @Override
        public void validate() {
            QL_REQUIRE(settlementDate != null
                            && TimeUtils.neq(settlementDate, new Date()),
                    "no settlement date provided");
            QL_REQUIRE(cashflows != null && !cashflows.isEmpty(),
                    "no cash flow provided");
            for (CashFlow cashflow : cashflows)
                QL_REQUIRE(cashflow != null, "null cash flow provided");
        }
    }

    public static class results extends Instrument.results {
        public double settlementValue = Double.NaN;

        public results() {
            additionalResults = new HashMap<>();
        }

        @Override
        public void reset() {
            settlementValue = Double.NaN;
            super.reset();
        }
    }

    protected int settlementDays_;
    protected Calendar calendar_;
    protected List<Date> notionalSchedule_ = new ArrayList<>();
    protected List<Double> notionals_ = new ArrayList<>();
    protected List<CashFlow> cashflows_ = new ArrayList<>();
    protected List<CashFlow> redemptions_ = new ArrayList<>();
    protected Date maturityDate_ = new Date();
    protected Date issueDate_ = new Date();
    protected double settlementValue_ = Double.NaN;

    public Bond(int settlementDays, Calendar calendar) {
        this(settlementDays, calendar, new Date());
    }

    public Bond(int settlementDays, Calendar calendar, Date issueDate) {
        settlementDays_ = settlementDays;
        calendar_ = calendar;
        issueDate_ = issueDate == null ? new Date() : issueDate.copy();
    }

    //! \name Inspectors
    public int settlementDays() {
        return settlementDays_;
    }

    public Calendar calendar() {
        return calendar_;
    }

    public List<Double> notionals() {
        return new ArrayList<>(notionals_);
    }

    /**
     * Current notional at the given date. An empty date means the bond
     * settlement date; a date after the last notional schedule point means the
     * bond has already been redeemed and has zero notional.
     */
    public double notional(Date d) {
        if (d == null || TimeUtils.equals(d, new Date()))
            d = settlementDate();

        if (TimeUtils.greater(d, notionalSchedule_.get(notionalSchedule_.size() - 1)))
            // after maturity
            return 0.0;

        // First schedule point greater than or equal to d, starting from the
        // second one because the first is the null date.
        int index = lowerBound(notionalSchedule_, d, 1);
        if (TimeUtils.less(d, notionalSchedule_.get(index)))
            return notionals_.get(index - 1);
        else
            // d is a redemption date: under bond conventions the payment has
            // already occurred and the notional has changed.
            return notionals_.get(index);
    }

    public double notional() {
        return notional(new Date());
    }

    /** All cash flows, including the redemptions. */
    public List<CashFlow> cashflows() {
        return cashflows_;
    }

    /** Just the redemption flows. */
    public List<CashFlow> redemptions() {
        return redemptions_;
    }

    public CashFlow redemption() {
        QL_REQUIRE(redemptions_.size() == 1, "multiple redemption cash flows given");
        return redemptions_.get(redemptions_.size() - 1);
    }

    public Date startDate() {
        return CashFlows.startDate(cashflows_).copy();
    }

    public Date maturityDate() {
        if (!TimeUtils.equals(maturityDate_, new Date()))
            return maturityDate_.copy();
        return CashFlows.maturityDate(cashflows_).copy();
    }

    public Date issueDate() {
        return issueDate_.copy();
    }

    public boolean isTradable() {
        return isTradable(new Date());
    }

    public boolean isTradable(Date d) {
        return BondFunctions.isTradable(this, d);
    }

    public Date settlementDate() {
        return settlementDate(new Date());
    }

    public Date settlementDate(Date d) {
        if (d == null || TimeUtils.equals(d, new Date()))
            d = Settings.instance.evaluationDate().Date();

        // Usually the settlement is at T+n...
        Date settlement = calendar_.advance(d, settlementDays_, TimeUnit.DAYS);
        // ...but the bond won't be traded before the issue date, if given.
        if (TimeUtils.equals(issueDate_, new Date()))
            return settlement;
        return TimeUtils.max(settlement, issueDate_).copy();
    }

    //! \name Calculations
    public double cleanPrice() {
        return dirtyPrice() - accruedAmount(settlementDate());
    }

    public double dirtyPrice() {
        double currentNotional = notional(settlementDate());
        if (currentNotional == 0.0)
            return 0.0;
        return settlementValue() * 100.0 / currentNotional;
    }

    public double settlementValue() {
        calculate();
        QL_REQUIRE(!Double.isNaN(settlementValue_), "settlement value not provided");
        return settlementValue_;
    }

    public double settlementValue(double cleanPrice) {
        double dirtyPrice = cleanPrice + accruedAmount(settlementDate());
        return dirtyPrice / 100.0 * notional(settlementDate());
    }

    public double yield(DayCounter dc, Compounding comp, Frequency freq) {
        return this.yield(dc, comp, freq, 1.0e-8, 100, 0.05, Price.Type.CLEAN);
    }

    public double yield(DayCounter dc,
                        Compounding comp,
                        Frequency freq,
                        double accuracy,
                        int maxEvaluations,
                        double guess,
                        Price.Type priceType) {
        double currentNotional = notional(settlementDate());
        if (currentNotional == 0.0)
            return 0.0;

        Price price = new Price(priceType == Price.Type.CLEAN ? cleanPrice() : dirtyPrice(),
                priceType);
        return BondFunctions.yield(this, price, dc, comp, freq,
                settlementDate(), accuracy, maxEvaluations, guess);
    }

    public double cleanPrice(double y,
                             DayCounter dc,
                             Compounding comp,
                             Frequency freq) {
        return cleanPrice(y, dc, comp, freq, new Date());
    }

    public double cleanPrice(double y,
                             DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Date settlement) {
        return BondFunctions.cleanPrice(this, y, dc, comp, freq, settlement);
    }

    public double dirtyPrice(double y,
                             DayCounter dc,
                             Compounding comp,
                             Frequency freq) {
        return dirtyPrice(y, dc, comp, freq, new Date());
    }

    public double dirtyPrice(double y,
                             DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Date settlement) {
        double currentNotional = notional(settlement);
        if (currentNotional == 0.0)
            return 0.0;

        return BondFunctions.cleanPrice(this, y, dc, comp, freq, settlement)
                + accruedAmount(settlement);
    }

    public double yield(Price price,
                        DayCounter dc,
                        Compounding comp,
                        Frequency freq) {
        return this.yield(price, dc, comp, freq, new Date(), 1.0e-8, 100, 0.05);
    }

    public double yield(Price price,
                        DayCounter dc,
                        Compounding comp,
                        Frequency freq,
                        Date settlement) {
        return this.yield(price, dc, comp, freq, settlement, 1.0e-8, 100, 0.05);
    }

    public double yield(Price price,
                        DayCounter dc,
                        Compounding comp,
                        Frequency freq,
                        Date settlement,
                        double accuracy,
                        int maxEvaluations,
                        double guess) {
        double currentNotional = notional(settlement);
        if (currentNotional == 0.0)
            return 0.0;

        return BondFunctions.yield(this, price, dc, comp, freq,
                settlement, accuracy, maxEvaluations, guess);
    }

    public double accruedAmount() {
        return accruedAmount(new Date());
    }

    public double accruedAmount(Date settlement) {
        double currentNotional = notional(settlement);
        if (currentNotional == 0.0)
            return 0.0;
        return BondFunctions.accruedAmount(this, settlement);
    }

    @Override
    public boolean isExpired() {
        // This is the Instrument interface, so it doesn't use BondFunctions.
        // A null includeSettlementDateFlows makes CashFlows use the Settings
        // default (whether reference-date events are included).
        return CashFlows.isExpired(cashflows_, null,
                Settings.instance.evaluationDate().Date());
    }

    //! \name Instrument interface
    @Override
    protected void setupExpired() {
        super.setupExpired();
        settlementValue_ = 0.0;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        QL_REQUIRE(args instanceof Bond.arguments, "wrong argument type");
        Bond.arguments arguments = (Bond.arguments) args;
        arguments.settlementDate = settlementDate();
        arguments.cashflows = cashflows_;
        arguments.calendar = calendar_;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        QL_REQUIRE(r instanceof Bond.results, "wrong result type");
        settlementValue_ = ((Bond.results) r).settlementValue;
    }

    //! \name Redemption helpers
    protected void addRedemptionsToCashflows() {
        addRedemptionsToCashflows(new double[0]);
    }

    /**
     * Build redemption payments from the existing coupons and append them to
     * the cash flows. Elements of {@code redemptions} are in base 100 and
     * multiply the notional change; the last element is reused when the vector
     * is shorter than the schedule.
     */
    protected void addRedemptionsToCashflows(double[] redemptions) {
        calculateNotionalsFromCashflows();

        redemptions_.clear();
        for (int i = 1; i < notionalSchedule_.size(); ++i) {
            double r = i < redemptions.length ? redemptions[i]
                    : redemptions.length > 0 ? redemptions[redemptions.length - 1]
                    : 100.0;
            double amount = (r / 100.0)
                    * (notionals_.get(i - 1) - notionals_.get(i));
            // QuantLib uses Redemption/AmortizingPayment here; both are plain
            // SimpleCashFlow subclasses with no extra behaviour.
            CashFlow payment = new SimpleCashFlow(amount,
                    notionalSchedule_.get(i).copy());
            cashflows_.add(payment);
            redemptions_.add(payment);
        }
        // A stable sort moves the redemptions to the right places while
        // ensuring that they follow coupons with the same date.
        cashflows_.sort((a, b) -> Integer.compare(a.date().serialNumber(),
                b.date().serialNumber()));
    }

    protected void setSingleRedemption(double notional, double redemption, Date date) {
        setSingleRedemption(notional,
                new SimpleCashFlow(notional * redemption / 100.0, date.copy()));
    }

    protected void setSingleRedemption(double notional, CashFlow redemption) {
        notionalSchedule_.clear();
        notionals_.clear();
        redemptions_.clear();

        notionalSchedule_.add(new Date());
        notionals_.add(notional);

        notionalSchedule_.add(redemption.date().copy());
        notionals_.add(0.0);

        cashflows_.add(redemption);
        redemptions_.add(redemption);
    }

    /**
     * Collect notional information from the coupons. The schedule starts at
     * the null date and a new point is added whenever the coupon notional
     * changes; the final notional is zero.
     */
    protected void calculateNotionalsFromCashflows() {
        notionalSchedule_.clear();
        notionals_.clear();

        Date lastPaymentDate = new Date();
        notionalSchedule_.add(new Date());
        for (CashFlow cashflow : cashflows_) {
            if (!(cashflow instanceof Coupon coupon))
                continue;

            double notional = coupon.nominal();
            if (notionals_.isEmpty()) {
                // We add the notional only if it is the first one...
                notionals_.add(coupon.nominal());
                lastPaymentDate = coupon.date();
            } else if (!close(notional, notionals_.get(notionals_.size() - 1))) {
                // ...or if it has changed.
                notionals_.add(coupon.nominal());
                // In this case we also add the last valid date for the
                // previous one...
                notionalSchedule_.add(lastPaymentDate);
                // ...and store the candidate for this one.
                lastPaymentDate = coupon.date();
            } else {
                // Otherwise we just extend the valid range of dates for the
                // current notional.
                lastPaymentDate = coupon.date();
            }
        }
        QL_REQUIRE(!notionals_.isEmpty(), "no coupons provided");
        notionals_.add(0.0);
        notionalSchedule_.add(lastPaymentDate);
    }

    private static int lowerBound(List<Date> dates, Date d, int from) {
        int lo = from;
        int hi = dates.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (TimeUtils.less(dates.get(mid), d))
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }
}
