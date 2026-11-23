package jquant.cashflows;

import jquant.Settings;
import jquant.indexes.OvernightIndex;
import jquant.math.ClosestRounding;
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
 * {@code ql/cashflows/overnightindexedcoupon.cpp}.
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
    private final int lockoutDays_;
    private final boolean applyObservationShift_;
    private final boolean compoundSpreadDaily_;
    private final Date rateComputationStartDate_;
    private final Date rateComputationEndDate_;
    private final Integer roundingPrecision_;

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
        this(paymentDate, nominal, startDate, endDate, overnightIndex,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter,
                telescopicValueDates, averagingMethod, null, 0, false);
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
                                  RateAveraging averagingMethod,
                                  Integer lookbackDays,
                                  int lockoutDays,
                                  boolean applyObservationShift) {
        this(paymentDate, nominal, startDate, endDate, overnightIndex,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter,
                telescopicValueDates, averagingMethod, lookbackDays,
                lockoutDays, applyObservationShift,
                false, new Date(), new Date(), new Date(), null);
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
                                  RateAveraging averagingMethod,
                                  Integer lookbackDays,
                                  int lockoutDays,
                                  boolean applyObservationShift,
                                  boolean compoundSpreadDaily,
                                  final Date rateComputationStartDate,
                                  final Date rateComputationEndDate,
                                  final Date exCouponDate,
                                  Integer roundingPrecision) {
        super(paymentDate, nominal, startDate, endDate,
                lookbackDays, overnightIndex,
                gearing, spread, refPeriodStart, refPeriodEnd,
                dayCounter, false, exCouponDate, BusinessDayConvention.PRECEDING);
        telescopicValueDates_ = telescopicValueDates;
        averagingMethod_ = averagingMethod;
        lockoutDays_ = lockoutDays;
        applyObservationShift_ = applyObservationShift;
        compoundSpreadDaily_ = compoundSpreadDaily;
        rateComputationStartDate_ = rateComputationStartDate;
        rateComputationEndDate_ = rateComputationEndDate;
        roundingPrecision_ = roundingPrecision;

        QL_REQUIRE(TimeUtils.less(startDate, endDate),
                "startDate must be less than endDate");
        QL_REQUIRE(TimeUtils.geq(paymentDate, endDate),
                "Payment date cannot be earlier than accrual end date");

        final Date rateCalcStartDate =
                isEmpty(rateComputationStartDate_) ? startDate : rateComputationStartDate_;
        final Date rateCalcEndDate =
                isEmpty(rateComputationEndDate_) ? endDate : rateComputationEndDate_;
        Date tmpEndDate = rateCalcEndDate;

        QL_REQUIRE(canApplyTelescopicFormula() || !telescopicValueDates,
                "Telescopic formula cannot be applied for a coupon with lookback.");

        // For the coupon's valuation only the first and last future valuation
        // dates matter; a front and a back stub are enough.
        final jquant.time.Calendar fixingCal = overnightIndex.fixingCalendar();
        if (telescopicValueDates) {
            Date evalDate = Settings.instance.evaluationDate().Date();
            tmpEndDate = fixingCal.advance(
                    TimeUtils.max(rateCalcStartDate, evalDate), 7, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            tmpEndDate = TimeUtils.min(tmpEndDate, rateCalcEndDate);
        }
        valueDates_.addAll(fixingCal.businessDayList(
                fixingCal.adjust(rateCalcStartDate, BusinessDayConvention.PRECEDING),
                fixingCal.adjust(tmpEndDate, BusinessDayConvention.FOLLOWING)));

        if (telescopicValueDates) {
            // if lockout days are defined, we need to ensure that
            // the lockout period is covered by the value dates
            tmpEndDate = fixingCal.adjust(rateCalcEndDate, BusinessDayConvention.FOLLOWING);
            Date tmpLockoutDate = fixingCal.advance(rateCalcEndDate,
                    -Math.max(lockoutDays_, 1), TimeUnit.DAYS,
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
        interestDates_.set(0, rateCalcStartDate);
        interestDates_.set(n_, rateCalcEndDate);

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

        // When lockout is used the fixing rate applied for the last k days of
        // the interest period is frozen at the rate observed k days before the
        // period ends.
        if (lockoutDays_ != 0) {
            QL_REQUIRE(lockoutDays_ > 0 && lockoutDays_ < n_,
                    "Lockout period cannot be negative or exceed the number of fixing days.");
            final Date lockoutDate = fixingDates_.get(n_ - 1 - lockoutDays_);
            for (int i = n_ - lockoutDays_; i < n_; ++i)
                fixingDates_.set(i, lockoutDate);
        }

        // accrual (compounding) periods
        dt_ = new double[n_];
        final DayCounter dc = overnightIndex.dayCounter();
        // Official Null<Natural>() converts to the maximum unsigned value, so
        // a null lookback counts as greater than zero in this comparison.
        final List<Date> accrualDates =
                (applyObservationShift_ && (lookbackDays == null || lookbackDays > 0))
                        ? valueDates_ : interestDates_;
        for (int i = 0; i < n_; ++i)
            dt_[i] = dc.yearFraction(accrualDates.get(i), accrualDates.get(i + 1),
                    new Date(), new Date());

        switch (averagingMethod) {
            case Simple:
                QL_REQUIRE(fixingDays_ == overnightIndex.fixingDays()
                                && !applyObservationShift_ && lockoutDays_ == 0,
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

    //! lockout days
    public int lockoutDays() {
        return lockoutDays_;
    }

    //! observation-shift flag
    public boolean applyObservationShift() {
        return applyObservationShift_;
    }

    //! whether the spread is compounded daily
    public boolean compoundSpreadDaily() {
        return compoundSpreadDaily_;
    }

    //! effective spread of the coupon
    public double effectiveSpread() {
        if (!compoundSpreadDaily_)
            return spread();

        if (averagingMethod_ == RateAveraging.Simple)
            return spread();

        final FloatingRateCouponPricer p = pricer();
        if (!(p instanceof CompoundingOvernightIndexedCouponPricer))
            QL_FAIL("OvernightIndexedCoupon::effectiveSpread(): expected "
                    + "OvernightIndexedCouponPricer");
        final CompoundingOvernightIndexedCouponPricer compoundPricer =
                (CompoundingOvernightIndexedCouponPricer) p;
        compoundPricer.initialize(this);
        return compoundPricer.effectiveSpread();
    }

    //! effective index fixing of the coupon
    public double effectiveIndexFixing() {
        final FloatingRateCouponPricer p = pricer();
        if (averagingMethod_ == RateAveraging.Simple)
            QL_FAIL("effectiveIndexFixing() is not defined for an "
                    + "OvernightIndexedCoupon when RateAveraging::Simple is used");

        if (!(p instanceof CompoundingOvernightIndexedCouponPricer))
            QL_FAIL("OvernightIndexedCoupon::effectiveIndexFixing(): expected "
                    + "OvernightIndexedCouponPricer");
        final CompoundingOvernightIndexedCouponPricer compoundPricer =
                (CompoundingOvernightIndexedCouponPricer) p;
        compoundPricer.initialize(this);
        return compoundPricer.effectiveIndexFixing();
    }

    //! start of the rate-computation period (Date() if not overridden)
    public Date rateComputationStartDate() {
        return rateComputationStartDate_;
    }

    //! end of the rate-computation period (Date() if not overridden)
    public Date rateComputationEndDate() {
        return rateComputationEndDate_;
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
        } else if (tradingExCoupon(d)) {
            return nominal() * averageRate(d) * accruedPeriod(d);
        } else {
            // usual case
            return nominal() * averageRate(TimeUtils.min(d, accrualEndDate_)) * accruedPeriod(d);
        }
    }

    @Override
    public double amount() {
        double r = rate();
        if (roundingPrecision_ != null)
            r = new ClosestRounding(roundingPrecision_, 5).value(r);
        return r * accrualPeriod() * nominal();
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
        return fixingDays_ == index_.fixingDays()
                || (applyObservationShift_ && index_.fixingDays() == 0);
    }

    public int numberOfFixings() {
        return n_;
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return jquant.CashFlow.hasOccurred(date(), refDate, includeRefDate);
    }

    private static boolean isEmpty(final Date date) {
        return date == null || date.serialNumber() == 0;
    }

}
