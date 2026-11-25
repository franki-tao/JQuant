package jquant.cashflows;

import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.indexes.SwapIndex;
import jquant.instruments.VanillaSwap;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Base class for CMS coupon pricing via Hagan's static replication.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp/.cpp}
 * ({@code HaganPricer}). Concrete subclasses implement
 * {@link #optionletPrice(Option.Type,double)}:
 * {@link NumericHaganPricer} and {@link AnalyticHaganPricer}.
 */
public abstract class HaganPricer extends CmsCouponPricer
        implements MeanRevertingPricer {

    protected YieldTermStructure rateCurve_;
    protected final GFunctionFactory.YieldCurveModel modelOfYieldCurve_;
    protected GFunction gFunction_;
    protected CmsCoupon coupon_;
    protected Date paymentDate_;
    protected Date fixingDate_;
    protected double swapRateValue_;
    protected double discount_;
    protected double annuity_;
    protected double gearing_;
    protected double spread_;
    protected double spreadLegValue_;
    protected double cutoffForCaplet_ = 2.0;
    protected double cutoffForFloorlet_ = 0.0;
    protected Handle<Quote> meanReversion_;
    protected Period swapTenor_;
    protected VanillaOptionPricer vanillaOptionPricer_;

    protected HaganPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                          GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                          Handle<Quote> meanReversion) {
        super(swaptionVol);
        modelOfYieldCurve_ = modelOfYieldCurve;
        meanReversion_ = meanReversion == null
                ? new Handle<Quote>(null, false) : meanReversion;
        registerWith(meanReversion_.get());
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
    public void initialize(final FloatingRateCoupon coupon) {
        QL_REQUIRE(coupon instanceof CmsCoupon, "CMS coupon needed");
        coupon_ = (CmsCoupon) coupon;
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();
        double accrualPeriod = coupon_.accrualPeriod();
        QL_REQUIRE(accrualPeriod != 0.0, "null accrual period");

        fixingDate_ = coupon_.fixingDate().copy();
        paymentDate_ = coupon_.date().copy();
        SwapIndex swapIndex = coupon_.swapIndex();
        rateCurve_ = swapIndex.discountingTermStructure().empty()
                ? swapIndex.forwardingTermStructure().currentLink()
                : swapIndex.discountingTermStructure().currentLink();

        Date today = Settings.instance.evaluationDate().Date();

        if (TimeUtils.greater(paymentDate_, today))
            discount_ = rateCurve_.discount(paymentDate_);
        else
            discount_ = 1.0;

        spreadLegValue_ = spread_ * accrualPeriod * discount_;

        if (TimeUtils.greater(fixingDate_, today)) {
            swapTenor_ = swapIndex.tenor();
            VanillaSwap swap = swapIndex.underlyingSwap(fixingDate_);

            swapRateValue_ = swap.fairRate();

            final double bp = 1.0e-4;
            annuity_ = Math.abs(swap.fixedLegBPS() / bp);

            int q = swapIndex.fixedLegTenor().frequency().getValue();
            Schedule schedule = swap.fixedSchedule();
            DayCounter dc = swapIndex.dayCounter();
            Date referenceDate = rateCurve_.referenceDate();
            double startTime = dc.yearFraction(referenceDate,
                    swap.startDate());
            double swapFirstPaymentTime = dc.yearFraction(referenceDate,
                    schedule.date(1));
            double paymentTime = dc.yearFraction(referenceDate, paymentDate_);
            double delta = (paymentTime - startTime)
                    / (swapFirstPaymentTime - startTime);

            switch (modelOfYieldCurve_) {
                case Standard:
                    gFunction_ = GFunctionFactory.newGFunctionStandard(q,
                            delta, swapTenor_.length());
                    break;
                case ExactYield:
                    gFunction_ = GFunctionFactory.newGFunctionExactYield(
                            coupon_);
                    break;
                case ParallelShifts: {
                    Handle<Quote> nullMeanReversionQuote = new Handle<>(
                            new SimpleQuote(0.0), true);
                    gFunction_ = GFunctionFactory.newGFunctionWithShifts(
                            coupon_, nullMeanReversionQuote);
                    break;
                }
                case NonParallelShifts:
                    gFunction_ = GFunctionFactory.newGFunctionWithShifts(
                            coupon_, meanReversion_);
                    break;
                default:
                    QL_FAIL("unknown/illegal gFunction type");
            }
            vanillaOptionPricer_ = new MarketQuotedOptionPricer(
                    swapRateValue_, fixingDate_, swapTenor_,
                    swaptionVolatility().currentLink());
        }
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double capletPrice(double effectiveCap) {
        // caplet is equivalent to call option on fixing
        Date today = Settings.instance.evaluationDate().Date();
        if (TimeUtils.leq(fixingDate_, today)) {
            // the fixing is determined
            double Rs = Math.max(
                    coupon_.swapIndex().fixing(fixingDate_, false) - effectiveCap,
                    0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod() * discount_);
        }
        double capletPrice = 0.0;
        if (swaptionVolatility().currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            final double cutoffNearZero = 1e-10;
            if (effectiveCap < cutoffForCaplet_) {
                double effectiveStrikeForMax = Math.max(effectiveCap,
                        cutoffNearZero);
                capletPrice = optionletPrice(Option.Type.Call,
                        effectiveStrikeForMax);
            }
        } else {
            capletPrice = optionletPrice(Option.Type.Call, effectiveCap);
        }
        return gearing_ * capletPrice;
    }

    @Override
    public double capletRate(double effectiveCap) {
        return capletPrice(effectiveCap)
                / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        // floorlet is equivalent to put option on fixing
        Date today = Settings.instance.evaluationDate().Date();
        if (TimeUtils.leq(fixingDate_, today)) {
            // the fixing is determined
            double Rs = Math.max(
                    effectiveFloor - coupon_.swapIndex().fixing(fixingDate_, false),
                    0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod() * discount_);
        }
        double floorletPrice = 0.0;
        if (swaptionVolatility().currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            final double cutoffNearZero = 1e-10;
            if (effectiveFloor > cutoffForFloorlet_) {
                double effectiveStrikeForMin = Math.max(effectiveFloor,
                        cutoffNearZero);
                floorletPrice = optionletPrice(Option.Type.Put,
                        effectiveStrikeForMin);
            }
        } else {
            floorletPrice = optionletPrice(Option.Type.Put, effectiveFloor);
        }
        return gearing_ * floorletPrice;
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        return floorletPrice(effectiveFloor)
                / (coupon_.accrualPeriod() * discount_);
    }

    protected abstract double optionletPrice(Option.Type optionType,
                                             double strike);

    @Override
    public abstract double swapletPrice();
}
