package jquant.instruments;

import jquant.CashFlow;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.CmsCouponPricer;
import jquant.cashflows.CmsLeg;
import jquant.cashflows.CashflowUtil;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.indexes.SwapIndex;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
// Official DiscountingSwapEngine prices a generic Swap. In this port the
// same engine class is specialized for VanillaSwap; generic two-leg CMS
// swaps use DiscountingMultiLegSwapEngine (same mapping as asset-swap tests).
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual360;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Helper class to instantiate a standard market CMS swap.
 *
 * <p>Ported from QuantLib {@code ql/instruments/makecms.hpp/.cpp}. The
 * official conversion operators are replaced by {@link #makeSwap()}.
 */
public class MakeCms {

    private final Period swapTenor_;
    private final SwapIndex swapIndex_;
    private final IborIndex iborIndex_;
    private double iborSpread_ = 0.0;
    private boolean useAtmSpread_ = false;
    private Period forwardStart_ = new Period(0, TimeUnit.DAYS);

    private double cmsSpread_ = 0.0;
    private double cmsGearing_ = 1.0;
    private double cmsCap_ = Double.NaN;
    private double cmsFloor_ = Double.NaN;

    private Date effectiveDate_ = new Date();
    private Calendar cmsCalendar_;
    private Calendar floatCalendar_;

    private boolean payCms_ = true;
    private double nominal_ = 1.0;
    private Period cmsTenor_ = new Period(3, TimeUnit.MONTHS);
    private Period floatTenor_;
    private BusinessDayConvention cmsConvention_ =
            BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention cmsTerminationDateConvention_ =
            BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention floatConvention_;
    private BusinessDayConvention floatTerminationDateConvention_;
    private DateGenerationRule cmsRule_ = DateGenerationRule.BACKWARD;
    private DateGenerationRule floatRule_ = DateGenerationRule.BACKWARD;
    private boolean cmsEndOfMonth_ = false;
    private boolean floatEndOfMonth_ = false;
    private Date cmsFirstDate_ = new Date();
    private Date cmsNextToLastDate_ = new Date();
    private Date floatFirstDate_ = new Date();
    private Date floatNextToLastDate_ = new Date();
    private DayCounter cmsDayCount_ = new Actual360();
    private DayCounter floatDayCount_;

    private PricingEngine engine_;
    private CmsCouponPricer couponPricer_;

    public MakeCms(final Period swapTenor,
                   SwapIndex swapIndex,
                   IborIndex iborIndex) {
        swapTenor_ = swapTenor;
        swapIndex_ = swapIndex;
        iborIndex_ = iborIndex != null ? iborIndex : swapIndex.iborIndex();
        cmsCalendar_ = swapIndex_.fixingCalendar();
        floatCalendar_ = iborIndex_.fixingCalendar();
        floatTenor_ = iborIndex_.tenor();
        floatConvention_ = iborIndex_.businessDayConvention();
        floatTerminationDateConvention_ = iborIndex_.businessDayConvention();
        cmsDayCount_ = new Actual360();
        floatDayCount_ = iborIndex_.dayCounter();
        // arbitrary choice:
        // engine = new DiscountingMultiLegSwapEngine(iborIndex.termStructure());
        engine_ = new DiscountingMultiLegSwapEngine(
                swapIndex_.forwardingTermStructure());
    }

    public MakeCms(final Period swapTenor,
                   SwapIndex swapIndex) {
        this(swapTenor, swapIndex, null);
    }

    public MakeCms(final Period swapTenor,
                   SwapIndex swapIndex,
                   IborIndex iborIndex,
                   double iborSpread,
                   final Period forwardStart) {
        this(swapTenor, swapIndex, iborIndex);
        withIborSpread(iborSpread);
        withForwardStart(forwardStart);
    }

    public MakeCms(final Period swapTenor,
                   SwapIndex swapIndex,
                   double iborSpread,
                   final Period forwardStart) {
        this(swapTenor, swapIndex);
        withIborSpread(iborSpread);
        withForwardStart(forwardStart);
    }

    /**
     * Official {@code MakeCms::operator ext::shared_ptr<Swap>()}.
     */
    public Swap makeSwap() {
        Date startDate;
        if (!TimeUtils.equals(effectiveDate_, new Date())) {
            startDate = effectiveDate_;
        } else {
            int fixingDays = iborIndex_.fixingDays();
            Date refDate = Settings.instance.evaluationDate().Date();
            // if the evaluation date is not a business day
            // then move to the next business day
            refDate = floatCalendar_.adjust(refDate);
            Date spotDate = floatCalendar_.advance(refDate,
                    fixingDays, TimeUnit.DAYS);
            startDate = spotDate.add(forwardStart_);
        }

        Date terminationDate = startDate.add(swapTenor_);

        Schedule cmsSchedule = new Schedule(startDate, terminationDate,
                cmsTenor_, cmsCalendar_,
                cmsConvention_,
                cmsTerminationDateConvention_,
                cmsRule_, cmsEndOfMonth_,
                cmsFirstDate_, cmsNextToLastDate_);

        Schedule floatSchedule = new Schedule(startDate, terminationDate,
                floatTenor_, floatCalendar_,
                floatConvention_,
                floatTerminationDateConvention_,
                floatRule_, floatEndOfMonth_,
                floatFirstDate_, floatNextToLastDate_);

        List<CashFlow> cmsLeg = new CmsLeg(cmsSchedule, swapIndex_)
                .withNotionals(nominal_)
                .withPaymentDayCounter(cmsDayCount_)
                .withPaymentAdjustment(cmsConvention_)
                .withFixingDays(swapIndex_.fixingDays())
                .withGearings(cmsGearing_)
                .withSpreads(cmsSpread_)
                .withCaps(cmsCap_)
                .withFloors(cmsFloor_)
                .leg();
        if (couponPricer_ != null)
            CashflowUtil.setCouponPricer(cmsLeg, couponPricer_);

        double usedSpread = iborSpread_;
        if (useAtmSpread_) {
            QL_REQUIRE(!iborIndex_.forwardingTermStructure().empty(),
                    "null term structure set to this instance of "
                            + iborIndex_.name());
            QL_REQUIRE(!swapIndex_.forwardingTermStructure().empty(),
                    "null term structure set to this instance of "
                            + swapIndex_.name());
            QL_REQUIRE(couponPricer_ != null,
                    "no CmsCouponPricer set (yet)");
            List<CashFlow> floatLeg = new IborLeg(floatSchedule, iborIndex_)
                    .withNotionals(nominal_)
                    .withPaymentDayCounter(floatDayCount_)
                    .withPaymentAdjustment(floatConvention_)
                    .withFixingDays(iborIndex_.fixingDays())
                    .leg();

            Swap temp = new Swap(cmsLeg, floatLeg);
            temp.setPricingEngine(engine_);

            double npv = temp.legNPV(0) + temp.legNPV(1);

            usedSpread = -npv / temp.legBPS(1) * 1.0e-4;
        } else {
            QL_REQUIRE(!Double.isNaN(usedSpread), "null spread set");
        }

        List<CashFlow> floatLeg = new IborLeg(floatSchedule, iborIndex_)
                .withNotionals(nominal_)
                .withPaymentDayCounter(floatDayCount_)
                .withPaymentAdjustment(floatConvention_)
                .withFixingDays(iborIndex_.fixingDays())
                .withSpreads(usedSpread)
                .leg();

        Swap swap;
        if (payCms_)
            swap = new Swap(cmsLeg, floatLeg);
        else
            swap = new Swap(floatLeg, cmsLeg);
        swap.setPricingEngine(engine_);
        return swap;
    }

    public MakeCms receiveCms() {
        return receiveCms(true);
    }

    public MakeCms receiveCms(boolean flag) {
        payCms_ = !flag;
        return this;
    }

    public MakeCms withNominal(double n) {
        nominal_ = n;
        return this;
    }

    public MakeCms withIborSpread(double s) {
        iborSpread_ = s;
        return this;
    }

    public MakeCms withForwardStart(final Period f) {
        forwardStart_ = f;
        return this;
    }

    public MakeCms withEffectiveDate(final Date effectiveDate) {
        effectiveDate_ = effectiveDate;
        return this;
    }

    public MakeCms withDiscountingTermStructure(
            final Handle<YieldTermStructure> discountingTermStructure) {
        engine_ = new DiscountingMultiLegSwapEngine(discountingTermStructure);
        return this;
    }

    public MakeCms withCmsCouponPricer(final CmsCouponPricer couponPricer) {
        couponPricer_ = couponPricer;
        return this;
    }

    public MakeCms withCmsLegTenor(final Period t) {
        cmsTenor_ = t;
        return this;
    }

    public MakeCms withCmsLegCalendar(final Calendar cal) {
        cmsCalendar_ = cal;
        return this;
    }

    public MakeCms withCmsLegConvention(BusinessDayConvention bdc) {
        cmsConvention_ = bdc;
        return this;
    }

    public MakeCms withCmsLegTerminationDateConvention(
            BusinessDayConvention bdc) {
        cmsTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeCms withCmsLegRule(DateGenerationRule r) {
        cmsRule_ = r;
        return this;
    }

    public MakeCms withCmsLegEndOfMonth() {
        return withCmsLegEndOfMonth(true);
    }

    public MakeCms withCmsLegEndOfMonth(boolean flag) {
        cmsEndOfMonth_ = flag;
        return this;
    }

    public MakeCms withCmsLegFirstDate(final Date d) {
        cmsFirstDate_ = d;
        return this;
    }

    public MakeCms withCmsLegNextToLastDate(final Date d) {
        cmsNextToLastDate_ = d;
        return this;
    }

    public MakeCms withCmsLegDayCount(final DayCounter dc) {
        cmsDayCount_ = dc;
        return this;
    }

    public MakeCms withFloatingLegTenor(final Period t) {
        floatTenor_ = t;
        return this;
    }

    public MakeCms withFloatingLegCalendar(final Calendar cal) {
        floatCalendar_ = cal;
        return this;
    }

    public MakeCms withFloatingLegConvention(BusinessDayConvention bdc) {
        floatConvention_ = bdc;
        return this;
    }

    public MakeCms withFloatingLegTerminationDateConvention(
            BusinessDayConvention bdc) {
        floatTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeCms withFloatingLegRule(DateGenerationRule r) {
        floatRule_ = r;
        return this;
    }

    public MakeCms withFloatingLegEndOfMonth() {
        return withFloatingLegEndOfMonth(true);
    }

    public MakeCms withFloatingLegEndOfMonth(boolean flag) {
        floatEndOfMonth_ = flag;
        return this;
    }

    public MakeCms withFloatingLegFirstDate(final Date d) {
        floatFirstDate_ = d;
        return this;
    }

    public MakeCms withFloatingLegNextToLastDate(final Date d) {
        floatNextToLastDate_ = d;
        return this;
    }

    public MakeCms withFloatingLegDayCount(final DayCounter dc) {
        floatDayCount_ = dc;
        return this;
    }

    public MakeCms withAtmSpread() {
        return withAtmSpread(true);
    }

    public MakeCms withAtmSpread(boolean flag) {
        useAtmSpread_ = flag;
        return this;
    }
}
