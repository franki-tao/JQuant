package jquant.cashflows;

import jquant.Handle;
import jquant.Settings;
import jquant.indexes.OvernightIndex;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricer for arithmetically averaged overnight-indexed floating coupons.
 *
 * <p>Reference: Katsumi Takada 2011, Valuation of Arithmetically Average of
 * Fed Funds Rates and Construction of the US Dollar Swap Yield Curve.
 *
 * <p>Port of QuantLib's {@code ArithmeticAveragedOvernightIndexedCouponPricer}
 * in {@code ql/cashflows/overnightindexedcouponpricer.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/overnightindexedcouponpricer.cpp
 */
public class ArithmeticAveragedOvernightIndexedCouponPricer extends OvernightIndexedCouponPricer {
    private final boolean byApprox_;
    private final double mrs_;
    private final double vol_;

    public ArithmeticAveragedOvernightIndexedCouponPricer(double meanReversion,
                                                          double volatility,
                                                          boolean byApprox) {
        byApprox_ = byApprox;
        mrs_ = meanReversion;
        vol_ = volatility;
    }

    /** Simplified constructor assuming no convexity correction. */
    public ArithmeticAveragedOvernightIndexedCouponPricer(boolean byApprox) {
        this(0.03, 0.0, byApprox);
    }

    @Override
    public double swapletRate() {
        return averageRate(coupon_.accrualEndDate());
    }

    @Override
    public double averageRate(final Date date) {
        final OvernightIndex index = (OvernightIndex) coupon_.index();

        final List<Date> fixingDates = coupon_.fixingDates();
        final List<Date> interestDates = coupon_.interestDates();
        final double[] dt = coupon_.dt();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date);

        double accumulatedRate = 0.0;

        final Date today = Settings.instance.evaluationDate().Date();

        // already fixed part
        while (i < n && TimeUtils.less(fixingDates.get(i), today)) {
            // rate must have been fixed
            double pastFixing = index.pastFixing(fixingDates.get(i));
            QL_REQUIRE(!Double.isNaN(pastFixing),
                    "Missing " + index.name() + " fixing for " + fixingDates.get(i));
            double span = TimeUtils.geq(date, interestDates.get(i + 1))
                    ? dt[i]
                    : index.dayCounter().yearFraction(interestDates.get(i), date,
                            new Date(), new Date());
            accumulatedRate += pastFixing * span;
            ++i;
        }

        // today is a border case
        if (i < n && TimeUtils.equals(fixingDates.get(i), today)) {
            // might have been fixed
            try {
                double pastFixing = index.pastFixing(fixingDates.get(i));
                if (!Double.isNaN(pastFixing)) {
                    double span = TimeUtils.geq(date, interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date,
                                    new Date(), new Date());
                    accumulatedRate += pastFixing * span;
                    ++i;
                }
            } catch (Exception e) {
                // fall through and forecast
            }
        }

        /* forward part using telescopic property in order
           to avoid the evaluation of multiple forward fixings
           (approximation proposed by Katsumi Takada) */
        if (byApprox_ && i < n) {
            final Handle<YieldTermStructure> curve = index.forwardingTermStructure();
            QL_REQUIRE(!curve.empty(),
                    "null term structure set to this instance of " + index.name());
            final YieldTermStructure ts = curve.currentLink();

            final List<Date> dates = coupon_.valueDates();
            final double startDiscount = ts.discount(dates.get(i), false);
            final double endDiscount = ts.discount(dates.get(n), false);

            final double tsTime = ts.timeFromReference(dates.get(i));
            final double teTime = ts.timeFromReference(dates.get(n));
            accumulatedRate += Math.log(startDiscount / endDiscount)
                    - convAdj1(tsTime, teTime)
                    - convAdj2(tsTime, teTime);
        } else if (i < n) {
            final Handle<YieldTermStructure> curve = index.forwardingTermStructure();
            QL_REQUIRE(!curve.empty(),
                    "null term structure set to this instance of " + index.name());
            final YieldTermStructure ts = curve.currentLink();

            final List<Date> dates = coupon_.valueDates();
            final double te = ts.timeFromReference(dates.get(n));
            final DayCounter dc = index.dayCounter();
            while (i < n) {
                // forecast fixing
                double forecastFixing = index.fixing(fixingDates.get(i), false);
                double ti1 = ts.timeFromReference(dates.get(i));
                double ti2 = ts.timeFromReference(dates.get(i + 1));
                /* convexity adjustment due to payment delay of each
                   overnight fixing, supposing a Hull-White short rate model */
                double convAdj = Math.exp(
                        0.5 * vol_ * vol_ / (mrs_ * mrs_ * mrs_)
                                * (Math.exp(2 * mrs_ * ti1) - 1)
                                * (Math.exp(-mrs_ * ti2) - Math.exp(-mrs_ * te))
                                * (Math.exp(-mrs_ * ti2) - Math.exp(-mrs_ * ti1)));
                accumulatedRate += convAdj * (1 + forecastFixing * dt[i]) - 1;
                ++i;
            }
        }

        double rate = accumulatedRate / coupon_.accruedPeriod(date);
        return coupon_.gearing() * rate + coupon_.spread();
    }

    private double convAdj1(double ts, double te) {
        return vol_ * vol_ / (4.0 * mrs_ * mrs_ * mrs_)
                * (1.0 - Math.exp(-2.0 * mrs_ * ts))
                * Math.pow(1.0 - Math.exp(-mrs_ * (te - ts)), 2);
    }

    private double convAdj2(double ts, double te) {
        return vol_ * vol_ / (2.0 * mrs_ * mrs_)
                * ((te - ts)
                   - Math.pow(1.0 - Math.exp(-mrs_ * (te - ts)), 2) / mrs_
                   - (1.0 - Math.exp(-2.0 * mrs_ * (te - ts))) / (2.0 * mrs_));
    }
}
