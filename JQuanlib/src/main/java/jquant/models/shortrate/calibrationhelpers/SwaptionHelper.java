package jquant.models.shortrate.calibrationhelpers;

import jquant.EuropeanExercise;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.patterns.Observer;
import jquant.Quote;
import jquant.cashflows.RateAveraging;
import jquant.indexes.IborIndex;
import jquant.indexes.OvernightIndex;
import jquant.instruments.FixedVsFloatingSwap;
import jquant.instruments.OvernightIndexedSwap;
import jquant.instruments.Settlement;
import jquant.instruments.Swap;
import jquant.instruments.Swaption;
import jquant.instruments.VanillaSwap;
import jquant.models.BlackCalibrationHelper;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.pricingengines.swaption.BachelierSwaptionEngine;
import jquant.pricingengines.swaption.BlackSwaptionEngine;
import jquant.pricingengines.swaption.BlackStyleSwaptionEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Calibration helper for an at-the-money swaption.
 *
 * <p>Official QuantLib
 * {@code ql/models/shortrate/calibrationhelpers/swaptionhelper.{hpp,cpp}}
 * port.
 */
public class SwaptionHelper extends BlackCalibrationHelper {

    private Date exerciseDate_;
    private Date endDate_;
    private final Period maturity_;
    private final Period length_;
    private final Period fixedLegTenor_;
    private final IborIndex index_;
    private final Handle<YieldTermStructure> termStructure_;
    private final DayCounter fixedLegDayCounter_;
    private final DayCounter floatingLegDayCounter_;
    private final double strike_;
    private final double nominal_;
    private final Integer settlementDays_;
    private final RateAveraging averagingMethod_;
    private double exerciseRate_;
    private FixedVsFloatingSwap swap_;
    private Swaption swaption_;

    public SwaptionHelper(final Period maturity,
                          final Period length,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure) {
        this(maturity, length, volatility, index, fixedLegTenor,
                fixedLegDayCounter, floatingLegDayCounter, termStructure,
                CalibrationErrorType.RelativePriceError, Double.NaN, 1.0,
                ShiftedLognormal, 0.0, null, RateAveraging.Compound);
    }

    public SwaptionHelper(final Period maturity,
                          final Period length,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure,
                          CalibrationErrorType errorType) {
        this(maturity, length, volatility, index, fixedLegTenor,
                fixedLegDayCounter, floatingLegDayCounter, termStructure,
                errorType, Double.NaN, 1.0, ShiftedLognormal, 0.0, null,
                RateAveraging.Compound);
    }

    public SwaptionHelper(final Period maturity,
                          final Period length,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure,
                          CalibrationErrorType errorType,
                          double strike,
                          double nominal,
                          VolatilityType type,
                          double shift,
                          Integer settlementDays,
                          RateAveraging averagingMethod) {
        super(volatility, errorType, type, shift);
        maturity_ = maturity;
        length_ = length;
        fixedLegTenor_ = fixedLegTenor;
        index_ = index;
        termStructure_ = termStructure;
        fixedLegDayCounter_ = fixedLegDayCounter;
        floatingLegDayCounter_ = floatingLegDayCounter;
        strike_ = strike;
        nominal_ = nominal;
        settlementDays_ = settlementDays;
        averagingMethod_ = averagingMethod;
        registerWith(index_);
        registerWith(termStructure_.get());
    }

    public SwaptionHelper(final Date exerciseDate,
                          final Period length,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure) {
        this(exerciseDate, length, volatility, index, fixedLegTenor,
                fixedLegDayCounter, floatingLegDayCounter, termStructure,
                CalibrationErrorType.RelativePriceError, Double.NaN, 1.0,
                ShiftedLognormal, 0.0, null, RateAveraging.Compound);
    }

    public SwaptionHelper(final Date exerciseDate,
                          final Period length,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure,
                          CalibrationErrorType errorType) {
        this(exerciseDate, length, volatility, index, fixedLegTenor,
                fixedLegDayCounter, floatingLegDayCounter, termStructure,
                errorType, Double.NaN, 1.0, ShiftedLognormal, 0.0, null,
                RateAveraging.Compound);
    }

    public SwaptionHelper(final Date exerciseDate,
                          final Period length,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure,
                          CalibrationErrorType errorType,
                          double strike,
                          double nominal,
                          VolatilityType type,
                          double shift,
                          Integer settlementDays,
                          RateAveraging averagingMethod) {
        super(volatility, errorType, type, shift);
        exerciseDate_ = exerciseDate.copy();
        maturity_ = new Period(0, TimeUnit.DAYS);
        length_ = length;
        fixedLegTenor_ = fixedLegTenor;
        index_ = index;
        termStructure_ = termStructure;
        fixedLegDayCounter_ = fixedLegDayCounter;
        floatingLegDayCounter_ = floatingLegDayCounter;
        strike_ = strike;
        nominal_ = nominal;
        settlementDays_ = settlementDays;
        averagingMethod_ = averagingMethod;
        registerWith(index_);
        registerWith(termStructure_.get());
    }

    public SwaptionHelper(final Date exerciseDate,
                          final Date endDate,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure) {
        this(exerciseDate, endDate, volatility, index, fixedLegTenor,
                fixedLegDayCounter, floatingLegDayCounter, termStructure,
                CalibrationErrorType.RelativePriceError, Double.NaN, 1.0,
                ShiftedLognormal, 0.0, null, RateAveraging.Compound);
    }

    public SwaptionHelper(final Date exerciseDate,
                          final Date endDate,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure,
                          CalibrationErrorType errorType) {
        this(exerciseDate, endDate, volatility, index, fixedLegTenor,
                fixedLegDayCounter, floatingLegDayCounter, termStructure,
                errorType, Double.NaN, 1.0, ShiftedLognormal, 0.0, null,
                RateAveraging.Compound);
    }

    public SwaptionHelper(final Date exerciseDate,
                          final Date endDate,
                          Handle<Quote> volatility,
                          IborIndex index,
                          final Period fixedLegTenor,
                          DayCounter fixedLegDayCounter,
                          DayCounter floatingLegDayCounter,
                          Handle<YieldTermStructure> termStructure,
                          CalibrationErrorType errorType,
                          double strike,
                          double nominal,
                          VolatilityType type,
                          double shift,
                          Integer settlementDays,
                          RateAveraging averagingMethod) {
        super(volatility, errorType, type, shift);
        exerciseDate_ = exerciseDate.copy();
        endDate_ = endDate.copy();
        maturity_ = new Period(0, TimeUnit.DAYS);
        length_ = new Period(0, TimeUnit.DAYS);
        fixedLegTenor_ = fixedLegTenor;
        index_ = index;
        termStructure_ = termStructure;
        fixedLegDayCounter_ = fixedLegDayCounter;
        floatingLegDayCounter_ = floatingLegDayCounter;
        strike_ = strike;
        nominal_ = nominal;
        settlementDays_ = settlementDays;
        averagingMethod_ = averagingMethod;
        registerWith(index_);
        registerWith(termStructure_.get());
    }

    public FixedVsFloatingSwap underlying() {
        calculate();
        return swap_;
    }

    public Swaption swaption() {
        calculate();
        return swaption_;
    }

    @Override
    public double modelValue() {
        calculate();
        swaption_.setPricingEngine(engine_);
        return swaption_.NPV();
    }

    @Override
    public double blackPrice(double sigma) {
        calculate();
        Handle<Quote> vol = new Handle<>(new SimpleQuote(sigma), true);
        PricingEngine engine;
        switch (volatilityType_) {
            case ShiftedLognormal:
                engine = new BlackSwaptionEngine(termStructure_, vol,
                        new jquant.time.daycounters.Actual365Fixed(), shift_);
                break;
            case Normal:
                engine = new BachelierSwaptionEngine(termStructure_, vol,
                        new jquant.time.daycounters.Actual365Fixed());
                break;
            default:
                QL_FAIL("can not construct engine: " + volatilityType_);
                throw new IllegalStateException("unreachable");
        }
        swaption_.setPricingEngine(engine);
        double value = swaption_.NPV();
        swaption_.setPricingEngine(engine_);
        // Official blackPrice destroys the temporary engine and its constant
        // vol at the end of the function (Observer::~Observer unregisters).
        releaseTemporaryBlackEngine(engine);
        return value;
    }

    private static void releaseTemporaryBlackEngine(PricingEngine engine) {
        if (engine instanceof Observer observer)
            observer.unregisterWithAll();
        if (engine instanceof BlackStyleSwaptionEngine e)
            e.volatility().currentLink().unregisterWithAll();
    }

    @Override
    protected void performCalculations() {
        Calendar calendar = index_.fixingCalendar();
        Date exerciseDate = exerciseDate_;
        if (exerciseDate == null
                || TimeUtils.equals(exerciseDate, new Date()))
            exerciseDate = calendar.advance(
                    termStructure_.currentLink().referenceDate(),
                    maturity_, index_.businessDayConvention());

        Date startDate;
        if (settlementDays_ == null) {
            startDate = index_.valueDate(
                    index_.fixingCalendar().adjust(exerciseDate));
        } else {
            startDate = calendar.advance(exerciseDate,
                    index_.fixingDays(), TimeUnit.DAYS,
                    index_.businessDayConvention());
        }

        Date endDate = endDate_;
        if (endDate == null || TimeUtils.equals(endDate, new Date()))
            endDate = calendar.advance(startDate, length_,
                    index_.businessDayConvention());
        Schedule fixedSchedule = new Schedule(startDate, endDate,
                fixedLegTenor_, calendar,
                index_.businessDayConvention(),
                index_.businessDayConvention(),
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        Schedule floatSchedule = new Schedule(startDate, endDate,
                index_.tenor(), calendar,
                index_.businessDayConvention(),
                index_.businessDayConvention(),
                DateGenerationRule.FORWARD, false, new Date(), new Date());

        PricingEngine swapEngine = new DiscountingSwapEngine(termStructure_);

        Swap.Type type = Swap.Type.Receiver;
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);
        FixedVsFloatingSwap temp = makeSwap(fixedSchedule, floatSchedule,
                0.0, type);
        temp.setPricingEngine(swapEngine);
        double forward = temp.fairRate();
        if (Double.isNaN(strike_)) {
            exerciseRate_ = forward;
        } else {
            exerciseRate_ = strike_;
            type = strike_ <= forward ? Swap.Type.Receiver : Swap.Type.Payer;
        }
        swap_ = makeSwap(fixedSchedule, floatSchedule, exerciseRate_, type);
        swap_.setPricingEngine(swapEngine);
        swaption_ = new Swaption(swap_, exercise,
                Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
        super.performCalculations();
    }

    private FixedVsFloatingSwap makeSwap(Schedule fixedSchedule,
                                         Schedule floatSchedule,
                                         double exerciseRate,
                                         Swap.Type type) {
        if (index_ instanceof OvernightIndex onIndex) {
            return new OvernightIndexedSwap(type, nominal_,
                    fixedSchedule, exerciseRate, fixedLegDayCounter_,
                    floatSchedule, onIndex, 0.0, 0,
                    BusinessDayConvention.FOLLOWING, null, true,
                    averagingMethod_);
        } else {
            return new VanillaSwap(
                    type == Swap.Type.Payer
                            ? VanillaSwap.Type.Payer : VanillaSwap.Type.Receiver,
                    nominal_, fixedSchedule, exerciseRate,
                    fixedLegDayCounter_, floatSchedule, index_, 0.0,
                    floatingLegDayCounter_);
        }
    }
}
