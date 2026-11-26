package jquant.cashflows;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.indexes.SwapIndex;
import jquant.instruments.FixedVsFloatingSwap;
import jquant.instruments.VanillaSwap;
import jquant.math.Function;
import jquant.math.integrals.GaussKronrodNonAdaptive;
import jquant.math.integrals.Integrator;
import jquant.math.solvers1d.Brent;
import jquant.termstructures.SmileSection;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.AtmSmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * CMS coupon pricer using a linear terminal swap rate model.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/lineartsrpricer.hpp/.cpp}.
 * The slope parameter is linked to a gaussian short rate model; reference
 * Andersen, Piterbarg, Interest Rate Modeling, 16.3.2.
 *
 * <p>The OIS branch of the official initializer
 * ({@code OvernightIndexedSwapIndex}) is not ported because the Java port has
 * no such index; the vanilla {@link SwapIndex} branch is complete.
 */
public class LinearTsrPricer extends CmsCouponPricer
        implements MeanRevertingPricer {

    private static final double defaultLowerBound = 0.0001;
    private static final double defaultUpperBound = 2.0000;

    /** Official {@code LinearTsrPricer::Settings}. */
    public static class Settings {

        public enum Strategy {
            RateBound, VegaRatio, PriceThreshold, BSStdDevs
        }

        public Strategy strategy_ = Strategy.RateBound;
        public double vegaRatio_ = 0.01;
        public double priceThreshold_ = 1.0e-8;
        public double stdDevs_ = 3.0;
        public double lowerRateBound_;
        public double upperRateBound_;
        public boolean defaultBounds_ = true;

        public Settings() {
            lowerRateBound_ = defaultLowerBound;
            upperRateBound_ = defaultUpperBound;
        }

        public Settings withRateBound(double lowerRateBound,
                                      double upperRateBound) {
            strategy_ = Strategy.RateBound;
            this.lowerRateBound_ = lowerRateBound;
            this.upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public Settings withRateBound() {
            return withRateBound(defaultLowerBound, defaultUpperBound);
        }

        public Settings withVegaRatio(double vegaRatio) {
            strategy_ = Strategy.VegaRatio;
            vegaRatio_ = vegaRatio;
            lowerRateBound_ = defaultLowerBound;
            upperRateBound_ = defaultUpperBound;
            defaultBounds_ = true;
            return this;
        }

        public Settings withVegaRatio() {
            return withVegaRatio(0.01);
        }

        public Settings withVegaRatio(double vegaRatio,
                                      double lowerRateBound,
                                      double upperRateBound) {
            strategy_ = Strategy.VegaRatio;
            vegaRatio_ = vegaRatio;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public Settings withPriceThreshold(double priceThreshold) {
            strategy_ = Strategy.PriceThreshold;
            priceThreshold_ = priceThreshold;
            lowerRateBound_ = defaultLowerBound;
            upperRateBound_ = defaultUpperBound;
            defaultBounds_ = true;
            return this;
        }

        public Settings withPriceThreshold() {
            return withPriceThreshold(1.0e-8);
        }

        public Settings withPriceThreshold(double priceThreshold,
                                           double lowerRateBound,
                                           double upperRateBound) {
            strategy_ = Strategy.PriceThreshold;
            priceThreshold_ = priceThreshold;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public Settings withBSStdDevs(double stdDevs) {
            strategy_ = Strategy.BSStdDevs;
            stdDevs_ = stdDevs;
            lowerRateBound_ = defaultLowerBound;
            upperRateBound_ = defaultUpperBound;
            defaultBounds_ = true;
            return this;
        }

        public Settings withBSStdDevs() {
            return withBSStdDevs(3.0);
        }

        public Settings withBSStdDevs(double stdDevs,
                                      double lowerRateBound,
                                      double upperRateBound) {
            strategy_ = Strategy.BSStdDevs;
            stdDevs_ = stdDevs;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }
    }

    private Handle<Quote> meanReversion_;

    private Handle<YieldTermStructure> forwardCurve_;
    private Handle<YieldTermStructure> discountCurve_;
    private final Handle<YieldTermStructure> couponDiscountCurve_;

    private CmsCoupon coupon_;

    private Date today_;
    private Date paymentDate_;
    private Date fixingDate_;

    private double gearing_;
    private double spread_;

    private Period swapTenor_;
    private double spreadLegValue_;
    private double swapRateValue_;
    private double couponDiscountRatio_;
    private double discountCurvePaymentDiscount_;
    private double annuity_;

    private SwapIndex swapIndex_;
    private FixedVsFloatingSwap swap_;
    private SmileSection smileSection_;

    private final Settings settings_;
    private final DayCounter volDayCounter_;
    private final Integrator integrator_;

    private double adjustedLowerBound_;
    private double adjustedUpperBound_;

    private double a_;
    private double b_;

    public LinearTsrPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                           Handle<Quote> meanReversion) {
        this(swaptionVol, meanReversion, null, new Settings(), null);
    }

    /** Official 3-argument constructor with a coupon discount curve. */
    public LinearTsrPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                           Handle<Quote> meanReversion,
                           Handle<YieldTermStructure> couponDiscountCurve) {
        this(swaptionVol, meanReversion, couponDiscountCurve,
                new Settings(), null);
    }

    public LinearTsrPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                           Handle<Quote> meanReversion,
                           Handle<YieldTermStructure> couponDiscountCurve,
                           Settings settings,
                           Integrator integrator) {
        super(swaptionVol);
        meanReversion_ = meanReversion == null
                ? new Handle<Quote>(null, false) : meanReversion;
        couponDiscountCurve_ = couponDiscountCurve == null
                ? new Handle<YieldTermStructure>(null, false)
                : couponDiscountCurve;
        settings_ = settings == null ? new Settings() : settings;
        volDayCounter_ = swaptionVol.currentLink().dayCounter();
        integrator_ = integrator == null
                ? new GaussKronrodNonAdaptive(1e-10, 5000, 1e-10)
                : integrator;

        if (!couponDiscountCurve_.empty())
            registerWith(couponDiscountCurve_.get());

        // The official class does not register the mean-reversion handle in
        // its constructor; this port follows the repository's PR101 rule and
        // always registers the Handle's Link instead.
        registerWith(meanReversion_.get());
    }

    private double GsrG(Date d) {
        double yf = volDayCounter_.yearFraction(fixingDate_, d);
        double meanReversion = meanReversion_.currentLink().value();
        if (Math.abs(meanReversion) < 1.0e-4)
            return yf;
        return (1.0 - Math.exp(-meanReversion * yf)) / meanReversion;
    }

    private double singularTerms(Option.Type type, double strike) {
        double omega = type == Option.Type.Call ? 1.0 : -1.0;
        double s1 = Math.max(omega * (swapRateValue_ - strike), 0.0)
                * (a_ * swapRateValue_ + b_);
        double s2 = (a_ * strike + b_)
                * smileSection_.optionPrice(strike,
                strike < swapRateValue_ ? Option.Type.Put : Option.Type.Call,
                1.0);
        return s1 + s2;
    }

    private double integrand(double strike) {
        return 2.0 * a_ * smileSection_.optionPrice(strike,
                strike < swapRateValue_ ? Option.Type.Put : Option.Type.Call,
                1.0);
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL_REQUIRE(coupon instanceof CmsCoupon, "CMS coupon needed");
        coupon_ = (CmsCoupon) coupon;
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();

        fixingDate_ = coupon_.fixingDate().copy();
        paymentDate_ = coupon_.date().copy();
        swapIndex_ = coupon_.swapIndex();

        forwardCurve_ = swapIndex_.forwardingTermStructure();
        if (swapIndex_.exogenousDiscount())
            discountCurve_ = swapIndex_.discountingTermStructure();
        else
            discountCurve_ = forwardCurve_;

        // if no coupon discount curve is given just use the discounting curve
        // from the swap index. for rate calculation this curve cancels out in
        // the computation, so e.g. the discounting swap engine will produce
        // correct results, even if the couponDiscountCurve is not set here.
        // only the price member function in this class will be dependent on
        // the coupon discount curve.

        today_ = jquant.Settings.instance.evaluationDate().Date();

        double couponCurvePaymentDiscount;
        if (!couponDiscountCurve_.empty()
                && TimeUtils.greater(paymentDate_,
                couponDiscountCurve_.currentLink().referenceDate())) {
            couponCurvePaymentDiscount =
                    couponDiscountCurve_.currentLink().discount(paymentDate_);
        } else {
            couponCurvePaymentDiscount = 1.0;
        }

        if (TimeUtils.greater(paymentDate_,
                discountCurve_.currentLink().referenceDate())) {
            discountCurvePaymentDiscount_ =
                    discountCurve_.currentLink().discount(paymentDate_);
        } else {
            discountCurvePaymentDiscount_ = 1.0;
        }

        couponDiscountRatio_ = couponCurvePaymentDiscount
                / discountCurvePaymentDiscount_;

        spreadLegValue_ = spread_ * coupon_.accrualPeriod()
                * discountCurvePaymentDiscount_ * couponDiscountRatio_;

        if (TimeUtils.greater(fixingDate_, today_)) {
            swapTenor_ = swapIndex_.tenor();

            // Official has an OvernightIndexedSwapIndex branch here; that
            // index is not ported, so the vanilla branch is used.
            VanillaSwap vanillaSwap = swapIndex_.underlyingSwap(fixingDate_);
            swap_ = vanillaSwap;

            swapRateValue_ = swap_.fairRate();
            annuity_ = 1.0e4 * Math.abs(swap_.fixedLegBPS());
            List<CashFlow> swapFixedLeg = swap_.fixedLeg();

            SmileSection sectionTmp = swaptionVolatility().currentLink()
                    .smileSection(fixingDate_, swapTenor_);

            adjustedLowerBound_ = settings_.lowerRateBound_;
            adjustedUpperBound_ = settings_.upperRateBound_;

            if (sectionTmp.volatilityType() == VolatilityType.Normal) {
                // adjust lower bound if it was not set explicitly
                if (settings_.defaultBounds_)
                    adjustedLowerBound_ = Math.min(adjustedLowerBound_,
                            -adjustedUpperBound_);
            } else {
                // adjust bounds by section's shift
                adjustedLowerBound_ -= sectionTmp.shift();
                adjustedUpperBound_ -= sectionTmp.shift();
            }

            // if the section does not provide an atm level, enhance it to have
            // one, no need to exit with an exception ...
            if (Double.isNaN(sectionTmp.atmLevel()))
                smileSection_ = AtmSmileSection.of(sectionTmp, swapRateValue_);
            else
                smileSection_ = sectionTmp;

            // compute linear model's parameters
            double gx = 0.0;
            double gy = 0.0;
            for (CashFlow cashFlow : swapFixedLeg) {
                Coupon c = (Coupon) cashFlow;
                double yf = c.accrualPeriod();
                Date d = c.date();
                double pv = yf
                        * discountCurve_.currentLink().discount(d);
                gx += pv * GsrG(d);
                gy += pv;
            }

            double gamma = gx / gy;
            Date lastd = swapFixedLeg.get(swapFixedLeg.size() - 1).date();

            a_ = discountCurve_.currentLink().discount(paymentDate_)
                    * (gamma - GsrG(paymentDate_))
                    / (discountCurve_.currentLink().discount(lastd)
                    * GsrG(lastd) + swapRateValue_ * gy * gamma);

            b_ = discountCurve_.currentLink().discount(paymentDate_) / gy
                    - a_ * swapRateValue_;
        }
    }

    private double strikeFromVegaRatio(double ratio, Option.Type optionType,
                                       double referenceStrike) {
        double a, b, min, max, k;
        if (optionType == Option.Type.Call) {
            a = swapRateValue_;
            min = referenceStrike;
            b = max = k = Math.min(smileSection_.maxStrike(),
                    adjustedUpperBound_);
        } else {
            a = min = k = Math.max(smileSection_.minStrike(),
                    adjustedLowerBound_);
            b = swapRateValue_;
            max = referenceStrike;
        }

        final double targetVega = smileSection_.vega(swapRateValue_, 1.0)
                * ratio;
        Function h = strike -> smileSection_.vega(strike, 1.0) - targetVega;
        Brent solver = new Brent();

        try {
            k = solver.solve(h, 1.0e-5, (a + b) / 2.0, a, b);
        } catch (RuntimeException e) {
            // use default value set above
        }

        return Math.min(Math.max(k, min), max);
    }

    private double strikeFromPrice(double price, Option.Type optionType,
                                   double referenceStrike) {
        double a, b, min, max, k;
        if (optionType == Option.Type.Call) {
            a = swapRateValue_;
            min = referenceStrike;
            b = max = k = Math.min(smileSection_.maxStrike(),
                    adjustedUpperBound_);
        } else {
            a = min = k = Math.max(smileSection_.minStrike(),
                    adjustedLowerBound_);
            b = swapRateValue_;
            max = referenceStrike;
        }

        final Option.Type type = optionType;
        final double targetPrice = price;
        Function h = strike -> smileSection_.optionPrice(strike, type, 1.0)
                - targetPrice;
        Brent solver = new Brent();

        try {
            k = solver.solve(h, 1.0e-5, swapRateValue_, a, b);
        } catch (RuntimeException e) {
            // use default value set above
        }

        return Math.min(Math.max(k, min), max);
    }

    private double optionletPrice(Option.Type optionType, double strike) {
        if (optionType == Option.Type.Call && strike >= adjustedUpperBound_)
            return 0.0;
        if (optionType == Option.Type.Put && strike <= adjustedLowerBound_)
            return 0.0;

        // determine lower or upper integration bound (depending on option type)
        double lower = strike;
        double upper = strike;

        switch (settings_.strategy_) {
            case RateBound: {
                if (optionType == Option.Type.Call)
                    upper = adjustedUpperBound_;
                else
                    lower = adjustedLowerBound_;
                break;
            }
            case VegaRatio: {
                // strikeFromVegaRatio ensures that returned strike is on the
                // expected side of strike
                double bound = strikeFromVegaRatio(settings_.vegaRatio_,
                        optionType, strike);
                if (optionType == Option.Type.Call)
                    upper = Math.min(bound, adjustedUpperBound_);
                else
                    lower = Math.max(bound, adjustedLowerBound_);
                break;
            }
            case PriceThreshold: {
                // strikeFromPrice ensures that returned strike is on the
                // expected side of strike. Official passes vegaRatio_ here.
                double bound = strikeFromPrice(settings_.vegaRatio_,
                        optionType, strike);
                if (optionType == Option.Type.Call)
                    upper = Math.min(bound, adjustedUpperBound_);
                else
                    lower = Math.max(bound, adjustedLowerBound_);
                break;
            }
            case BSStdDevs: {
                double atm = smileSection_.atmLevel();
                double atmVol = smileSection_.volatility(atm);
                double shift = smileSection_.shift();
                double lowerTmp, upperTmp;
                if (smileSection_.volatilityType()
                        == VolatilityType.ShiftedLognormal) {
                    upperTmp = (atm + shift)
                            * Math.exp(settings_.stdDevs_ * atmVol
                            - 0.5 * atmVol * atmVol
                            * smileSection_.exerciseTime())
                            - shift;
                    lowerTmp = (atm + shift)
                            * Math.exp(-settings_.stdDevs_ * atmVol
                            - 0.5 * atmVol * atmVol
                            * smileSection_.exerciseTime())
                            - shift;
                } else {
                    double tmp = settings_.stdDevs_ * atmVol
                            * Math.sqrt(smileSection_.exerciseTime());
                    upperTmp = atm + tmp;
                    lowerTmp = atm - tmp;
                }
                upper = Math.min(upperTmp - shift, adjustedUpperBound_);
                lower = Math.max(lowerTmp - shift, adjustedLowerBound_);
                break;
            }
            default:
                QL_FAIL("Unknown strategy (" + settings_.strategy_ + ")");
        }

        // compute the relevant integral
        double result = 0.0;
        double tmpBound;
        if (upper > lower) {
            tmpBound = Math.min(upper, swapRateValue_);
            if (tmpBound > lower)
                result += integrator_.value(this::integrand, lower, tmpBound);
            tmpBound = Math.max(lower, swapRateValue_);
            if (upper > tmpBound)
                result += integrator_.value(this::integrand, tmpBound, upper);
            result *= optionType == Option.Type.Call ? 1.0 : -1.0;
        }

        result += singularTerms(optionType, strike);

        return annuity_ * result * couponDiscountRatio_
                * coupon_.accrualPeriod();
    }

    @Override
    public double meanReversion() {
        return meanReversion_.currentLink().value();
    }

    @Override
    public void setMeanReversion(Handle<Quote> meanReversion) {
        unregisterWith(meanReversion_.get());
        meanReversion_ = meanReversion == null
                ? new Handle<Quote>(null, false) : meanReversion;
        registerWith(meanReversion_.get());
        update();
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (coupon_.accrualPeriod()
                * discountCurvePaymentDiscount_ * couponDiscountRatio_);
    }

    @Override
    public double capletPrice(double effectiveCap) {
        // caplet is equivalent to call option on fixing
        if (TimeUtils.leq(fixingDate_, today_)) {
            // the fixing is determined
            double Rs = Math.max(
                    coupon_.swapIndex().fixing(fixingDate_, false) - effectiveCap,
                    0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod()
                    * discountCurvePaymentDiscount_ * couponDiscountRatio_);
        }
        return gearing_ * optionletPrice(Option.Type.Call, effectiveCap);
    }

    @Override
    public double capletRate(double effectiveCap) {
        return capletPrice(effectiveCap) / (coupon_.accrualPeriod()
                * discountCurvePaymentDiscount_ * couponDiscountRatio_);
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        // floorlet is equivalent to put option on fixing
        if (TimeUtils.leq(fixingDate_, today_)) {
            // the fixing is determined
            double Rs = Math.max(
                    effectiveFloor - coupon_.swapIndex().fixing(fixingDate_, false),
                    0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod()
                    * discountCurvePaymentDiscount_ * couponDiscountRatio_);
        }
        return gearing_ * optionletPrice(Option.Type.Put, effectiveFloor);
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        return floorletPrice(effectiveFloor) / (coupon_.accrualPeriod()
                * discountCurvePaymentDiscount_ * couponDiscountRatio_);
    }

    @Override
    public double swapletPrice() {
        if (TimeUtils.leq(fixingDate_, today_)) {
            // the fixing is determined
            double Rs = coupon_.swapIndex().fixing(fixingDate_, false);
            return (gearing_ * Rs + spread_) * (coupon_.accrualPeriod()
                    * discountCurvePaymentDiscount_ * couponDiscountRatio_);
        }
        double atmCapletPrice = optionletPrice(Option.Type.Call,
                swapRateValue_);
        double atmFloorletPrice = optionletPrice(Option.Type.Put,
                swapRateValue_);
        return gearing_ * (coupon_.accrualPeriod()
                * discountCurvePaymentDiscount_ * swapRateValue_
                * couponDiscountRatio_
                + atmCapletPrice - atmFloorletPrice)
                + spreadLegValue_;
    }
}
