package jquant.cashflows;


import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.Optional;

import static jquant.cashflows.TimingAdjustment.BivariateLognormal;
import static jquant.cashflows.TimingAdjustment.Black76;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.pricingengines.BlackFormula.bachelierBlackFormula;
import static jquant.pricingengines.BlackFormula.blackFormula;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/*! Black-formula pricer for capped/floored Ibor coupons
    References for timing adjustments
    Black76             Hull, Options, Futures and other
                        derivatives, 4th ed., page 550
    BivariateLognormal  http://ssrn.com/abstract=2170721 */
public class BlackIborCouponPricer extends IborCouponPricer {
    protected double discount_;
    private TimingAdjustment timingAdjustment_;
    private Handle<Quote> correlation_;

    public BlackIborCouponPricer(final Handle<OptionletVolatilityStructure> v, // = Handle<OptionletVolatilityStructure>(),
                                 final TimingAdjustment timingAdjustment, // = Black76,
                                 Handle<Quote> correlation, // = Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(1.0))),
                                 final Optional<Boolean> useIndexedCoupon //= ext::nullopt)
    ) {
        super(v, useIndexedCoupon);
        timingAdjustment_ = timingAdjustment;
        correlation_ = correlation;
        // this additional scope seems required to avoid a misleading-indentation warning
        QL_REQUIRE(timingAdjustment_ == Black76 || timingAdjustment_ == BivariateLognormal,
                "unknown timing adjustment (code " + timingAdjustment_ + ")");
        registerWith(correlation_.currentLink());
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        super.initialize(coupon);

        final Handle<YieldTermStructure> rateCurve = index_.forwardingTermStructure();

        if (rateCurve.empty()) {
            discount_ = Double.NaN; // might not be needed, will be checked later
        } else {
            Date paymentDate = coupon_.date();
            if (TimeUtils.greater(paymentDate, rateCurve.currentLink().referenceDate()))
                discount_ = rateCurve.currentLink().discount(paymentDate, false);
            else
                discount_ = 1.0;
        }
    }

    @Override
    public double swapletPrice() {
        // past or future fixing is managed in InterestRateIndex::fixing()
        QL_REQUIRE(!Double.isNaN(discount_), "no forecast curve provided");
        return swapletRate() * accrualPeriod_ * discount_;
    }

    @Override
    public double swapletRate() {
        return gearing_ * adjustedFixing(Double.NaN) + spread_;
    }

    @Override
    public double capletPrice(double effectiveCap) {
        QL_REQUIRE(!Double.isNaN(discount_), "no forecast curve provided");
        return capletRate(effectiveCap) * accrualPeriod_ * discount_;
    }

    @Override
    public double capletRate(double effectiveCap) {
        return gearing_ * optionletRate(Option.Type.Call, effectiveCap);
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        QL_REQUIRE(!Double.isNaN(discount_), "no forecast curve provided");
        return floorletRate(effectiveFloor) * accrualPeriod_ * discount_;
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        return gearing_ * optionletRate(Option.Type.Put, effectiveFloor);
    }

    protected double optionletPrice(Option.Type optionType, double effStrike) {
        QL_REQUIRE(!Double.isNaN(discount_), "no forecast curve provided");
        return optionletRate(optionType, effStrike) * accrualPeriod_ * discount_;
    }

    protected double optionletRate(Option.Type optionType, double effStrike) {
        if (TimeUtils.leq(fixingDate_, Settings.instance.evaluationDate().Date())) {
            // the amount is determined
            double a, b;
            if (optionType == Option.Type.Call) {
                a = coupon_.indexFixing();
                b = effStrike;
            } else {
                a = effStrike;
                b = coupon_.indexFixing();
            }
            return Math.max(a - b, 0.0);
        } else {
            // not yet determined, use Black model
            QL_REQUIRE(!capletVolatility().empty(),
                    "missing optionlet volatility");
            double stdDev =
                    Math.sqrt(capletVolatility().currentLink().blackVariance(fixingDate_,
                            effStrike, false));
            double shift = capletVolatility().currentLink().displacement();
            boolean shiftedLn =
                    capletVolatility().currentLink().volatilityType() == ShiftedLognormal;
            double fixing =
                    shiftedLn
                            ? blackFormula(optionType, effStrike, adjustedFixing(Double.NaN),
                            stdDev, 1.0, shift)
                            : bachelierBlackFormula(optionType, effStrike,
                            adjustedFixing(Double.NaN), stdDev, 1.0);
            return fixing;
        }
    }

    protected double adjustedFixing(double fixing) {
        if (Double.isNaN(fixing))
            fixing = coupon_.indexFixing();

        // if the pay date is equal to the index estimation end date
        // there is no convexity; in all other cases in principle an
        // adjustment has to be applied, but the Black76 method only
        // applies the standard in arrears adjustment; the bivariate
        // lognormal method is more accurate in this regard.
        if ((!coupon_.isInArrears() && timingAdjustment_ == Black76))
            return fixing;
        final Date d1 = fixingDate_;
        final Date d2 = fixingValueDate_;
        final Date d3 = fixingMaturityDate_;
        if (TimeUtils.equals(coupon_.date(), d3))
            return fixing;

        QL_REQUIRE(!capletVolatility().empty(),
                "missing optionlet volatility");
        Date referenceDate = capletVolatility().currentLink().referenceDate();
        // no variance has accumulated, so the convexity is zero
        if (TimeUtils.leq(d1, referenceDate))
            return fixing;
        final double tau = spanningTimeIndexMaturity_;
        double variance = capletVolatility().currentLink().blackVariance(d1, fixing, false);

        double shift = capletVolatility().currentLink().displacement();
        boolean shiftedLn =
                capletVolatility().currentLink().volatilityType() == ShiftedLognormal;

        double adjustment = shiftedLn
                ? ((fixing + shift) * (fixing + shift) *
                variance * tau / (1.0 + fixing * tau))
                : (variance * tau / (1.0 + fixing * tau));

        if (timingAdjustment_ == BivariateLognormal) {
            QL_REQUIRE(!correlation_.empty(), "no correlation given");
            final Date d4 = coupon_.date();
            final Date d5 = TimeUtils.geq(d4, d3) ? d3 : d2;
            double tau2 = index_.dayCounter().yearFraction(d5, d4, new Date(), new Date());
            if (TimeUtils.geq(d4, d3))
                adjustment = 0.0;
            // if d4 < d2 (payment before index start) we just apply the
            // Black76 in arrears adjustment
            if (tau2 > 0.0) {
                double fixing2 =
                        (index_.forwardingTermStructure().currentLink().discount(d5, false) /
                                index_.forwardingTermStructure().currentLink().discount(d4, false) -
                                1.0) /
                                tau2;
                adjustment -= shiftedLn
                        ? (correlation_.currentLink().value() * tau2 * variance *
                        (fixing + shift) * (fixing2 + shift) /
                        (1.0 + fixing2 * tau2))
                        : (correlation_.currentLink().value() * tau2 * variance /
                        (1.0 + fixing2 * tau2));
            }
        }
        return fixing + adjustment;
    }
}
