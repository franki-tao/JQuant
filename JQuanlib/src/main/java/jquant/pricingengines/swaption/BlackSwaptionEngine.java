package jquant.pricingengines.swaption;

import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.pricingengines.BlackFormula;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.DayCounter;
import jquant.time.daycounters.Actual365Fixed;

/**
 * Shifted-lognormal Black-formula swaption engine.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swaption/blackswaptionengine.hpp}
 * ({@code Black76Spec}).
 *
 * <p>The engine assumes that the exercise date lies before the start date of
 * the passed swap.
 */
public class BlackSwaptionEngine extends BlackStyleSwaptionEngine {

    public BlackSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                               double vol,
                               DayCounter dc,
                               double displacement,
                               CashAnnuityModel model) {
        super(discountCurve, constantVol(vol, dc, displacement, Sarb.VolatilityType.ShiftedLognormal), model);
    }

    public BlackSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                               double vol,
                               DayCounter dc,
                               double displacement) {
        this(discountCurve, vol, dc, displacement, CashAnnuityModel.DiscountCurve);
    }

    public BlackSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                               Handle<Quote> vol,
                               DayCounter dc,
                               double displacement,
                               CashAnnuityModel model) {
        super(discountCurve, constantVol(vol, dc, displacement, Sarb.VolatilityType.ShiftedLognormal), model);
    }

    public BlackSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                               Handle<Quote> vol,
                               DayCounter dc,
                               double displacement) {
        this(discountCurve, vol, dc, displacement, CashAnnuityModel.DiscountCurve);
    }

    public BlackSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                               Handle<Quote> vol) {
        this(discountCurve, vol, new Actual365Fixed(), 0.0, CashAnnuityModel.DiscountCurve);
    }

    public BlackSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                               Handle<SwaptionVolatilityStructure> vol,
                               CashAnnuityModel model) {
        super(discountCurve, vol, model);
    }

    /**
     * Java cannot overload on {@code Handle<Quote>} vs
     * {@code Handle<SwaptionVolatilityStructure>} (same erasure). The
     * official two-argument structure constructor is provided as a factory.
     */
    public static BlackSwaptionEngine withVolatility(
            Handle<YieldTermStructure> discountCurve,
            Handle<SwaptionVolatilityStructure> vol) {
        return new BlackSwaptionEngine(discountCurve, vol, CashAnnuityModel.DiscountCurve);
    }

    @Override
    protected Sarb.VolatilityType volatilityType() {
        return Sarb.VolatilityType.ShiftedLognormal;
    }

    @Override
    protected double value(Option.Type type,
                           double strike,
                           double atmForward,
                           double stdDev,
                           double annuity,
                           double displacement) {
        return BlackFormula.blackFormula(type, strike, atmForward, stdDev,
                annuity, displacement);
    }

    @Override
    protected double vega(double strike,
                          double atmForward,
                          double stdDev,
                          double exerciseTime,
                          double annuity,
                          double displacement) {
        return Math.sqrt(exerciseTime)
                * BlackFormula.blackFormulaStdDevDerivative(strike, atmForward,
                stdDev, annuity, displacement);
    }

    @Override
    protected double delta(Option.Type type,
                           double strike,
                           double atmForward,
                           double stdDev,
                           double annuity,
                           double displacement) {
        return BlackFormula.blackFormulaForwardDerivative(type, strike,
                atmForward, stdDev, annuity, displacement);
    }
}
