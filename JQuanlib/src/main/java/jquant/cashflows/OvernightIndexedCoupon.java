package jquant.cashflows;

import jquant.Settings;
import jquant.indexes.OvernightIndex;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Coupon paying the interest, depending on the averaging convention, due to
 * daily overnight fixings.
 *
 * <p>Port of QuantLib's {@code OvernightIndexedCoupon} in
 * {@code ql/cashflows/overnightindexedcoupon.cpp}. The lookback, lockout,
 * observation-shift, daily-compounded-spread and rounding options are not
 * ported: they are not used by the basis-swap helpers.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/overnightindexedcoupon.cpp
 */
public class OvernightIndexedCoupon extends FloatingRateCoupon {
    private final List<Date> valueDates_ = new ArrayList<>();
    private final List<Date> interestDates_ = new ArrayList<>();
    private final List<Date> fixingDates_ = new ArrayList<>();
    private final int n_;
    private final double[] dt_;
    private final RateAveraging averagingMethod_;
    private final boolean telescopicValueDates_;

    /** Convenience constructor using the official defaults. */
    public OvernightIndexedCoupon(final Date paymentDate,
                                  double nominal,
                                  final Date startDate,
                                  final Date endDate,
                                  final OvernightIndex overnightIndex) {
        this(paymentDate, nominal, startDate, endDate, overnightIndex,
                1.0, 0.0, new Date(), new Date(), new DayCounter(),
                false, RateAveraging.Compound);
    }

    public OvernightIndexedCoupon(final Date paymentDate,
                                  double nominal,
                                  final Date startDate,
                                  final Date endDate,
                                  final OvernightIndex overnightIndex,
                                  double gearing,
                                  double spread,
                                  final Date refPeriodStart,
                                  final Date refPeriodEnd,
                                  final DayCounter dayCounter,
                                  boolean telescopicValueDates,
                                  RateAveraging averagingMethod) {
        super(paymentDate, nominal, startDate, endDate,
                overnightIndex.fixingDays(), overnightIndex,
                gearing, spread, refPeriodStart, refPeriodEnd,
                dayCounter, false, new Date());
        telescopicValueDates_ = telescopicValueDates;
        averagingMethod_ = averagingMethod;

        QL_REQUIRE(TimeUtils.less(startDate, endDate),
                "startDate must be less than endDate");
        QL_REQUIRE(TimeUtils.geq(paymentDate, endDate),
                "Payment date cannot be earlier than accrual end date");

        Date tmpEndDate = endDate;

        // For the coupon's valuation only the first and last future valuation
        // dates matter; a front and a back stub are enough.
        final jquant.time.Calendar fixingCal = overnightIndex.fixingCalendar();
        if (telescopicValueDates) {
            Date evalDate = Settings.instance.evaluationDate().Date();
            tmpEndDate = fixingCal.advance(
                    TimeUtils.max(startDate, evalDate), 7, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            tmpEndDate = TimeUtils.min(tmpEndDate, endDate);
        }
        valueDates_.addAll(fixingCal.businessDayList(
                fixingCal.adjust(startDate, BusinessDayConvention.PRECEDING),
                fixingCal.adjust(tmpEndDate, BusinessDayConvention.FOLLOWING)));

        if (telescopicValueDates) {
            // if lockout days are defined, we need to ensure that
            // the lockout period is covered by the value dates
            tmpEndDate = fixingCal.adjust(endDate, BusinessDayConvention.FOLLOWING);
            Date tmpLockoutDate = fixingCal.advance(endDate, -1, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            Date nextValueDate = TimeUtils.greater(tmpLockoutDate,
                    valueDates_.get(valueDates_.size() - 1))
                    ? tmpLockoutDate
                    : fixingCal.advance(valueDates_.get(valueDates_.size() - 1),
                            1, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
            while (TimeUtils.leq(nextValueDate, tmpEndDate)) {
                valueDates_.add(nextValueDate);
                nextValueDate = fixingCal.advance(nextValueDate, 1, TimeUnit.DAYS,
                        BusinessDayConvention.FOLLOWING, false);
            }
        }

        QL_REQUIRE(valueDates_.size() >= 2, "degenerate schedule");

        n_ = valueDates_.size() - 1;

        interestDates_.addAll(valueDates_);
        interestDates_.set(0, startDate);
        interestDates_.set(n_, endDate);

        if (fixingDays_ == overnightIndex.fixingDays() && fixingDays_ == 0) {
            fixingDates_.addAll(valueDates_.subList(0, n_));
        } else {
            // Lookback (fixing days) without observation shift.
            for (int i = 0; i < n_; ++i)
                fixingDates_.add(null);
            for (int i = 0; i <= n_; ++i) {
                Date tmp = fixingCal.advance(valueDates_.get(i), -fixingDays_,
                        TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
                if (i < n_)
                    fixingDates_.set(i, tmp);
                if (fixingDays_ != overnightIndex.fixingDays())
                    valueDates_.set(i, overnightIndex.valueDate(tmp));
            }
        }

        // accrual (compounding) periods
        dt_ = new double[n_];
        final DayCounter dc = overnightIndex.dayCounter();
        for (int i = 0; i < n_; ++i)
            dt_[i] = dc.yearFraction(interestDates_.get(i), interestDates_.get(i + 1),
                    new Date(), new Date());

        switch (averagingMethod) {
            case Simple:
                QL_REQUIRE(fixingDays_ == overnightIndex.fixingDays(),
                        "Cannot price an overnight coupon with simple averaging with lookback or lockout.");
                setPricer(new ArithmeticAveragedOvernightIndexedCouponPricer(telescopicValueDates));
                break;
            case Compound:
                setPricer(new CompoundingOvernightIndexedCouponPricer());
                break;
            default:
                QL_FAIL("unknown compounding convention");
        }
    }

    //! fixing dates for the rates to be compounded
    public List<Date> fixingDates() {
        return fixingDates_;
    }

    //! accrual (compounding) periods
    public double[] dt() {
        return dt_;
    }

    //! fixings to be compounded
    public List<Double> indexFixings() {
        List<Double> fixings = new ArrayList<>(n_);
        for (int i = 0; i < n_; ++i)
            fixings.add(index_.fixing(fixingDates_.get(i), false));
        return fixings;
    }

    //! value dates for the rates to be compounded
    public List<Date> valueDates() {
        return valueDates_;
    }

    //! interest dates for the rates to be compounded
    public List<Date> interestDates() {
        return interestDates_;
    }

    //! averaging method
    public RateAveraging averagingMethod() {
        return averagingMethod_;
    }

    //! lockout days (not ported; always zero)
    public int lockoutDays() {
        return 0;
    }

    //! observation-shift flag (not ported; always false)
    public boolean applyObservationShift() {
        return false;
    }

    public boolean telescopicValueDates() {
        return telescopicValueDates_;
    }

    //! the date when the coupon is fully determined
    @Override
    public Date fixingDate() {
        return fixingDates_.get(fixingDates_.size() - 1);
    }

    @Override
    public double accruedAmount(final Date d) {
        if (TimeUtils.leq(d, accrualStartDate_) || TimeUtils.greater(d, paymentDate_)) {
            // out of coupon range
            return 0.0;
        } else {
            return nominal() * averageRate(TimeUtils.min(d, accrualEndDate_)) * accruedPeriod(d);
        }
    }

    public double averageRate(final Date d) {
        QL_REQUIRE(pricer_ != null, "pricer not set");
        pricer_.initialize(this);
        if (pricer_ instanceof OvernightIndexedCouponPricer) {
            return ((OvernightIndexedCouponPricer) pricer_).averageRate(d);
        }
        return pricer_.swapletRate();
    }

    //! Telescopic formula cannot be used with lookback days different from
    //! the intrinsic index fixing delay.
    public boolean canApplyTelescopicFormula() {
        return fixingDays_ == index_.fixingDays();
    }

    public int numberOfFixings() {
        return n_;
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return jquant.CashFlow.hasOccurred(date(), refDate, includeRefDate);
    }

    @Override
    public boolean tradingExCoupon(Date refDate) {
        return false;
    }

}
