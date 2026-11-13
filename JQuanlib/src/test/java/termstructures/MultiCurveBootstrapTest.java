package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.instruments.ForwardRateAgreement;
import jquant.instruments.Swap;
import jquant.instruments.VanillaSwap;
import jquant.math.interpolations.impl.LogLinear;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.MultiCurve;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.FraRateHelper;
import jquant.termstructures.yield.GlobalBootstrap;
import jquant.termstructures.yield.IborIborBasisSwapRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.termstructures.yield.ZeroSpreadedTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code testMultiCurveTwoPiecewiseYieldCurves} and
 * {@code testMultiCurvePiecewiseYieldCurveAndSpreadedCurve}
 * (test-suite/piecewiseyieldcurve.cpp, lines 1628 and 1768).
 *
 * <p>All dates, quotes and schedules are copied from the official tests. The
 * {@code QL_CHECK_CLOSE} assertions use a relative tolerance of 1E-10 and the
 * {@code QL_CHECK_SMALL} assertions an absolute tolerance of 1E-10.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 */
public class MultiCurveBootstrapTest {
    private static final double ACCURACY = 1.0e-10;
    private static final double TOLERANCE = 1.0e-10;

    @Test
    public void testMultiCurveTwoPiecewiseYieldCurves() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();

            Calendar calendar = new Target();
            Date today = calendar.adjust(new Date(23, Month.OCTOBER, 2025),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            Date settlement = calendar.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);

            DayCounter actual360 = new Actual360(false);
            YieldTermStructure flat = new FlatForward(settlement, 0.02, actual360,
                    Compounding.Continuous, Frequency.ANNUAL);
            Handle<YieldTermStructure> discountCurve = new Handle<>(flat, false);

            RelinkableHandle<YieldTermStructure> intcurve3m = new RelinkableHandle<>();
            RelinkableHandle<YieldTermStructure> intcurve6m = new RelinkableHandle<>();

            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), intcurve3m);
            IborIndex euribor6m = euribor(new Period(6, TimeUnit.MONTHS), intcurve6m);

            Handle<Quote> q = new Handle<>(new SimpleQuote(0.03), false);
            Handle<Quote> b = new Handle<>(new SimpleQuote(0.0020), false);

            List<RateHelper> helpers3m = new ArrayList<>();
            List<RateHelper> helpers6m = new ArrayList<>();

            for (int i = 1; i <= 9; ++i) {
                helpers3m.add(new FraRateHelper(q, i, i + 3,
                        euribor3m.fixingDays(), euribor3m.fixingCalendar(),
                        euribor3m.businessDayConvention(), euribor3m.endOfMonth(),
                        euribor3m.dayCounter(), FraRateHelper.Pillar.LastRelevantDate, true));
            }

            for (int i = 2; i <= 10; ++i) {
                helpers3m.add(new IborIborBasisSwapRateHelper(b,
                        new Period(i, TimeUnit.YEARS), euribor3m.fixingDays(),
                        euribor3m.fixingCalendar(), euribor3m.businessDayConvention(),
                        euribor3m.endOfMonth(), euribor3m, euribor6m, discountCurve, true));
            }

            for (int i = 1; i <= 3; ++i) {
                helpers6m.add(new IborIborBasisSwapRateHelper(b,
                        new Period(i * 6, TimeUnit.MONTHS), euribor3m.fixingDays(),
                        euribor3m.fixingCalendar(), euribor3m.businessDayConvention(),
                        euribor3m.endOfMonth(), euribor3m, euribor6m, discountCurve, false));
            }

            for (int i = 2; i <= 10; ++i) {
                helpers6m.add(new SwapRateHelper(q, new Period(i, TimeUnit.YEARS),
                        euribor6m.fixingCalendar(), Frequency.ANNUAL,
                        BusinessDayConvention.FOLLOWING,
                        new Thirty360(Thirty360.Convention.BondBasis), euribor6m,
                        new Handle<>(null, false), new Period(0, TimeUnit.DAYS),
                        discountCurve));
            }

            YieldTermStructure ptr3m = new PiecewiseYieldCurve(today, helpers3m,
                    actual360, BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(ACCURACY));
            YieldTermStructure ptr6m = new PiecewiseYieldCurve(today, helpers6m,
                    actual360, BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(ACCURACY));

            MultiCurve multiCurve = new MultiCurve(ACCURACY);
            Handle<YieldTermStructure> curve3m =
                    multiCurve.addBootstrappedCurve(intcurve3m, ptr3m);
            Handle<YieldTermStructure> curve6m =
                    multiCurve.addBootstrappedCurve(intcurve6m, ptr6m);

            // check instrument npvs
            for (int i = 1; i <= 9; ++i) {
                Date start = euribor3m.fixingCalendar().advance(
                        euribor3m.fixingCalendar().advance(today, euribor3m.fixingDays(),
                                TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false),
                        i, TimeUnit.MONTHS, euribor3m.businessDayConvention(),
                        euribor3m.endOfMonth());
                ForwardRateAgreement fra = new ForwardRateAgreement(euribor3m, start,
                        ForwardRateAgreement.Position.Long, q.getValue().value(), 1.0,
                        curve3m.currentLink());
                assertRelativeClose(q.getValue().value(), fra.forwardRate(), TOLERANCE,
                        "FRA forward rate " + i);
            }

            for (int i = 2; i <= 10; ++i) {
                Date start = euribor3m.fixingCalendar().advance(today,
                        euribor3m.fixingDays(), TimeUnit.DAYS,
                        BusinessDayConvention.FOLLOWING, false);
                Date maturity = euribor3m.fixingCalendar().advance(start, i,
                        TimeUnit.YEARS, euribor3m.businessDayConvention(), false);
                Schedule baseSchedule = new MakeSchedule()
                        .from(start)
                        .to(maturity)
                        .withTenor(new Period(3, TimeUnit.MONTHS))
                        .withCalendar(euribor3m.fixingCalendar())
                        .withConvention(euribor3m.businessDayConvention())
                        .endOfMonth(euribor3m.endOfMonth())
                        .forwards()
                        .toSchedule();
                Schedule otherSchedule = new MakeSchedule()
                        .from(start)
                        .to(maturity)
                        .withTenor(new Period(6, TimeUnit.MONTHS))
                        .withCalendar(euribor6m.fixingCalendar())
                        .withConvention(euribor6m.businessDayConvention())
                        .endOfMonth(euribor6m.endOfMonth())
                        .forwards()
                        .toSchedule();
                List<jquant.CashFlow> baseLeg = new IborLeg(baseSchedule, euribor3m)
                        .withSpreads(b.getValue().value())
                        .withNotionals(1.0)
                        .withPaymentDayCounter(euribor3m.dayCounter())
                        .withPaymentAdjustment(euribor3m.businessDayConvention())
                        .withPaymentCalendar(euribor3m.fixingCalendar())
                        .withIndexedCoupons(Optional.empty())
                        .leg();
                List<jquant.CashFlow> otherLeg = new IborLeg(otherSchedule, euribor6m)
                        .withNotionals(1.0)
                        .withPaymentDayCounter(euribor6m.dayCounter())
                        .withPaymentAdjustment(euribor6m.businessDayConvention())
                        .withPaymentCalendar(euribor6m.fixingCalendar())
                        .withIndexedCoupons(Optional.empty())
                        .leg();
                Swap swap = new Swap(baseLeg, otherLeg);
                swap.setPricingEngine(new DiscountingMultiLegSwapEngine(discountCurve));
                assertSmall(swap.NPV(), TOLERANCE, "3M basis swap " + i);
            }

            for (int i = 1; i <= 3; ++i) {
                Date start = euribor3m.fixingCalendar().advance(today,
                        euribor3m.fixingDays(), TimeUnit.DAYS,
                        BusinessDayConvention.FOLLOWING, false);
                Date maturity = euribor3m.fixingCalendar().advance(start, i * 6,
                        TimeUnit.MONTHS, euribor3m.businessDayConvention(), false);
                Schedule baseSchedule = new MakeSchedule()
                        .from(start)
                        .to(maturity)
                        .withTenor(new Period(3, TimeUnit.MONTHS))
                        .withCalendar(euribor3m.fixingCalendar())
                        .withConvention(euribor3m.businessDayConvention())
                        .endOfMonth(euribor3m.endOfMonth())
                        .forwards()
                        .toSchedule();
                Schedule otherSchedule = new MakeSchedule()
                        .from(start)
                        .to(maturity)
                        .withTenor(new Period(6, TimeUnit.MONTHS))
                        .withCalendar(euribor6m.fixingCalendar())
                        .withConvention(euribor6m.businessDayConvention())
                        .endOfMonth(euribor6m.endOfMonth())
                        .forwards()
                        .toSchedule();
                List<jquant.CashFlow> baseLeg = new IborLeg(baseSchedule, euribor3m)
                        .withSpreads(b.getValue().value())
                        .withNotionals(1.0)
                        .withPaymentDayCounter(euribor3m.dayCounter())
                        .withPaymentAdjustment(euribor3m.businessDayConvention())
                        .withPaymentCalendar(euribor3m.fixingCalendar())
                        .withIndexedCoupons(Optional.empty())
                        .leg();
                List<jquant.CashFlow> otherLeg = new IborLeg(otherSchedule, euribor6m)
                        .withNotionals(1.0)
                        .withPaymentDayCounter(euribor6m.dayCounter())
                        .withPaymentAdjustment(euribor6m.businessDayConvention())
                        .withPaymentCalendar(euribor6m.fixingCalendar())
                        .withIndexedCoupons(Optional.empty())
                        .leg();
                Swap swap = new Swap(baseLeg, otherLeg);
                swap.setPricingEngine(new DiscountingMultiLegSwapEngine(discountCurve));
                assertSmall(swap.NPV(), TOLERANCE, "6M basis swap " + i);
            }

            for (int i = 2; i <= 10; ++i) {
                VanillaSwap swap = vanillaSwap(i, euribor6m, q.getValue().value());
                swap.setPricingEngine(new DiscountingSwapEngine(discountCurve));
                assertSmall(swap.NPV(), TOLERANCE, "vanilla swap " + i);
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            if (savedAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
    }

    @Test
    public void testMultiCurvePiecewiseYieldCurveAndSpreadedCurve() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();

            Calendar calendar = new Target();
            Date today = calendar.adjust(new Date(23, Month.OCTOBER, 2025),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);

            RelinkableHandle<YieldTermStructure> intcurveois = new RelinkableHandle<>();
            RelinkableHandle<YieldTermStructure> intcurve3m = new RelinkableHandle<>();

            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), intcurve3m);

            Handle<Quote> q = new Handle<>(new SimpleQuote(0.03), false);
            Handle<Quote> b = new Handle<>(new SimpleQuote(-0.01), false);

            List<RateHelper> helpers3m = new ArrayList<>();
            for (int i = 1; i <= 10; ++i) {
                helpers3m.add(new SwapRateHelper(q, new Period(i, TimeUnit.YEARS),
                        euribor3m.fixingCalendar(), Frequency.ANNUAL,
                        BusinessDayConvention.FOLLOWING,
                        new Thirty360(Thirty360.Convention.BondBasis), euribor3m,
                        new Handle<>(null, false), new Period(0, TimeUnit.DAYS),
                        intcurveois));
            }

            MultiCurve multiCurve = new MultiCurve(ACCURACY);

            YieldTermStructure ptr3m = new PiecewiseYieldCurve(today, helpers3m,
                    new Actual360(false), BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(ACCURACY));
            Handle<YieldTermStructure> curve3m =
                    multiCurve.addBootstrappedCurve(intcurve3m, ptr3m);

            YieldTermStructure ptrois = new ZeroSpreadedTermStructure(intcurve3m, b);
            Handle<YieldTermStructure> curveois =
                    multiCurve.addNonBootstrappedCurve(intcurveois, ptrois);

            // check spread ois 3m
            double zeroOis = curveois.currentLink().zeroRate(1.0,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            double zero3m = curve3m.currentLink().zeroRate(1.0,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            assertRelativeClose(b.getValue().value(), zeroOis - zero3m, TOLERANCE,
                    "ois 3m zero-rate spread");

            // check instrument npvs
            for (int i = 1; i <= 10; ++i) {
                VanillaSwap swap = vanillaSwap(i, euribor3m, q.getValue().value());
                swap.setPricingEngine(new DiscountingSwapEngine(curveois));
                assertSmall(swap.NPV(), TOLERANCE, "vanilla swap " + i);
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            if (savedAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
    }

    /**
     * Observability of the joint system, following the official
     * {@code testObservability} pattern in test-suite/piecewiseyieldcurve.cpp
     * (bump a helper quote by 1% and require a recalculation). The bumped
     * quote here is the spread of the non-bootstrapped OIS curve: the 3M
     * member curve only becomes stale if the MultiCurve registered with the
     * OIS curve's observables and re-bootstraps both curves jointly.
     */
    @Test
    public void testMultiCurveObservability() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();

            Calendar calendar = new Target();
            Date today = calendar.adjust(new Date(23, Month.OCTOBER, 2025),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);

            RelinkableHandle<YieldTermStructure> intcurveois = new RelinkableHandle<>();
            RelinkableHandle<YieldTermStructure> intcurve3m = new RelinkableHandle<>();

            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), intcurve3m);

            Handle<Quote> q = new Handle<>(new SimpleQuote(0.03), false);
            Handle<Quote> b = new Handle<>(new SimpleQuote(-0.01), false);

            List<RateHelper> helpers3m = new ArrayList<>();
            for (int i = 1; i <= 10; ++i) {
                helpers3m.add(new SwapRateHelper(q, new Period(i, TimeUnit.YEARS),
                        euribor3m.fixingCalendar(), Frequency.ANNUAL,
                        BusinessDayConvention.FOLLOWING,
                        new Thirty360(Thirty360.Convention.BondBasis), euribor3m,
                        new Handle<>(null, false), new Period(0, TimeUnit.DAYS),
                        intcurveois));
            }

            MultiCurve multiCurve = new MultiCurve(ACCURACY);

            YieldTermStructure ptr3m = new PiecewiseYieldCurve(today, helpers3m,
                    new Actual360(false), BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(ACCURACY));
            Handle<YieldTermStructure> curve3m =
                    multiCurve.addBootstrappedCurve(intcurve3m, ptr3m);

            YieldTermStructure ptrois = new ZeroSpreadedTermStructure(intcurve3m, b);
            Handle<YieldTermStructure> curveois =
                    multiCurve.addNonBootstrappedCurve(intcurveois, ptrois);

            // Initial reading forces the joint bootstrap.
            double initialDiscount3m = curve3m.currentLink().discount(1.0, true);
            double initialZero3m = curve3m.currentLink().zeroRate(1.0,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            double initialZeroOis = curveois.currentLink().zeroRate(1.0,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            assertRelativeClose(b.getValue().value(), initialZeroOis - initialZero3m,
                    TOLERANCE, "initial ois 3m zero-rate spread");

            // Official testObservability bump.
            SimpleQuote spreadQuote = (SimpleQuote) b.getValue();
            spreadQuote.setValue(spreadQuote.value() * 1.01);
            double newSpread = spreadQuote.value();

            // The 3M curve bootstraps against the OIS curve, so it must have
            // been recalculated as well.
            double bumpedDiscount3m = curve3m.currentLink().discount(1.0, true);
            assertTrue(bumpedDiscount3m != initialDiscount3m,
                    "spread change did not trigger recalculation of the member curve");

            double zero3m = curve3m.currentLink().zeroRate(1.0,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            double zeroOis = curveois.currentLink().zeroRate(1.0,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            assertRelativeClose(newSpread, zeroOis - zero3m, TOLERANCE,
                    "bumped ois 3m zero-rate spread");

            // The joint system must still be calibrated to the new market.
            for (int i = 1; i <= 10; ++i) {
                VanillaSwap swap = vanillaSwap(i, euribor3m, q.getValue().value());
                swap.setPricingEngine(new DiscountingSwapEngine(curveois));
                assertSmall(swap.NPV(), TOLERANCE, "bumped vanilla swap " + i);
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            if (savedAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
    }

    /**
     * JQuant has no {@code MakeVanillaSwap}, so this reproduces the official
     * builder with the parameters used by the tests: payer, unit nominal,
     * settlement days equal to the index fixing days, unadjusted
     * start + tenor termination, 1Y fixed leg with Following convention,
     * Backward schedules and no end-of-month adjustment.
     */
    private static VanillaSwap vanillaSwap(int years, IborIndex index, double fixedRate) {
        Calendar calendar = index.fixingCalendar();
        Date today = Settings.instance.evaluationDate().Date();
        Date start = calendar.advance(today, index.fixingDays(), TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        Date end = start.add(new Period(years, TimeUnit.YEARS));
        Schedule fixed = new Schedule(start, end, new Period(1, TimeUnit.YEARS),
                calendar, BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING, DateGenerationRule.BACKWARD,
                false, new Date(), new Date());
        Schedule floating = new Schedule(start, end, index.tenor(), calendar,
                index.businessDayConvention(), index.businessDayConvention(),
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        return new VanillaSwap(VanillaSwap.Type.Payer, 1.0, fixed, fixedRate,
                new Thirty360(Thirty360.Convention.BondBasis), floating, index,
                0.0, index.dayCounter());
    }

    private static IborIndex euribor(Period tenor,
                                     Handle<YieldTermStructure> forwarding) {
        return new IborIndex("Euribor", tenor, 2, new Currency(), new Target(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true,
                new Actual360(false), forwarding);
    }

    private static void assertRelativeClose(double expected, double actual,
                                            double tolerance, String message) {
        double scale = Math.max(Math.abs(expected), Math.abs(actual));
        double error = scale == 0.0 ? Math.abs(expected - actual)
                : Math.abs(expected - actual) / scale;
        assertTrue(error <= tolerance,
                message + ": expected " + expected + " but was " + actual
                        + " (relative error " + error + " > " + tolerance + ")");
    }

    private static void assertSmall(double value, double tolerance, String message) {
        assertTrue(Math.abs(value) <= tolerance,
                message + ": |" + value + "| > " + tolerance);
    }
}
