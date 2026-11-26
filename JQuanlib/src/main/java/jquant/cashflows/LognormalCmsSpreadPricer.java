package jquant.cashflows;

import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.indexes.SwapSpreadIndex;
import jquant.math.MathUtils;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.integrals.GaussHermiteIntegration;
import jquant.math.integrals.GaussianQuadrature;
import jquant.pricingengines.BlackFormula;
import jquant.termstructures.SmileSection;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_SQRTPI;
import static jquant.math.MathUtils.M_SQRT2;

/**
 * CMS spread coupon pricer as in Brigo, Mercurio, 13.6.2, with extensions for
 * shifted lognormal and normal dynamics as described in
 * <a href="http://ssrn.com/abstract=2686998">ssrn.com/abstract=2686998</a>.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/coupons/lognormalcmsspreadpricer.{hpp,cpp}}. The swap
 * rate adjustments of the two underlyings are computed with the given CMS
 * pricer (typically {@link LinearTsrPricer}).
 */
public class LognormalCmsSpreadPricer extends CmsSpreadCouponPricer {

    private final CmsCouponPricer cmsPricer_;
    private Handle<YieldTermStructure> couponDiscountCurve_;

    private CmsSpreadCoupon coupon_;

    private Date today_, fixingDate_, paymentDate_;
    private double fixingTime_;

    private double gearing_, spread_;
    private double spreadLegValue_;
    private double discount_;

    private SwapSpreadIndex index_;

    private final CumulativeNormalDistribution cnd_;
    private final GaussianQuadrature integrator_;

    private double swapRate1_, swapRate2_, gearing1_, gearing2_;
    private double adjustedRate1_, adjustedRate2_;
    private double vol1_, vol2_;
    private double mu1_, mu2_;
    private double rho_;

    private boolean inheritedVolatilityType_;
    private VolatilityType volType_;
    private double shift1_, shift2_;

    private double phi_, a_, b_, s1_, s2_, m1_, m2_, v1_, v2_, k_;
    private double alpha_, psi_;
    private Option.Type optionType_;

    private CmsCoupon c1_, c2_;

    /** Official defaults: empty discount curve, 16 points, inherited type. */
    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer,
                                    Handle<Quote> correlation) {
        this(cmsPricer, correlation, null, 16, null,
                MathUtils.NULL_REAL, MathUtils.NULL_REAL);
    }

    /** Official defaults: 16 points, inherited volatility type. */
    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer,
                                    Handle<Quote> correlation,
                                    Handle<YieldTermStructure> couponDiscountCurve) {
        this(cmsPricer, correlation, couponDiscountCurve, 16, null,
                MathUtils.NULL_REAL, MathUtils.NULL_REAL);
    }

    /** Official defaults: inherited volatility type, unspecified shifts. */
    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer,
                                    Handle<Quote> correlation,
                                    Handle<YieldTermStructure> couponDiscountCurve,
                                    int integrationPoints) {
        this(cmsPricer, correlation, couponDiscountCurve, integrationPoints,
                null, MathUtils.NULL_REAL, MathUtils.NULL_REAL);
    }

    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer,
                                    Handle<Quote> correlation,
                                    Handle<YieldTermStructure> couponDiscountCurve,
                                    int integrationPoints,
                                    VolatilityType volatilityType,
                                    double shift1,
                                    double shift2) {
        super(correlation);
        cmsPricer_ = cmsPricer;
        couponDiscountCurve_ = couponDiscountCurve == null
                ? new Handle<YieldTermStructure>(null, false)
                : couponDiscountCurve;

        registerWith(correlation == null ? null : correlation.get());
        if (!couponDiscountCurve_.empty())
            registerWith(couponDiscountCurve_.get());
        registerWith(cmsPricer_);

        QL_REQUIRE(integrationPoints >= 4,
                "at least 4 integration points should be used ("
                        + integrationPoints + ")");
        integrator_ = new GaussHermiteIntegration(integrationPoints);

        cnd_ = new CumulativeNormalDistribution(0.0, 1.0);

        if (volatilityType == null) {
            QL_REQUIRE(shift1 == MathUtils.NULL_REAL &&
                            shift2 == MathUtils.NULL_REAL,
                    "if volatility type is inherited, no shifts should be "
                            + "specified");
            inheritedVolatilityType_ = true;
            volType_ = cmsPricer_.swaptionVolatility().currentLink()
                    .volatilityType();
        } else {
            shift1_ = shift1 == MathUtils.NULL_REAL ? 0.0 : shift1;
            shift2_ = shift2 == MathUtils.NULL_REAL ? 0.0 : shift2;
            inheritedVolatilityType_ = false;
            volType_ = volatilityType;
        }
    }

    private double integrand(double x) {

        // this is Brigo, 13.16.2 with x = v / sqrt(2)

        double v = M_SQRT2 * x;
        double h = k_ - b_ * s2_ * Math.exp((m2_ - 0.5 * v2_ * v2_)
                * fixingTime_ + v2_ * Math.sqrt(fixingTime_) * v);
        double phi1, phi2;
        phi1 = cnd_.value(
                phi_ * (Math.log(a_ * s1_ / h) +
                        (m1_ + (0.5 - rho_ * rho_) * v1_ * v1_) * fixingTime_ +
                        rho_ * v1_ * Math.sqrt(fixingTime_) * v) /
                        (v1_ * Math.sqrt(fixingTime_ * (1.0 - rho_ * rho_))));
        phi2 = cnd_.value(
                phi_ * (Math.log(a_ * s1_ / h) +
                        (m1_ - 0.5 * v1_ * v1_) * fixingTime_ +
                        rho_ * v1_ * Math.sqrt(fixingTime_) * v) /
                        (v1_ * Math.sqrt(fixingTime_ * (1.0 - rho_ * rho_))));
        double f = a_ * phi_ * s1_ *
                Math.exp(m1_ * fixingTime_ -
                        0.5 * rho_ * rho_ * v1_ * v1_ * fixingTime_ +
                        rho_ * v1_ * Math.sqrt(fixingTime_) * v) *
                phi1 -
                phi_ * h * phi2;
        return Math.exp(-x * x) * f;
    }

    private double integrand_normal(double x) {

        // this is http://ssrn.com/abstract=2686998, 3.20 with x = s / sqrt(2)

        double s = M_SQRT2 * x;

        double beta = phi_ *
                (gearing1_ * adjustedRate1_ + gearing2_ * adjustedRate2_ - k_ +
                        Math.sqrt(fixingTime_) *
                                (rho_ * gearing1_ * vol1_ + gearing2_ * vol2_) * s);
        double f = MathUtils.close_enough(alpha_, 0.0)
                ? Math.max(beta, 0.0)
                : psi_ * alpha_ / (M_SQRTPI * M_SQRT2) *
                        Math.exp(-beta * beta / (2.0 * alpha_ * alpha_)) +
                        beta * (1.0 - cnd_.value(-psi_ * beta / alpha_));
        return Math.exp(-x * x) * f;
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {

        QL_REQUIRE(coupon instanceof CmsSpreadCoupon,
                "CMS spread coupon needed");
        coupon_ = (CmsSpreadCoupon) coupon;
        index_ = coupon_.swapSpreadIndex();
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();

        fixingDate_ = coupon_.fixingDate();
        paymentDate_ = coupon_.date();

        // if no coupon discount curve is given just use the discounting curve
        // from the _first_ swap index.
        // for rate calculation this curve cancels out in the computation, so
        // e.g. the discounting
        // swap engine will produce correct results, even if the
        // couponDiscountCurve is not set here.
        // only the price member function in this class will be dependent on the
        // coupon discount curve.

        today_ = Settings.instance.evaluationDate().Date();

        if (couponDiscountCurve_.empty())
            couponDiscountCurve_ =
                    index_.swapIndex1().exogenousDiscount()
                            ? index_.swapIndex1().discountingTermStructure()
                            : index_.swapIndex1().forwardingTermStructure();

        discount_ = TimeUtils.greater(paymentDate_,
                couponDiscountCurve_.currentLink().referenceDate())
                ? couponDiscountCurve_.currentLink().discount(paymentDate_)
                : 1.0;

        spreadLegValue_ = spread_ * coupon_.accrualPeriod() * discount_;

        gearing1_ = index_.gearing1();
        gearing2_ = index_.gearing2();

        QL_REQUIRE(gearing1_ > 0.0 && gearing2_ < 0.0,
                "gearing1 (" + gearing1_
                        + ") should be positive while gearing2 ("
                        + gearing2_ + ") should be negative");

        c1_ = new CmsCoupon(
                coupon_.date(), coupon_.nominal(), coupon_.accrualStartDate(),
                coupon_.accrualEndDate(), coupon_.fixingDays(),
                index_.swapIndex1(), 1.0, 0.0, coupon_.referencePeriodStart(),
                coupon_.referencePeriodEnd(), coupon_.dayCounter(),
                coupon_.isInArrears());

        c2_ = new CmsCoupon(
                coupon_.date(), coupon_.nominal(), coupon_.accrualStartDate(),
                coupon_.accrualEndDate(), coupon_.fixingDays(),
                index_.swapIndex2(), 1.0, 0.0, coupon_.referencePeriodStart(),
                coupon_.referencePeriodEnd(), coupon_.dayCounter(),
                coupon_.isInArrears());

        c1_.setPricer(cmsPricer_);
        c2_.setPricer(cmsPricer_);

        if (TimeUtils.greater(fixingDate_, today_)) {

            fixingTime_ = cmsPricer_.swaptionVolatility().currentLink()
                    .timeFromReference(fixingDate_);

            swapRate1_ = c1_.indexFixing();
            swapRate2_ = c2_.indexFixing();

            adjustedRate1_ = c1_.adjustedFixing();
            adjustedRate2_ = c2_.adjustedFixing();

            SwaptionVolatilityStructure swvol =
                    cmsPricer_.swaptionVolatility().currentLink();
            SwaptionVolatilityCube swcub =
                    swvol instanceof SwaptionVolatilityCube
                            ? (SwaptionVolatilityCube) swvol : null;

            if (inheritedVolatilityType_ && volType_ == VolatilityType.ShiftedLognormal) {
                shift1_ = swvol.shift(fixingDate_,
                        index_.swapIndex1().tenor());
                shift2_ = swvol.shift(fixingDate_,
                        index_.swapIndex2().tenor());
            }

            if (swcub == null) {
                // not a cube, just an atm surface given, so we can
                // not easily convert volatilities and just forbid it
                QL_REQUIRE(inheritedVolatilityType_,
                        "if only an atm surface is given, the volatility "
                                + "type must be inherited");
                vol1_ = swvol.volatility(fixingDate_,
                        index_.swapIndex1().tenor(), swapRate1_);
                vol2_ = swvol.volatility(fixingDate_,
                        index_.swapIndex2().tenor(), swapRate2_);
            } else {
                SmileSection s1 = swcub.smileSection(fixingDate_,
                        index_.swapIndex1().tenor());
                vol1_ = s1.volatility(swapRate1_, volType_, shift1_);
                SmileSection s2 = swcub.smileSection(fixingDate_,
                        index_.swapIndex2().tenor());
                vol2_ = s2.volatility(swapRate2_, volType_, shift2_);
            }

            if (volType_ == VolatilityType.ShiftedLognormal) {
                mu1_ = 1.0 / fixingTime_ *
                        Math.log((adjustedRate1_ + shift1_) /
                                (swapRate1_ + shift1_));
                mu2_ = 1.0 / fixingTime_ *
                        Math.log((adjustedRate2_ + shift2_) /
                                (swapRate2_ + shift2_));
            }
            // for the normal volatility case we do not need the drifts
            // but rather use adjusted rates directly in the integrand

            rho_ = Math.max(Math.min(correlation().currentLink().value(),
                    0.9999), -0.9999); // avoid division by zero in integrand
        } else {
            // fixing is in the past or today
            adjustedRate1_ = c1_.indexFixing();
            adjustedRate2_ = c2_.indexFixing();
        }
    }

    private double optionletPrice(Option.Type optionType, double strike) {
        // this method is only called for future fixings
        optionType_ = optionType;
        phi_ = optionType == Option.Type.Call ? 1.0 : -1.0;
        double res = 0.0;
        if (volType_ == VolatilityType.ShiftedLognormal) {
            // (shifted) lognormal volatility
            final double displacedStrike =
                    strike + gearing1_ * shift1_ + gearing2_ * shift2_;
            if (displacedStrike >= 0.0) {
                a_ = gearing1_;
                b_ = gearing2_;
                s1_ = swapRate1_ + shift1_;
                s2_ = swapRate2_ + shift2_;
                m1_ = mu1_;
                m2_ = mu2_;
                v1_ = vol1_;
                v2_ = vol2_;
                k_ = displacedStrike;
            } else {
                a_ = -gearing2_;
                b_ = -gearing1_;
                s1_ = swapRate2_ + shift2_;
                s2_ = swapRate1_ + shift1_;
                m1_ = mu2_;
                m2_ = mu1_;
                v1_ = vol2_;
                v2_ = vol1_;
                k_ = -displacedStrike;
                res += phi_ * (gearing1_ * adjustedRate1_ +
                        gearing2_ * adjustedRate2_ - strike);
            }
            res += 1.0 / M_SQRTPI * integrator_.value(x -> integrand(x));
        } else {
            // normal volatility
            double forward = gearing1_ * adjustedRate1_ +
                    gearing2_ * adjustedRate2_;
            double stddev =
                    Math.sqrt(fixingTime_ *
                            (gearing1_ * gearing1_ * vol1_ * vol1_ +
                                    gearing2_ * gearing2_ * vol2_ * vol2_ +
                                    2.0 * gearing1_ * gearing2_ * rho_ * vol1_ * vol2_));
            res = BlackFormula.bachelierBlackFormula(optionType_, strike,
                    forward, stddev, 1.0);
        }
        return res * discount_ * coupon_.accrualPeriod();
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double capletPrice(double effectiveCap) {
        // caplet is equivalent to call option on fixing
        if (TimeUtils.leq(fixingDate_, today_)) {
            // the fixing is determined
            final double Rs = Math.max(
                    coupon_.index().fixing(fixingDate_, false) - effectiveCap,
                    0.);
            return gearing_ * Rs * coupon_.accrualPeriod() * discount_;
        } else {
            double capletPrice = optionletPrice(Option.Type.Call, effectiveCap);
            return gearing_ * capletPrice;
        }
    }

    @Override
    public double capletRate(double effectiveCap) {
        return capletPrice(effectiveCap) /
                (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        // floorlet is equivalent to put option on fixing
        if (TimeUtils.leq(fixingDate_, today_)) {
            // the fixing is determined
            final double Rs = Math.max(
                    effectiveFloor - coupon_.index().fixing(fixingDate_, false),
                    0.);
            return gearing_ * Rs * coupon_.accrualPeriod() * discount_;
        } else {
            double floorletPrice = optionletPrice(Option.Type.Put, effectiveFloor);
            return gearing_ * floorletPrice;
        }
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        return floorletPrice(effectiveFloor) /
                (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double swapletPrice() {
        return gearing_ * coupon_.accrualPeriod() * discount_ *
                (gearing1_ * adjustedRate1_ + gearing2_ * adjustedRate2_) +
                spreadLegValue_;
    }
}
