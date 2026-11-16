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
 * Pricer for compounded overnight-indexed floating coupons.
 *
 * <p>Port of QuantLib's {@code CompoundingOvernightIndexedCouponPricer} in
 * {@code ql/cashflows/overnightindexedcouponpricer.cpp}. The lookback,
 * lockout, observation-shift and daily-compounded-spread branches are not
 * ported, nor used by the basis-swap helpers.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/overnightindexedcouponpricer.cpp
 */
public class CompoundingOvernightIndexedCouponPricer extends OvernightIndexedCouponPricer {
    private record ComputeResult(double rate,
                                 double effectiveSpread,
                                 double effectiveIndexFixing) {
    }

    @Override
    public double swapletRate() {
        return compute(coupon_.accrualEndDate()).rate;
    }

    @Override
    public double averageRate(final Date date) {
        return compute(date).rate;
    }

    public double effectiveSpread() {
        return compute(coupon_.accrualEndDate()).effectiveSpread;
    }

    public double effectiveIndexFixing() {
        return compute(coupon_.accrualEndDate()).effectiveIndexFixing;
    }

    private ComputeResult compute(final Date date) {
        final Date today = Settings.instance.evaluationDate().Date();

        final OvernightIndex index = (OvernightIndex) coupon_.index();

        final List<Date> fixingDates = coupon_.fixingDates();
        final List<Date> valueDates = coupon_.valueDates();
        final List<Date> interestDates = coupon_.interestDates();
        final double[] dt = coupon_.dt();
        final DayCounter dc = index.dayCounter();
        final double couponSpread = coupon_.spread();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date);

        double compoundFactor = 1.0, compoundFactorWithoutSpread = 1.0;

        // already fixed part
        while (i < n && TimeUtils.less(fixingDates.get(i), today)) {
            // rate must have been fixed
            double fixing = index.pastFixing(fixingDates.get(i));
            QL_REQUIRE(!Double.isNaN(fixing),
                    "Missing " + index.name() + " fixing for " + fixingDates.get(i));
            double gf = 1.0 + fixing * dt[i];
            compoundFactorWithoutSpread *= gf;
            compoundFactor *= gf;
            ++i;
        }

        // today is a border case
        if (i < n && TimeUtils.equals(fixingDates.get(i), today)) {
            // might have been fixed
            try {
                double fixing = index.pastFixing(fixingDates.get(i));
                if (!Double.isNaN(fixing)) {
                    double gf = 1.0 + fixing * dt[i];
                    compoundFactorWithoutSpread *= gf;
                    compoundFactor *= gf;
                    ++i;
                }
            } catch (Exception e) {
                // fall through and forecast
            }
        }

        // forward part using telescopic property in order
        // to avoid the evaluation of multiple forward fixings
        // where possible.
        if (i < n) {
            final Handle<YieldTermStructure> curve = index.forwardingTermStructure();
            QL_REQUIRE(!curve.empty(),
                    "null term structure set to this instance of " + index.name());
            final YieldTermStructure ts = curve.currentLink();
            QL_REQUIRE(TimeUtils.leq(ts.referenceDate(), valueDates.get(i))
                            && (ts.allowsExtrapolation()
                                || TimeUtils.leq(valueDates.get(n), ts.maxDate())),
                    "coupon requires a range [" + valueDates.get(i) + ", "
                            + valueDates.get(n) + "] wider than is supported by the term "
                            + "structure for instance of " + index.name() + " ["
                            + ts.referenceDate() + ", "
                            + (ts.allowsExtrapolation() ? "max" : ts.maxDate()) + "]");

            if (coupon_.canApplyTelescopicFormula()) {
                // handle partial accrual of first fixing if the first interestDate
                // lands on a fixing holiday
                final int telescopicStartIdx =
                        (i == 0 && TimeUtils.less(valueDates.get(0), interestDates.get(0))) ? 1 : i;
                // telescopic formula up to potential lockout dates and partial
                // accrual up to date.
                final int endDateIdx = n;
                final int telescopicEndIdx =
                        endDateIdx - (TimeUtils.leq(valueDates.get(endDateIdx), date) ? 0 : 1);
                if (telescopicStartIdx < telescopicEndIdx) {
                    while (i < telescopicStartIdx) {
                        // compound up any periods ahead of the telescopic range
                        double fixing = index.fixing(fixingDates.get(i), false);
                        double gf = 1.0 + fixing * dt[i];
                        compoundFactorWithoutSpread *= gf;
                        compoundFactor *= gf;
                        ++i;
                    }
                    final double startDiscount = ts.discount(valueDates.get(telescopicStartIdx), false);
                    final double endDiscount = ts.discount(valueDates.get(telescopicEndIdx), false);
                    compoundFactor *= startDiscount / endDiscount;
                    compoundFactorWithoutSpread *= startDiscount / endDiscount;
                    i = telescopicEndIdx;
                }
            }
            // compound up any remaining periods
            while (i < n) {
                double fixing = index.fixing(fixingDates.get(i), false);
                double gf = 1.0 + fixing * dt[i];
                compoundFactorWithoutSpread *= gf;
                compoundFactor *= gf;
                ++i;
            }
        }

        final Date rateAccrualStartDate = interestDates.get(0);
        final Date rateAccrualEndDate = TimeUtils.min(date, interestDates.get(n));
        final double tau = dc.yearFraction(rateAccrualStartDate, rateAccrualEndDate,
                new Date(), new Date());
        final double rate = (compoundFactor - 1.0) / tau;
        double swapletRate = coupon_.gearing() * rate + couponSpread;
        final double effectiveSpread = couponSpread;
        final double effectiveIndexFixing = rate;

        return new ComputeResult(swapletRate, effectiveSpread, effectiveIndexFixing);
    }
}
