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
 * Normal Bachelier-formula swaption engine.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swaption/blackswaptionengine.hpp}
 * ({@code BachelierSpec}).
 *
 * <p>The engine assumes that the exercise date lies before the start date of
 * the passed swap.
 */
public class BachelierSwaptionEngine extends BlackStyleSwaptionEngine {

    public BachelierSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                   double vol,
                                   DayCounter dc,
                                   CashAnnuityModel model) {
        super(discountCurve, constantVol(vol, dc, 0.0, Sarb.VolatilityType.Normal), model);
    }

    public BachelierSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                   double vol,
                                   DayCounter dc) {
        this(discountCurve, vol, dc, CashAnnuityModel.DiscountCurve);
    }

    public BachelierSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                   Handle<Quote> vol,
                                   DayCounter dc,
                                   CashAnnuityModel model) {
        super(discountCurve, constantVol(vol, dc, 0.0, Sarb.VolatilityType.Normal), model);
    }

    public BachelierSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                   Handle<Quote> vol,
                                   DayCounter dc) {
        this(discountCurve, vol, dc, CashAnnuityModel.DiscountCurve);
    }

    public BachelierSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                   Handle<Quote> vol) {
        this(discountCurve, vol, new Actual365Fixed(), CashAnnuityModel.DiscountCurve);
    }

    public BachelierSwaptionEngine(Handle<YieldTermStructure> discountCurve,
                                   Handle<SwaptionVolatilityStructure> vol,
                                   CashAnnuityModel model) {
        super(discountCurve, vol, model);
    }

    /**
     * Java cannot overload on {@code Handle<Quote>} vs
     * {@code Handle<SwaptionVolatilityStructure>} (same erasure). The
     * official two-argument structure constructor is provided as a factory.
     */
    public static BachelierSwaptionEngine withVolatility(
            Handle<YieldTermStructure> discountCurve,
            Handle<SwaptionVolatilityStructure> vol) {
        return new BachelierSwaptionEngine(discountCurve, vol, CashAnnuityModel.DiscountCurve);
    }

    @Override
    protected Sarb.VolatilityType volatilityType() {
        return Sarb.VolatilityType.Normal;
    }

    @Override
    protected double value(Option.Type type,
                           double strike,
                           double atmForward,
                           double stdDev,
                           double annuity,
                           double displacement) {
        return BlackFormula.bachelierBlackFormula(type, strike, atmForward,
                stdDev, annuity);
    }

    @Override
    protected double vega(double strike,
                          double atmForward,
                          double stdDev,
                          double exerciseTime,
                          double annuity,
                          double displacement) {
        return Math.sqrt(exerciseTime)
                * BlackFormula.bachelierBlackFormulaStdDevDerivative(strike,
                atmForward, stdDev, annuity);
    }

    @Override
    protected double delta(Option.Type type,
                           double strike,
                           double atmForward,
                           double stdDev,
                           double annuity,
                           double displacement) {
        return BlackFormula.bachelierBlackFormulaForwardDerivative(type, strike,
                atmForward, stdDev, annuity);
    }
}
