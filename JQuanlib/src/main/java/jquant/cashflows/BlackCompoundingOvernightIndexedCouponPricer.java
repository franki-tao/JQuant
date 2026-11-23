package jquant.cashflows;

import jquant.Handle;
import jquant.Option;
import jquant.Settings;
import jquant.indexes.OvernightIndex;
import jquant.pricingengines.BlackFormula;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

/**
 * Black coupon pricer for capped/floored compounded overnight-indexed coupons.
 *
 * <p>Port of QuantLib's {@code BlackCompoundingOvernightIndexedCouponPricer}
 * in {@code ql/cashflows/blackovernightindexedcouponpricer.cpp}. The methods
 * are highly experimental and ad-hoc in the official implementation and are
 * reproduced verbatim here.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/blackovernightindexedcouponpricer.cpp
 */
public class BlackCompoundingOvernightIndexedCouponPricer
        extends CompoundingOvernightIndexedCouponPricer {
    private double gearing_;
    private double effectiveIndexFixing_;
    private double swapletRate_;

    public BlackCompoundingOvernightIndexedCouponPricer() {
        this(null, false);
    }

    public BlackCompoundingOvernightIndexedCouponPricer(
            final Handle<OptionletVolatilityStructure> v) {
        this(v, false);
    }

    public BlackCompoundingOvernightIndexedCouponPricer(
            final Handle<OptionletVolatilityStructure> v,
            final boolean effectiveVolatilityInput) {
        super(v, effectiveVolatilityInput);
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        super.initialize(coupon);

        gearing_ = coupon.gearing();
        final ComputeResult computed = compute(coupon_.accrualEndDate());
        swapletRate_ = computed.rate();
        effectiveIndexFixing_ = computed.effectiveIndexFixing();
        effectiveCapletVolatility_ = Double.NaN;
        effectiveFloorletVolatility_ = Double.NaN;
    }

    private double optionletRateGlobal(final Option.Type optionType,
                                       final double effStrike) {
        final Date lastRelevantFixingDate = coupon_.fixingDate();
        if (TimeUtils.leq(lastRelevantFixingDate,
                Settings.instance.evaluationDate().Date())) {
            // the amount is determined
            final double a, b;
            if (optionType == Option.Type.Call) {
                a = effectiveIndexFixing_;
                b = effStrike;
            } else {
                a = effStrike;
                b = effectiveIndexFixing_;
            }
            return gearing_ * Math.max(a - b, 0.0);
        } else {
            // not yet determined, use Black model
            QL_REQUIRE(!capletVolatility().empty(),
                    "BlackCompoundingOvernightIndexedCouponPricer: missing "
                            + "optionlet volatility");
            final List<Date> fixingDates = coupon_.fixingDates();
            QL_REQUIRE(!fixingDates.isEmpty(),
                    "BlackCompoundingOvernightIndexedCouponPricer: empty fixing dates");
            final OptionletVolatilityStructure vol = capletVolatility().currentLink();
            final boolean shiftedLn =
                    vol.volatilityType() == Sarb.VolatilityType.ShiftedLognormal;
            final double shift = vol.displacement();
            final double stdDev;
            final double effectiveTime =
                    vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));
            if (effectiveVolatilityInput()) {
                // vol input is effective, i.e. we use a plain Black model
                stdDev = vol.volatility(
                        fixingDates.get(fixingDates.size() - 1), effStrike, false)
                        * Math.sqrt(effectiveTime);
            } else {
                // vol input is not effective: see Lyashenko, Mercurio,
                // "Looking forward to backward looking rates", section 6.3.
                // The idea is to dampen the average volatility sigma between
                // the fixing start and fixing end date by a linear function
                // going from (fixing start, 1) to (fixing end, 0).
                final double fixingStartTime =
                        vol.timeFromReference(fixingDates.get(0));
                final double fixingEndTime = vol.timeFromReference(
                        fixingDates.get(fixingDates.size() - 1));
                final double sigma = vol.volatility(
                        TimeUtils.max(fixingDates.get(0),
                                vol.referenceDate().add(1)),
                        effStrike, false);
                double t = Math.max(fixingStartTime, 0.0);
                if (!close_enough(fixingEndTime, t, 42))
                    t += (fixingEndTime - t) * (fixingEndTime - t)
                            * (fixingEndTime - t)
                            / ((fixingEndTime - fixingStartTime)
                            * (fixingEndTime - fixingStartTime)) / 3.0;
                stdDev = sigma * Math.sqrt(t);
            }
            if (optionType == Option.Type.Call)
                effectiveCapletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            else
                effectiveFloorletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            final double fixing = shiftedLn
                    ? BlackFormula.blackFormula(optionType, effStrike,
                            effectiveIndexFixing_, stdDev, 1.0, shift)
                    : BlackFormula.bachelierBlackFormula(optionType, effStrike,
                            effectiveIndexFixing_, stdDev, 1.0);
            return gearing_ * fixing;
        }
    }

    static double cappedFlooredRate(final double r,
                                             final Option.Type optionType,
                                             final double k) {
        if (optionType == Option.Type.Call) {
            return Math.min(r, k);
        } else {
            return Math.max(r, k);
        }
    }

    private double optionletRateLocal(final Option.Type optionType,
                                      final double effStrike) {

        QL_REQUIRE(!effectiveVolatilityInput(),
                "BlackAverageONIndexedCouponPricer::optionletRateLocal() does "
                        + "not support effective volatility input.");

        // We compute a rate and a rawRate such that
        // rate * tau * nominal is the amount of the coupon with daily
        // capped / floored rates
        // rawRate * tau * nominal is the amount of the coupon without
        // capping / flooring the rate.
        // We then return the difference between rate and rawRate (with the
        // correct sign) as the option component of the coupon.

        // See CappedFlooredOvernightIndexedCoupon::effectiveCap(), Floor()
        // for what is passed in as effStrike. From this we back out the
        // absolute strike at which the
        // - daily rate + spread (spread included) or the
        // - daily rate (spread excluded)
        // is capped / floored.

        final double absStrike = coupon_.compoundSpreadDaily()
                ? effStrike + coupon_.spread() : effStrike;

        final OvernightIndex index = (OvernightIndex) coupon_.index();

        final List<Date> fixingDates = coupon_.fixingDates();
        final double[] dt = coupon_.dt();

        final int n = dt.length;
        int i = 0;
        QL_REQUIRE(coupon_.lockoutDays() < n,
                "rate cutoff (" + coupon_.lockoutDays()
                        + ") must be less than number of fixings in period ("
                        + n + ")");
        final int nCutoff = n - coupon_.lockoutDays();

        double compoundFactor = 1.0, compoundFactorRaw = 1.0;

        // already fixed part
        final Date today = Settings.instance.evaluationDate().Date();
        while (i < n && TimeUtils.less(fixingDates.get(Math.min(i, nCutoff)),
                today)) {
            // rate must have been fixed
            double pastFixing =
                    index.pastFixing(fixingDates.get(Math.min(i, nCutoff)));
            QL_REQUIRE(!Double.isNaN(pastFixing),
                    "Missing " + index.name() + " fixing for "
                            + fixingDates.get(Math.min(i, nCutoff)));
            if (coupon_.compoundSpreadDaily()) {
                pastFixing += coupon_.spread();
            }
            compoundFactor *= 1.0
                    + cappedFlooredRate(pastFixing, optionType, absStrike) * dt[i];
            compoundFactorRaw *= 1.0 + pastFixing * dt[i];
            ++i;
        }

        // today is a border case
        if (i < n && TimeUtils.equals(fixingDates.get(Math.min(i, nCutoff)),
                today)) {
            // might have been fixed
            try {
                double pastFixing = index.pastFixing(today);
                if (!Double.isNaN(pastFixing)) {
                    if (coupon_.compoundSpreadDaily()) {
                        pastFixing += coupon_.spread();
                    }
                    compoundFactor *= 1.0
                            + cappedFlooredRate(pastFixing, optionType, absStrike)
                            * dt[i];
                    compoundFactorRaw *= 1.0 + pastFixing * dt[i];
                    ++i;
                } // else fall through and forecast
            } catch (Exception e) {
                // fall through and forecast
            }
        }

        // forward part, approximation by pricing a cap / floor in the middle
        // of the future period
        final List<Date> dates = coupon_.valueDates();
        if (i < n) {
            final Handle<YieldTermStructure> curve =
                    index.forwardingTermStructure();
            QL_REQUIRE(!curve.empty(),
                    "null term structure set to this instance of "
                            + index.name());
            final YieldTermStructure ts = curve.currentLink();

            double startDiscount = ts.discount(dates.get(i), false);
            double endDiscount = ts.discount(dates.get(Math.max(nCutoff, i)),
                    false);

            // handle the rate cutoff period (if there is any)
            if (nCutoff < n) {
                // forward discount factor for one calendar day on the cutoff date
                final double discountCutoffDate =
                        ts.discount(dates.get(nCutoff).add(1), false)
                                / ts.discount(dates.get(nCutoff), false);
                // keep the above forward discount factor constant during the
                // cutoff period
                endDiscount *= Math.pow(discountCutoffDate,
                        dates.get(n).serialNumber()
                                - dates.get(nCutoff).serialNumber());
            }

            // estimate the average daily rate over the future period
            // (approximate the continuously compounded rate)
            final double tau = coupon_.dayCounter().yearFraction(
                    dates.get(i), dates.get(dates.size() - 1),
                    new Date(), new Date());
            double averageRate = -Math.log(endDiscount / startDiscount) / tau;

            // compute the value of a cap or floor with fixing in the middle
            // of the future period (but accounting for the rate cutoff here)
            final OptionletVolatilityStructure vol =
                    capletVolatility().currentLink();
            final double midPoint =
                    (vol.timeFromReference(dates.get(i))
                            + vol.timeFromReference(dates.get(nCutoff))) / 2.0;
            final double stdDev = vol.volatility(midPoint, effStrike, false)
                    * Math.sqrt(midPoint);
            final double shift = vol.displacement();
            final boolean shiftedLn =
                    vol.volatilityType() == Sarb.VolatilityType.ShiftedLognormal;
            final double cfValue = shiftedLn
                    ? BlackFormula.blackFormula(optionType, effStrike,
                            averageRate, stdDev, 1.0, shift)
                    : BlackFormula.bachelierBlackFormula(optionType, effStrike,
                            averageRate, stdDev, 1.0);
            final double effectiveTime = vol.timeFromReference(
                    fixingDates.get(fixingDates.size() - 1));
            if (optionType == Option.Type.Call)
                effectiveCapletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            else
                effectiveFloorletVolatility_ = stdDev / Math.sqrt(effectiveTime);

            // add spread to average rate
            if (coupon_.compoundSpreadDaily()) {
                averageRate += coupon_.spread();
            }

            // incorporate cap/floor into average rate
            final double averageRateRaw = averageRate;
            averageRate += optionType == Option.Type.Call ? (-cfValue) : cfValue;

            // now assume the averageRate is the effective rate over the
            // future period and update the compoundFactor
            // this is an approximation, see "Ester / Daily Spread Curve Setup
            // in ORE": set tau to avg value
            final double dailyTau = coupon_.dayCounter().yearFraction(
                    dates.get(i), dates.get(dates.size() - 1),
                    new Date(), new Date())
                    / (dates.get(dates.size() - 1).serialNumber()
                    - dates.get(i).serialNumber());
            // now use formula (4) from the paper
            compoundFactor *= Math.pow(1.0 + dailyTau * averageRate,
                    dates.get(dates.size() - 1).serialNumber()
                            - dates.get(i).serialNumber());
            compoundFactorRaw *= Math.pow(1.0 + dailyTau * averageRateRaw,
                    dates.get(dates.size() - 1).serialNumber()
                            - dates.get(i).serialNumber());
        }

        final double tau = coupon_.lockoutDays() == 0
                ? coupon_.accrualPeriod()
                : coupon_.dayCounter().yearFraction(dates.get(0),
                        dates.get(dates.size() - 1), new Date(), new Date());
        double rate = (compoundFactor - 1.0) / tau;
        double rawRate = (compoundFactorRaw - 1.0) / tau;

        rate *= coupon_.gearing();
        rawRate *= coupon_.gearing();

        if (!coupon_.compoundSpreadDaily()) {
            rate += coupon_.spread();
            rawRate += coupon_.spread();
        }

        // return optionletRate := r - rawRate, i.e. the option component only
        // (see CappedFlooredOvernightIndexedCoupon::rate() for the signs of
        // the capletRate / flooredRate)
        return (optionType == Option.Type.Call ? -1.0 : 1.0) * (rate - rawRate);
    }

    @Override
    public double swapletRate() {
        return swapletRate_;
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return capletRate(effectiveCap, false);
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return floorletRate(effectiveFloor, false);
    }

    @Override
    public double capletRate(final double effectiveCap,
                             final boolean dailyCapFloor) {
        return dailyCapFloor ? optionletRateLocal(Option.Type.Call, effectiveCap)
                : optionletRateGlobal(Option.Type.Call, effectiveCap);
    }

    @Override
    public double floorletRate(final double effectiveFloor,
                               final boolean dailyCapFloor) {
        return dailyCapFloor ? optionletRateLocal(Option.Type.Put, effectiveFloor)
                : optionletRateGlobal(Option.Type.Put, effectiveFloor);
    }

    @Override
    public double swapletPrice() {
        QL_FAIL("BlackCompoundingOvernightIndexedCouponPricer::swapletPrice() "
                + "not provided");
        return 0.0;
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        QL_FAIL("BlackCompoundingOvernightIndexedCouponPricer::capletPrice() "
                + "not provided");
        return 0.0;
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        QL_FAIL("BlackCompoundingOvernightIndexedCouponPricer::floorletPrice() "
                + "not provided");
        return 0.0;
    }
}
