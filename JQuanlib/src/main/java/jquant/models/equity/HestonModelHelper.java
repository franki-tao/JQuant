package jquant.models.equity;

import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.models.BlackCalibrationHelper;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Period;

/**
 * Calibration helper for the Heston model.
 *
 * <p>Official QuantLib {@code ql/models/equity/hestonmodelhelper.{hpp,cpp}}
 * port.
 */
public class HestonModelHelper extends BlackCalibrationHelper {

    private final Period maturity_;
    private final Calendar calendar_;
    private final Handle<Quote> s0_;
    private final double strikePrice_;
    private final Handle<YieldTermStructure> riskFreeRate_;
    private final Handle<YieldTermStructure> dividendYield_;

    private Date exerciseDate_;
    private double tau_;
    private Option.Type type_;
    private EuropeanOption option_;

    public HestonModelHelper(final Period maturity,
                             Calendar calendar,
                             double s0,
                             double strikePrice,
                             Handle<Quote> volatility,
                             Handle<YieldTermStructure> riskFreeRate,
                             Handle<YieldTermStructure> dividendYield) {
        this(maturity, calendar, s0, strikePrice, volatility, riskFreeRate,
                dividendYield, CalibrationErrorType.RelativePriceError);
    }

    public HestonModelHelper(final Period maturity,
                             Calendar calendar,
                             double s0,
                             double strikePrice,
                             Handle<Quote> volatility,
                             Handle<YieldTermStructure> riskFreeRate,
                             Handle<YieldTermStructure> dividendYield,
                             CalibrationErrorType errorType) {
        super(volatility, errorType, VolatilityType.ShiftedLognormal, 0.0);
        maturity_ = maturity;
        calendar_ = calendar;
        s0_ = new Handle<>(new SimpleQuote(s0), true);
        strikePrice_ = strikePrice;
        riskFreeRate_ = riskFreeRate;
        dividendYield_ = dividendYield;
        registerWith(riskFreeRate_.get());
        registerWith(dividendYield_.get());
    }

    public HestonModelHelper(final Period maturity,
                             Calendar calendar,
                             Handle<Quote> s0,
                             double strikePrice,
                             Handle<Quote> volatility,
                             Handle<YieldTermStructure> riskFreeRate,
                             Handle<YieldTermStructure> dividendYield) {
        this(maturity, calendar, s0, strikePrice, volatility, riskFreeRate,
                dividendYield, CalibrationErrorType.RelativePriceError);
    }

    public HestonModelHelper(final Period maturity,
                             Calendar calendar,
                             Handle<Quote> s0,
                             double strikePrice,
                             Handle<Quote> volatility,
                             Handle<YieldTermStructure> riskFreeRate,
                             Handle<YieldTermStructure> dividendYield,
                             CalibrationErrorType errorType) {
        super(volatility, errorType, VolatilityType.ShiftedLognormal, 0.0);
        maturity_ = maturity;
        calendar_ = calendar;
        s0_ = s0;
        strikePrice_ = strikePrice;
        riskFreeRate_ = riskFreeRate;
        dividendYield_ = dividendYield;
        registerWith(s0_.get());
        registerWith(riskFreeRate_.get());
        registerWith(dividendYield_.get());
    }

    @Override
    protected void performCalculations() {
        exerciseDate_ = calendar_.advance(
                riskFreeRate_.currentLink().referenceDate(), maturity_);
        tau_ = riskFreeRate_.currentLink().timeFromReference(exerciseDate_);
        type_ = strikePrice_ * riskFreeRate_.currentLink().discount(tau_)
                >= s0_.currentLink().value()
                * dividendYield_.currentLink().discount(tau_)
                ? Option.Type.Call
                : Option.Type.Put;
        PlainVanillaPayoff payoff = new PlainVanillaPayoff(type_, strikePrice_);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate_);
        option_ = new EuropeanOption(payoff, exercise);
        super.performCalculations();
    }

    @Override
    public double modelValue() {
        calculate();
        option_.setPricingEngine(engine_);
        return option_.NPV();
    }

    @Override
    public double blackPrice(double volatility) {
        calculate();
        double stdDev = volatility * Math.sqrt(maturity());
        return BlackFormula.blackFormula(type_,
                strikePrice_ * riskFreeRate_.currentLink().discount(tau_),
                s0_.currentLink().value()
                        * dividendYield_.currentLink().discount(tau_),
                stdDev, 1.0, 0.0);
    }

    public double maturity() {
        calculate();
        return tau_;
    }
}
