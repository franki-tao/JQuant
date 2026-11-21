package jquant.pricingengines.swaption;

import jquant.CashFlow;
import jquant.Exercise;
import jquant.Handle;
import jquant.InterestRate;
import jquant.Option;
import jquant.Quote;
import jquant.cashflows.CashFlows;
import jquant.cashflows.FixedRateCoupon;
import jquant.instruments.FixedVsFloatingSwap;
import jquant.instruments.Settlement;
import jquant.instruments.Swaption;
import jquant.patterns.ObservableSettings;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.swaption.ConstantSwaptionVolatility;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;

import java.util.List;

import static jquant.Compounding.Compounded;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Generic Black-style-formula swaption engine, base class for the Black and
 * Bachelier swaption engines.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swaption/blackswaptionengine.hpp}. QuantLib uses a
 * template parameter ({@code Black76Spec}/{@code BachelierSpec}); Java uses
 * the abstract {@link #value}/{@link #vega}/{@link #delta} methods and
 * {@link #volatilityType()} implemented by the two concrete subclasses.
 */
public abstract class BlackStyleSwaptionEngine extends Swaption.engine {
    public enum CashAnnuityModel {
        SwapRate,
        DiscountCurve
    }

    protected final Handle<YieldTermStructure> discountCurve_;
    protected final Handle<SwaptionVolatilityStructure> vol_;
    protected final CashAnnuityModel model_;

    protected BlackStyleSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                       Handle<SwaptionVolatilityStructure> volatility,
                                       CashAnnuityModel model) {
        discountCurve_ = discountCurve;
        vol_ = volatility;
        model_ = model;
        registerWith(discountCurve_.get());
        registerWith(vol_.get());
    }

    /** volatility type used by the concrete spec. */
    protected abstract Sarb.VolatilityType volatilityType();

    protected abstract double value(Option.Type type,
                                    double strike,
                                    double atmForward,
                                    double stdDev,
                                    double annuity,
                                    double displacement);

    protected abstract double vega(double strike,
                                   double atmForward,
                                   double stdDev,
                                   double exerciseTime,
                                   double annuity,
                                   double displacement);

    protected abstract double delta(Option.Type type,
                                    double strike,
                                    double atmForward,
                                    double stdDev,
                                    double annuity,
                                    double displacement);

    /** Builds a constant volatility handle as the official templated constructors do. */
    protected static Handle<SwaptionVolatilityStructure> constantVol(
            double vol,
            DayCounter dc,
            double displacement,
            Sarb.VolatilityType type) {
        return new Handle<>(new ConstantSwaptionVolatility(
                0, new NullCalendar(), BusinessDayConvention.FOLLOWING,
                vol, dc, type, displacement), true);
    }

    protected static Handle<SwaptionVolatilityStructure> constantVol(
            Handle<Quote> vol,
            DayCounter dc,
            double displacement,
            Sarb.VolatilityType type) {
        return new Handle<>(new ConstantSwaptionVolatility(
                0, new NullCalendar(), BusinessDayConvention.FOLLOWING,
                vol, dc, type, displacement), true);
    }

    public Handle<YieldTermStructure> termStructure() {
        return discountCurve_;
    }

    public Handle<SwaptionVolatilityStructure> volatility() {
        return vol_;
    }

    @Override
    public void calculate() {
        final double basisPoint = 1.0e-4;

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not a European option");

        Date exerciseDate = arguments_.exercise.date(0);

        // The part of the swap preceding exerciseDate should be truncated to
        // avoid taking into account unwanted cashflows. For the moment we add
        // a check avoiding this situation.
        FixedVsFloatingSwap swap = arguments_.swap;

        List<CashFlow> fixedLeg = swap.fixedLeg();
        FixedRateCoupon firstCoupon = (FixedRateCoupon) fixedLeg.get(0);
        QL_REQUIRE(TimeUtils.geq(firstCoupon.accrualStartDate(), exerciseDate),
                "swap start (" + firstCoupon.accrualStartDate()
                        + ") before exercise date (" + exerciseDate
                        + ") not supported in Black swaption engine");

        double strike = swap.fixedRate();

        // using the discounting curve
        // swap.iborIndex() might be using a different forwarding curve
        DiscountingSwapEngine engine = new DiscountingSwapEngine(discountCurve_);
        // Drop (not defer) notifications while swapping the engine, as the
        // official ObservableSettings::disableUpdates() default does; deferring
        // them would invalidate the swaption we are currently calculating.
        // The official code has no try/finally; because ObservableSettings is
        // a process singleton, an exception between disable and enable would
        // otherwise leave updates disabled for the rest of the JVM.
        ObservableSettings.getInstance().disableUpdates(false);
        try {
            swap.setPricingEngine(engine);
        } finally {
            ObservableSettings.getInstance().enableUpdates();
        }
        Date valuationDate = results_.valuationDate = swap.valuationDate();
        double atmForward = swap.fairRate();

        // Volatilities are quoted for zero-spreaded swaps.
        // Therefore, any spread on the floating leg must be removed
        // with a corresponding correction on the fixed leg.
        double spread = swap.spread();
        if (spread != 0.0) {
            double correction = spread
                    * Math.abs(swap.floatingLegBPS() / swap.fixedLegBPS());
            strike -= correction;
            atmForward -= correction;
            results_.additionalResults.put("spreadCorrection", correction);
        } else {
            results_.additionalResults.put("spreadCorrection", 0.0);
        }
        results_.additionalResults.put("strike", strike);
        results_.additionalResults.put("atmForward", atmForward);

        double annuity;
        if (arguments_.settlementType == Settlement.Type.Physical
                || (arguments_.settlementType == Settlement.Type.Cash
                && arguments_.settlementMethod == Settlement.Method.CollateralizedCashPrice)) {
            annuity = Math.abs(swap.fixedLegBPS()) / basisPoint;
        } else if (arguments_.settlementType == Settlement.Type.Cash
                && arguments_.settlementMethod == Settlement.Method.ParYieldCurve) {
            DayCounter dayCount = firstCoupon.dayCounter();
            // we assume that the cash settlement date is equal
            // to the swap start date
            Date discountDate = model_ == CashAnnuityModel.DiscountCurve
                    ? firstCoupon.accrualStartDate()
                    : valuationDate;
            Frequency freq = Frequency.ANNUAL;
            Schedule fixedSchedule = swap.fixedSchedule();
            if (fixedSchedule.hasTenor()) {
                freq = fixedSchedule.tenor().frequency();
            }
            double fixedLegCashBPS =
                    CashFlows.bps(fixedLeg,
                            new InterestRate(atmForward, dayCount, Compounded, freq),
                            false, discountDate);
            annuity = Math.abs(fixedLegCashBPS / basisPoint)
                    * discountCurve_.currentLink().discount(discountDate);
        } else {
            QL_FAIL("invalid (settlementType, settlementMethod) pair");
            throw new IllegalArgumentException("invalid settlement pair");
        }
        results_.additionalResults.put("annuity", annuity);

        Schedule floatingSchedule = swap.floatingSchedule();
        List<Date> floatingDates = floatingSchedule.dates();
        double swapLength = vol_.currentLink().swapLength(
                floatingDates.get(0), floatingDates.get(floatingDates.size() - 1));

        // swapLength is rounded to whole months. To ensure we can read a
        // variance and a shift from vol_ we floor swapLength at 1/12 here
        // therefore.
        swapLength = Math.max(swapLength, 1.0 / 12.0);
        results_.additionalResults.put("swapLength", swapLength);

        SwaptionVolatilityStructure vol = vol_.currentLink();
        double variance = vol.blackVariance(exerciseDate, swapLength, strike);

        double displacement = vol.volatilityType() == ShiftedLognormal
                ? vol.shift(exerciseDate, swapLength) : 0.0;

        double stdDev = Math.sqrt(variance);
        results_.additionalResults.put("stdDev", stdDev);
        Option.Type w = swap.swapType() == jquant.instruments.Swap.Type.Payer
                ? Option.Type.Call : Option.Type.Put;
        results_.value = value(w, strike, atmForward, stdDev, annuity, displacement);

        double exerciseTime = vol.timeFromReference(exerciseDate);
        results_.additionalResults.put("vega",
                vega(strike, atmForward, stdDev, exerciseTime, annuity, displacement));
        results_.additionalResults.put("delta",
                delta(w, strike, atmForward, stdDev, annuity, displacement));
        results_.additionalResults.put("timeToExpiry", exerciseTime);
        results_.additionalResults.put("impliedVolatility",
                stdDev / Math.sqrt(exerciseTime));
        results_.additionalResults.put("forwardPrice",
                results_.value / discountCurve_.currentLink().discount(exerciseDate));
    }
}
