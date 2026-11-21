package jquant.instruments;

import jquant.CashFlow;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.cashflows.CashFlows;
import jquant.indexes.IborIndex;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Cap/floor builder.
 *
 * <p>Ported from QuantLib {@code ql/instruments/makecapfloor.{hpp,cpp}}.
 */
public class MakeCapFloor {
    private final CapFloor.Type capFloorType_;
    private double strike_ = Double.NaN;
    private boolean firstCapletExcluded_ = true;
    private boolean asOptionlet_ = false;

    private final MakeVanillaSwap makeVanillaSwap_;

    private PricingEngine engine_ = null;

    public MakeCapFloor(CapFloor.Type capFloorType,
                        Period tenor,
                        IborIndex iborIndex) {
        capFloorType_ = capFloorType;
        // setting the fixed leg tenor avoids that MakeVanillaSwap throws
        // because of an unknown fixed leg default tenor for a currency;
        // notice that only the floating leg of the swap is used anyway
        makeVanillaSwap_ = new MakeVanillaSwap(tenor, iborIndex)
                .withFixedRate(0.0)
                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                .withFixedLegDayCount(new Actual365Fixed());
    }

    /** Builds the cap/floor, as the official conversion operator. */
    public CapFloor makeCapFloor() {
        VanillaSwap swap = makeVanillaSwap_.makeSwap();

        List<CashFlow> leg = new ArrayList<>(swap.floatingLeg());
        if (firstCapletExcluded_)
            leg.remove(0);

        // only leaves the last coupon
        if (asOptionlet_ && leg.size() > 1)
            leg = new ArrayList<>(leg.subList(leg.size() - 1, leg.size()));

        double strike = strike_;
        if (Double.isNaN(strike_)) {
            // temporary patch...
            // should be fixed for every CapFloor::Engine
            Handle<YieldTermStructure> discountCurve = null;
            if (engine_ instanceof BlackCapFloorEngine blackCapTemp) {
                discountCurve = blackCapTemp.termStructure();
            } else {
                QL_FAIL("cannot calculate ATM without a BlackCapFloorEngine or "
                        + "BachelierCapFloorEngine");
            }
            strike = CashFlows.atmRate(leg,
                    discountCurve.currentLink(),
                    false,
                    discountCurve.currentLink().referenceDate());
        }

        CapFloor capFloor = new CapFloor(capFloorType_, leg,
                Collections.singletonList(strike));
        capFloor.setPricingEngine(engine_);
        return capFloor;
    }

    public MakeCapFloor withNominal(double n) {
        makeVanillaSwap_.withNominal(n);
        return this;
    }

    public MakeCapFloor withStrike(double k) {
        strike_ = k;
        return this;
    }

    public MakeCapFloor withForwardStart(Period f) {
        makeVanillaSwap_.withForwardStart(f);
        firstCapletExcluded_ = TimeUtils.equals(f, new Period(0, TimeUnit.DAYS));
        return this;
    }

    public MakeCapFloor withEffectiveDate(Date effectiveDate,
                                          boolean firstCapletExcluded) {
        makeVanillaSwap_.withEffectiveDate(effectiveDate);
        firstCapletExcluded_ = firstCapletExcluded;
        return this;
    }

    public MakeCapFloor withTenor(Period t) {
        makeVanillaSwap_.withFloatingLegTenor(t);
        return this;
    }

    public MakeCapFloor withCalendar(Calendar cal) {
        makeVanillaSwap_.withFloatingLegCalendar(cal);
        return this;
    }

    public MakeCapFloor withConvention(BusinessDayConvention bdc) {
        makeVanillaSwap_.withFloatingLegConvention(bdc);
        return this;
    }

    public MakeCapFloor withTerminationDateConvention(BusinessDayConvention bdc) {
        makeVanillaSwap_.withFloatingLegTerminationDateConvention(bdc);
        return this;
    }

    public MakeCapFloor withRule(DateGenerationRule r) {
        makeVanillaSwap_.withFloatingLegRule(r);
        return this;
    }

    public MakeCapFloor withEndOfMonth(boolean flag) {
        makeVanillaSwap_.withFloatingLegEndOfMonth(flag);
        return this;
    }

    public MakeCapFloor withFirstDate(Date d) {
        makeVanillaSwap_.withFloatingLegFirstDate(d);
        return this;
    }

    public MakeCapFloor withNextToLastDate(Date d) {
        makeVanillaSwap_.withFloatingLegNextToLastDate(d);
        return this;
    }

    public MakeCapFloor withDayCount(DayCounter dc) {
        makeVanillaSwap_.withFloatingLegDayCount(dc);
        return this;
    }

    /** Only get the last coupon. */
    public MakeCapFloor asOptionlet(boolean b) {
        asOptionlet_ = b;
        return this;
    }

    public MakeCapFloor withPricingEngine(PricingEngine engine) {
        engine_ = engine;
        return this;
    }
}
