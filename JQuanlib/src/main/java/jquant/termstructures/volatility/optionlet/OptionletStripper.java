package jquant.termstructures.volatility.optionlet;

import jquant.CashFlow;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.Settings;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.indexes.OvernightIndex;
import jquant.pricingengines.capfloor.BachelierCapFloorEngine;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.capfloor.CapFloorTermVolSurface;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.Normal;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * StrippedOptionletBase specialization. It is up to derived classes to
 * implement {@code performCalculations()}.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/optionlet/optionletstripper.{hpp,cpp}}.
 */
public abstract class OptionletStripper extends StrippedOptionletBase {

    protected final CapFloorTermVolSurface termVolSurface_;
    protected final IborIndex iborIndex_;
    protected final Handle<YieldTermStructure> discount_;
    protected final int nStrikes_;
    protected final int nOptionletTenors_;

    protected final List<List<Double>> optionletStrikes_;
    protected final List<List<Double>> optionletVolatilities_;

    protected final double[] optionletTimes_;
    protected final List<Date> optionletDates_;
    protected final List<Period> optionletTenors_;
    protected final double[] atmOptionletRate_;
    protected final List<Date> optionletPaymentDates_;
    protected final double[] optionletAccrualPeriods_;

    protected final List<Period> capFloorLengths_;
    private final Sarb.VolatilityType volatilityType_;
    private final double displacement_;
    private final int paymentLag_;
    private final Period optionletFrequency_;
    private final boolean isOvernightIndex_;

    protected OptionletStripper(CapFloorTermVolSurface termVolSurface,
                                IborIndex iborIndex,
                                Handle<YieldTermStructure> discount,
                                Sarb.VolatilityType type,
                                double displacement,
                                int paymentLag) {
        this(termVolSurface, iborIndex, discount, type, displacement, null,
                paymentLag);
    }

    protected OptionletStripper(CapFloorTermVolSurface termVolSurface,
                                IborIndex iborIndex,
                                Handle<YieldTermStructure> discount,
                                Sarb.VolatilityType type,
                                double displacement,
                                Period optionletFrequency,
                                int paymentLag) {
        termVolSurface_ = termVolSurface;
        iborIndex_ = iborIndex;
        discount_ = discount;
        nStrikes_ = termVolSurface.strikes().length;
        volatilityType_ = type;
        displacement_ = displacement;
        paymentLag_ = paymentLag;
        optionletFrequency_ = optionletFrequency;
        isOvernightIndex_ = iborIndex instanceof OvernightIndex;

        if (volatilityType_ == Normal)
            QL_REQUIRE(displacement_ == 0.0,
                    "non-null displacement is not allowed with Normal model");
        if (isOvernightIndex_)
            QL_FAIL("overnight-index optionlet stripping is not ported");

        registerWith(termVolSurface_);
        registerWith(iborIndex_);
        if (discount_ != null)
            registerWith(discount_.get());
        registerWith(Settings.instance.evaluationDate());

        Period indexTenor = optionletFrequency_ != null
                ? optionletFrequency_ : iborIndex_.tenor();
        Period maxCapFloorTenor = termVolSurface_.optionTenors()
                .get(termVolSurface_.optionTenors().size() - 1);

        // optionlet tenors and capFloor lengths
        optionletTenors_ = new ArrayList<>();
        capFloorLengths_ = new ArrayList<>();
        optionletTenors_.add(indexTenor);
        capFloorLengths_.add(TimeUtils.add(optionletTenors_.get(0), indexTenor));
        QL_REQUIRE(TimeUtils.geq(maxCapFloorTenor,
                        capFloorLengths_.get(capFloorLengths_.size() - 1)),
                "too short (" + maxCapFloorTenor + ") capfloor term vol termVolSurface");
        Period nextCapFloorLength = TimeUtils.add(
                capFloorLengths_.get(capFloorLengths_.size() - 1), indexTenor);
        while (TimeUtils.leq(nextCapFloorLength, maxCapFloorTenor)) {
            optionletTenors_.add(capFloorLengths_.get(capFloorLengths_.size() - 1));
            capFloorLengths_.add(nextCapFloorLength);
            nextCapFloorLength = TimeUtils.add(nextCapFloorLength, indexTenor);
        }
        nOptionletTenors_ = optionletTenors_.size();

        optionletVolatilities_ = new ArrayList<>(nOptionletTenors_);
        optionletStrikes_ = new ArrayList<>(nOptionletTenors_);
        double[] strikes = termVolSurface_.strikes();
        for (int i = 0; i < nOptionletTenors_; ++i) {
            List<Double> rowVols = new ArrayList<>(Collections.nCopies(nStrikes_, 0.0));
            optionletVolatilities_.add(rowVols);
            List<Double> rowStrikes = new ArrayList<>(nStrikes_);
            for (double strike : strikes)
                rowStrikes.add(strike);
            optionletStrikes_.add(rowStrikes);
        }
        optionletDates_ = new ArrayList<>(Collections.nCopies(nOptionletTenors_, new Date()));
        optionletTimes_ = new double[nOptionletTenors_];
        atmOptionletRate_ = new double[nOptionletTenors_];
        optionletPaymentDates_ = new ArrayList<>(
                Collections.nCopies(nOptionletTenors_, new Date()));
        optionletAccrualPeriods_ = new double[nOptionletTenors_];
    }

    @Override
    public List<Double> optionletStrikes(int i) {
        calculate();
        QL_REQUIRE(i < optionletStrikes_.size(),
                "index (" + i + ") must be less than optionletStrikes size ("
                        + optionletStrikes_.size() + ")");
        return optionletStrikes_.get(i);
    }

    @Override
    public List<Double> optionletVolatilities(int i) {
        calculate();
        QL_REQUIRE(i < optionletVolatilities_.size(),
                "index (" + i + ") must be less than optionletVolatilities size ("
                        + optionletVolatilities_.size() + ")");
        return optionletVolatilities_.get(i);
    }

    public List<Period> optionletFixingTenors() {
        return Collections.unmodifiableList(optionletTenors_);
    }

    @Override
    public List<Date> optionletFixingDates() {
        calculate();
        return optionletDates_;
    }

    @Override
    public double[] optionletFixingTimes() {
        calculate();
        return optionletTimes_;
    }

    @Override
    public int optionletMaturities() {
        return optionletTenors_.size();
    }

    public List<Date> optionletPaymentDates() {
        calculate();
        return optionletPaymentDates_;
    }

    public double[] optionletAccrualPeriods() {
        calculate();
        return optionletAccrualPeriods_;
    }

    @Override
    public double[] atmOptionletRates() {
        calculate();
        return atmOptionletRate_;
    }

    @Override
    public DayCounter dayCounter() {
        return termVolSurface_.dayCounter();
    }

    @Override
    public Calendar calendar() {
        return termVolSurface_.calendar();
    }

    @Override
    public int settlementDays() {
        return termVolSurface_.settlementDays();
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return termVolSurface_.businessDayConvention();
    }

    public CapFloorTermVolSurface termVolSurface() {
        return termVolSurface_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    @Override
    public double displacement() {
        return displacement_;
    }

    @Override
    public Sarb.VolatilityType volatilityType() {
        return volatilityType_;
    }

    /** Optional official {@code optionletFrequency}; {@code null} when unset. */
    public Period optionletFrequency() {
        return optionletFrequency_;
    }

    public int paymentLag() {
        return paymentLag_;
    }

    protected boolean isOvernightIndex() {
        return isOvernightIndex_;
    }

    protected List<CashFlow> makeCapFloorLeg(Period capFloorLength) {
        Date startDate = iborIndex_.valueDate(
                iborIndex_.fixingCalendar().adjust(
                        Settings.instance.evaluationDate().Date(),
                        BusinessDayConvention.FOLLOWING));
        Date endDate = startDate.add(capFloorLength);
        Schedule schedule = new Schedule(startDate, endDate,
                iborIndex_.tenor(), iborIndex_.fixingCalendar(),
                iborIndex_.businessDayConvention(),
                iborIndex_.businessDayConvention(),
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        List<CashFlow> leg = new IborLeg(schedule, iborIndex_)
                .withNotionals(1.0)
                .withPaymentDayCounter(iborIndex_.dayCounter())
                .withPaymentAdjustment(iborIndex_.businessDayConvention())
                .withPaymentLag(paymentLag_)
                .leg();
        // Spot-starting Ibor caps exclude the first, already-fixed caplet.
        leg.remove(0);
        QL_REQUIRE(!leg.isEmpty(), "cap/floor length " + capFloorLength
                + " does not contain any optionlets");
        return leg;
    }

    protected Handle<OptionletVolatilityStructure> constantOptionletVolatility(
            Handle<Quote> volatility) {
        return new Handle<>(new ConstantOptionletVolatility(
                termVolSurface_.referenceDate(), termVolSurface_.calendar(),
                termVolSurface_.businessDayConvention(), volatility,
                termVolSurface_.dayCounter(), volatilityType_, displacement_), true);
    }

    protected PricingEngine makeCapFloorPricingEngine(
            Handle<YieldTermStructure> discountCurve,
            Handle<Quote> volatility) {
        // Preserve the quote-based engines' zero-settlement reference date for Ibor.
        switch (volatilityType_) {
            case ShiftedLognormal:
                return new BlackCapFloorEngine(discountCurve, volatility,
                        termVolSurface_.dayCounter(), displacement_);
            case Normal:
                return new BachelierCapFloorEngine(discountCurve, volatility,
                        termVolSurface_.dayCounter());
            default:
                QL_FAIL("unknown volatility type: " + volatilityType_);
        }
        return null;
    }

    /**
     * Java counterpart of the official overload taking a
     * {@code Handle<OptionletVolatilityStructure>}. The name differs because
     * both overloads erase to the same signature.
     */
    protected PricingEngine makeCapFloorPricingEngineWithOptionletVolatility(
            Handle<YieldTermStructure> discountCurve,
            Handle<OptionletVolatilityStructure> volatility) {
        switch (volatilityType_) {
            case ShiftedLognormal:
                return BlackCapFloorEngine.withVolatility(discountCurve, volatility,
                        displacement_);
            case Normal:
                return BachelierCapFloorEngine.withVolatility(discountCurve,
                        volatility);
            default:
                QL_FAIL("unknown volatility type: " + volatilityType_);
        }
        return null;
    }
}
