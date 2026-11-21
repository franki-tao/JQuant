package jquant.instruments;

import jquant.EuropeanExercise;
import jquant.Exercise;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.indexes.SwapIndex;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Period;
import jquant.time.TimeUtils;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Builder for {@link Swaption}, following QuantLib
 * {@code ql/instruments/makeswaption.{hpp,cpp}}.
 *
 * <p>Only the vanilla-swap branch is ported: the overnight-indexed-swap-index
 * branch needs {@code OvernightIndexedSwapIndex}, which the swaption suite
 * does not use.
 */
public class MakeSwaption {
    private final SwapIndex swapIndex_;
    private Settlement.Type delivery_ = Settlement.Type.Physical;
    private Settlement.Method settlementMethod_ = Settlement.Method.PhysicalOTC;
    private Period optionTenor_ = new Period();
    private BusinessDayConvention optionConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private Date fixingDate_ = new Date();
    private Date exerciseDate_ = new Date();
    private Calendar exerciseCalendar_ = null;
    private double strike_ = Double.NaN;
    private Swap.Type underlyingType_ = Swap.Type.Payer;
    private double nominal_ = 1.0;
    private Optional<Boolean> useIndexedCoupons_ = Optional.empty();
    private PricingEngine engine_ = null;

    public MakeSwaption(SwapIndex swapIndex, Period optionTenor, double strike) {
        swapIndex_ = swapIndex;
        optionTenor_ = optionTenor;
        strike_ = strike;
    }

    public MakeSwaption(SwapIndex swapIndex, Date fixingDate, double strike) {
        swapIndex_ = swapIndex;
        fixingDate_ = fixingDate;
        strike_ = strike;
    }

    public Swaption makeSwaption() {
        Calendar calendar = (exerciseCalendar_ == null || exerciseCalendar_.empty())
                ? swapIndex_.fixingCalendar()
                : exerciseCalendar_;
        Date refDate = Settings.instance.evaluationDate().Date();
        // if the evaluation date is not a business day
        // then move to the next business day
        refDate = calendar.adjust(refDate, BusinessDayConvention.FOLLOWING);
        if (TimeUtils.equals(fixingDate_, new Date())) {
            fixingDate_ = calendar.advance(refDate, optionTenor_, optionConvention_);
        }
        Exercise exercise;
        if (TimeUtils.equals(exerciseDate_, new Date())) {
            exercise = new EuropeanExercise(fixingDate_);
        } else {
            QL_REQUIRE(TimeUtils.leq(exerciseDate_, fixingDate_),
                    "exercise date (" + exerciseDate_ + ") must be less "
                            + "than or equal to fixing date (" + fixingDate_ + ")");
            exercise = new EuropeanExercise(exerciseDate_);
        }

        double usedStrike;
        if (Double.isNaN(strike_)) {
            // ATM on curve(s) attached to index
            QL_REQUIRE(!swapIndex_.forwardingTermStructure().empty(),
                    "null term structure set to this instance of " + swapIndex_.name());
            VanillaSwap temp = swapIndex_.underlyingSwap(fixingDate_);
            temp.setPricingEngine(new DiscountingSwapEngine(
                    swapIndex_.exogenousDiscount()
                            ? swapIndex_.discountingTermStructure()
                            : swapIndex_.forwardingTermStructure()));
            usedStrike = temp.fairRate();
        } else {
            usedStrike = strike_;
        }

        BusinessDayConvention bdc = swapIndex_.fixedLegConvention();
        FixedVsFloatingSwap underlyingSwap =
                new MakeVanillaSwap(swapIndex_.tenor(), swapIndex_.iborIndex())
                        .withFixedRate(usedStrike)
                        .withEffectiveDate(swapIndex_.valueDate(fixingDate_))
                        .withFixedLegCalendar(swapIndex_.fixingCalendar())
                        .withFixedLegDayCount(swapIndex_.dayCounter())
                        .withFixedLegTenor(swapIndex_.fixedLegTenor())
                        .withFixedLegConvention(bdc)
                        .withFixedLegTerminationDateConvention(bdc)
                        .withType(underlyingType_)
                        .withNominal(nominal_)
                        .withIndexedCoupons(useIndexedCoupons_)
                        .makeSwap();

        Swaption swaption = new Swaption(underlyingSwap, exercise,
                delivery_, settlementMethod_);
        if (engine_ != null)
            swaption.setPricingEngine(engine_);
        return swaption;
    }

    public MakeSwaption withSettlementType(Settlement.Type delivery) {
        delivery_ = delivery;
        return this;
    }

    public MakeSwaption withSettlementMethod(Settlement.Method settlementMethod) {
        settlementMethod_ = settlementMethod;
        return this;
    }

    public MakeSwaption withOptionConvention(BusinessDayConvention bdc) {
        optionConvention_ = bdc;
        return this;
    }

    public MakeSwaption withExerciseDate(Date date) {
        exerciseDate_ = date;
        return this;
    }

    public MakeSwaption withExerciseCalendar(Calendar cal) {
        exerciseCalendar_ = cal;
        return this;
    }

    public MakeSwaption withUnderlyingType(Swap.Type type) {
        underlyingType_ = type;
        return this;
    }

    public MakeSwaption withPricingEngine(PricingEngine engine) {
        engine_ = engine;
        return this;
    }

    public MakeSwaption withNominal(double n) {
        nominal_ = n;
        return this;
    }

    public MakeSwaption withIndexedCoupons(Optional<Boolean> b) {
        useIndexedCoupons_ = b;
        return this;
    }

    public MakeSwaption withAtParCoupons(boolean b) {
        useIndexedCoupons_ = Optional.of(!b);
        return this;
    }
}
