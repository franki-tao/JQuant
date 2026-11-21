package jquant.cashflows;

import jquant.Handle;
import jquant.Settings;
import jquant.indexes.InterestRateIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.termstructures.YieldTermStructure;
import jquant.time.*;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! base floating-rate coupon class
public class FloatingRateCoupon extends Coupon {
    protected InterestRateIndex index_;
    protected DayCounter dayCounter_;
    protected Integer fixingDays_;
    protected double gearing_;
    protected double spread_;
    protected boolean isInArrears_;
    protected BusinessDayConvention fixingConvention_;
    protected FloatingRateCouponPricer pricer_;
    protected double rate_;

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              final Integer fixingDays,
                              final InterestRateIndex index,
                              double gearing,
                              double spread,
                              final Date refPeriodStart,
                              final Date refPeriodEnd,
                              DayCounter dayCounter,
                              boolean isInArrears,
                              final Date exCouponDate,
                              BusinessDayConvention fixingConvention) {
        super(paymentDate, nominal, startDate, endDate, refPeriodStart, refPeriodEnd, exCouponDate);
        index_ = index;
        dayCounter_ = dayCounter;
        fixingDays_ = fixingDays == null ? (index != null ? index.fixingDays() : 0) : fixingDays;
        gearing_ = gearing;
        spread_ = spread;
        isInArrears_ = isInArrears;
        fixingConvention_ = fixingConvention;
        QL_REQUIRE(index_ != null, "no index provided");
        QL_REQUIRE(gearing_ != 0, "Null gearing not allowed");
        if (dayCounter_.empty())
            dayCounter_ = index_.dayCounter();

        registerWith(index_);
        registerWith(Settings.instance.evaluationDate());
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index,
                              double gearing,
                              double spread,
                              final Date refPeriodStart,
                              final Date refPeriodEnd,
                              DayCounter dayCounter,
                              boolean isInArrears,
                              final Date exCouponDate) {
        this(paymentDate, nominal, startDate, endDate, Integer.valueOf(fixingDays), index,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter, isInArrears,
                exCouponDate, BusinessDayConvention.PRECEDING);
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index,
                              double gearing,
                              double spread,
                              final Date refPeriodStart,
                              final Date refPeriodEnd,
                              DayCounter dayCounter,
                              boolean isInArrears) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, spread,
                refPeriodStart, refPeriodEnd, dayCounter, isInArrears, new Date());
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index,
                              double gearing,
                              double spread,
                              final Date refPeriodStart,
                              final Date refPeriodEnd,
                              DayCounter dayCounter) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, spread,
                refPeriodStart, refPeriodEnd, dayCounter, false);
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index,
                              double gearing,
                              double spread,
                              final Date refPeriodStart,
                              final Date refPeriodEnd) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, spread,
                refPeriodStart, refPeriodEnd, new DayCounter());
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index,
                              double gearing,
                              double spread) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, spread,
                new Date(), new Date());
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index,
                              double gearing) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, 0.0);
    }

    public FloatingRateCoupon(final Date paymentDate,
                              double nominal,
                              final Date startDate,
                              final Date endDate,
                              int fixingDays,
                              final InterestRateIndex index) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, 1.0);
    }

    //! \name Observer interface
    //@{
    /** Mirrors QuantLib {@code Observer::deepUpdate()}, which defaults to
     *  {@code update()} unless a subclass deep-updates its own observers. */
    public void deepUpdate() {
        update();
    }
    //@}

    //! \name LazyObject interface
    @Override
    public void performCalculations() {
        QL_REQUIRE(pricer_ != null, "pricer not set");
        pricer_.initialize(this);
        rate_ = pricer_.swapletRate();
    }

    //! \name CashFlow interface
    @Override
    public double amount() {
        return rate() * accrualPeriod() * nominal();
    }

    //! \name Coupon interface
    @Override
    public double rate() {
        calculate();
        return rate_;
    }

    public double price(final Handle<YieldTermStructure> discountingCurve) {
        return amount() * discountingCurve.currentLink().discount(date(), false);
    }

    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    @Override
    public double accruedAmount(final Date d) {
        if (TimeUtils.leq(d, accrualStartDate_) || TimeUtils.greater(d, paymentDate_)) {
            // out of coupon range
            return 0.0;
        } else {
            return nominal() * rate() * accruedPeriod(d);
        }
    }

    //! floating index
    public final InterestRateIndex index() {
        return index_;
    }

    //! fixing days
    public int fixingDays() {
        return fixingDays_;
    }

    //! fixing date
    public Date fixingDate() {
        // if isInArrears_ fix at the end of period
        Date refDate = isInArrears_ ? accrualEndDate_ : accrualStartDate_;
        return index_.fixingCalendar().advance(refDate,
                -(fixingDays_), TimeUnit.DAYS, fixingConvention_, false);
    }

    //! fixing convention
    public BusinessDayConvention fixingConvention() {
        return fixingConvention_;
    }

    //! index gearing, i.e. multiplicative coefficient for the index
    public double gearing() {
        return gearing_;
    }

    //! spread paid over the fixing of the underlying index
    public double spread() {
        return spread_;
    }

    //! fixing of the underlying index
    public double indexFixing() {
        return index_.fixing(fixingDate(), false);
    }

    //! convexity adjustment
    public double convexityAdjustment() {
        return convexityAdjustmentImpl(indexFixing());
    }

    //! convexity-adjusted fixing
    public double adjustedFixing() {
        return (rate() - spread()) / gearing();
    }

    //! whether or not the coupon fixes in arrears
    public boolean isInArrears() {
        return isInArrears_;
    }

    //! \name Visitability
    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<FloatingRateCoupon> vv = (Visitor<FloatingRateCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    public void setPricer(final FloatingRateCouponPricer pricer) {
        if (pricer_ != null)
            unregisterWith(pricer_);
        pricer_ = pricer;
        if (pricer_ != null)
            registerWith(pricer_);
        update();
    }

    public FloatingRateCouponPricer pricer() {
        return pricer_;
    }

    //! convexity adjustment for the given index fixing
    protected double convexityAdjustmentImpl(double fixing) {
        return (gearing() == 0.0 ? (0.0) : (adjustedFixing()-fixing));
    }
}
