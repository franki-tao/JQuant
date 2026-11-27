package jquant.models.shortrate.calibrationhelpers;

import jquant.CashFlow;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.patterns.Observer;
import jquant.Quote;
import jquant.cashflows.FixedRateLeg;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.instruments.CapFloor;
import jquant.instruments.Swap;
import jquant.models.BlackCalibrationHelper;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.pricingengines.capfloor.BachelierCapFloorEngine;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Calibration helper for an at-the-money cap.
 *
 * <p>Official QuantLib
 * {@code ql/models/shortrate/calibrationhelpers/caphelper.{hpp,cpp}} port.
 * Java adaptation: official prices the temporary swap with
 * {@code DiscountingSwapEngine}, while the Java {@link Swap} writes
 * {@code DiscountingMultiLegSwapEngine} arguments, so that engine is used
 * here; the financial quantities are identical.
 */
public class CapHelper extends BlackCalibrationHelper {

    private CapFloor.Cap cap_;
    private final Period length_;
    private final IborIndex index_;
    private final Handle<YieldTermStructure> termStructure_;
    private final Frequency fixedLegFrequency_;
    private final DayCounter fixedLegDayCounter_;
    private final boolean includeFirstSwaplet_;

    public CapHelper(final Period length,
                     Handle<Quote> volatility,
                     IborIndex index,
                     Frequency fixedLegFrequency,
                     DayCounter fixedLegDayCounter,
                     boolean includeFirstSwaplet,
                     Handle<YieldTermStructure> termStructure) {
        this(length, volatility, index, fixedLegFrequency, fixedLegDayCounter,
                includeFirstSwaplet, termStructure,
                CalibrationErrorType.RelativePriceError, ShiftedLognormal, 0.0);
    }

    public CapHelper(final Period length,
                     Handle<Quote> volatility,
                     IborIndex index,
                     Frequency fixedLegFrequency,
                     DayCounter fixedLegDayCounter,
                     boolean includeFirstSwaplet,
                     Handle<YieldTermStructure> termStructure,
                     CalibrationErrorType errorType) {
        this(length, volatility, index, fixedLegFrequency, fixedLegDayCounter,
                includeFirstSwaplet, termStructure, errorType,
                ShiftedLognormal, 0.0);
    }

    public CapHelper(final Period length,
                     Handle<Quote> volatility,
                     IborIndex index,
                     Frequency fixedLegFrequency,
                     DayCounter fixedLegDayCounter,
                     boolean includeFirstSwaplet,
                     Handle<YieldTermStructure> termStructure,
                     CalibrationErrorType errorType,
                     VolatilityType type,
                     double shift) {
        super(volatility, errorType, type, shift);
        length_ = length;
        index_ = index;
        termStructure_ = termStructure;
        fixedLegFrequency_ = fixedLegFrequency;
        fixedLegDayCounter_ = fixedLegDayCounter;
        includeFirstSwaplet_ = includeFirstSwaplet;
        registerWith(index_);
        registerWith(termStructure_.get());
    }

    @Override
    public double modelValue() {
        calculate();
        cap_.setPricingEngine(engine_);
        return cap_.NPV();
    }

    @Override
    public double blackPrice(double sigma) {
        calculate();
        Handle<Quote> vol = new Handle<>(new SimpleQuote(sigma), true);
        PricingEngine engine;
        switch (volatilityType_) {
            case ShiftedLognormal:
                engine = new BlackCapFloorEngine(termStructure_, vol,
                        new jquant.time.daycounters.Actual365Fixed(), shift_);
                break;
            case Normal:
                engine = new BachelierCapFloorEngine(termStructure_, vol,
                        new jquant.time.daycounters.Actual365Fixed());
                break;
            default:
                QL_FAIL("unknown volatility type: " + volatilityType_);
                throw new IllegalStateException("unreachable");
        }
        cap_.setPricingEngine(engine);
        double value = cap_.NPV();
        cap_.setPricingEngine(engine_);
        // Official blackPrice destroys the temporary engine and its constant
        // vol at the end of the function (Observer::~Observer unregisters).
        releaseTemporaryBlackEngine(engine);
        return value;
    }

    private static void releaseTemporaryBlackEngine(PricingEngine engine) {
        if (engine instanceof Observer observer)
            observer.unregisterWithAll();
        if (engine instanceof BlackCapFloorEngine e)
            e.volatility().currentLink().unregisterWithAll();
        else if (engine instanceof BachelierCapFloorEngine e)
            e.volatility().currentLink().unregisterWithAll();
    }

    @Override
    protected void performCalculations() {
        Period indexTenor = index_.tenor();
        double fixedRate = 0.04; // dummy value
        Date startDate, maturity;
        if (includeFirstSwaplet_) {
            startDate = termStructure_.currentLink().referenceDate();
            maturity = termStructure_.currentLink().referenceDate().add(length_);
        } else {
            startDate = termStructure_.currentLink().referenceDate().add(indexTenor);
            maturity = termStructure_.currentLink().referenceDate().add(length_);
        }

        List<Double> nominals = List.of(1.0);

        Schedule floatSchedule = new Schedule(startDate, maturity,
                index_.tenor(), index_.fixingCalendar(),
                index_.businessDayConvention(),
                index_.businessDayConvention(),
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        List<CashFlow> floatingLeg = new IborLeg(floatSchedule, index_)
                .withNotionals(nominals)
                .withPaymentAdjustment(index_.businessDayConvention())
                .withFixingDays(0)
                .leg();

        Schedule fixedSchedule = new Schedule(startDate, maturity,
                new Period(fixedLegFrequency_),
                index_.fixingCalendar(),
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        List<CashFlow> fixedLeg = new FixedRateLeg(fixedSchedule)
                .withNotionals(nominals)
                .withCouponRates(fixedRate, fixedLegDayCounter_)
                .withPaymentAdjustment(index_.businessDayConvention())
                .leg();

        Swap swap = new Swap(floatingLeg, fixedLeg);
        swap.setPricingEngine(new DiscountingMultiLegSwapEngine(termStructure_,
                Boolean.FALSE, null, null));
        double fairRate = fixedRate - swap.NPV() / (swap.legBPS(1) / 1.0e-4);
        cap_ = new CapFloor.Cap(floatingLeg, List.of(fairRate));

        super.performCalculations();
    }
}
