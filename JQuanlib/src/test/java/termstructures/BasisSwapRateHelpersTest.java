package termstructures;

import jquant.Compounding;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.TimeSeries;
import jquant.cashflows.Coupon;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.cashflows.OvernightIndexedCoupon;
import jquant.cashflows.OvernightLeg;
import jquant.cashflows.RateAveraging;
import jquant.indexes.FedFunds;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.OvernightIndex;
import jquant.indexes.Sofr;
import jquant.indexes.USDLibor;
import jquant.instruments.Swap;
import jquant.math.interpolations.impl.Linear;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.IborIborBasisSwapRateHelper;
import jquant.termstructures.yield.OvernightIborBasisSwapRateHelper;
import jquant.termstructures.yield.OvernightOvernightBasisSwapRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
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
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official tests for {@code OvernightIborBasisSwapRateHelper} and
 * {@code OvernightOvernightBasisSwapRateHelper}, ported from
 * {@code test-suite/basisswapratehelpers.cpp}.
 */
class BasisSwapRateHelpersTest {

    private record BasisSwapQuote(int n, TimeUnit units, double basis) {
    }

    private static YieldTermStructure flatRate(double rate, DayCounter dc) {
        return new FlatForward(0, new NullCalendar(), rate, dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static Handle<Quote> quote(double value) {
        return new Handle<>(new SimpleQuote(value), false);
    }

    private static Date referenceDate(Calendar calendar) {
        return calendar.adjust(Settings.instance.evaluationDate().Date(),
                BusinessDayConvention.FOLLOWING);
    }

    private static Map<String, TimeSeries<Double>> snapshotHistories() {
        Map<String, TimeSeries<Double>> histories = new LinkedHashMap<>();
        for (String name : IndexManager.Instance.histories()) {
            TimeSeries<Double> series = IndexManager.Instance.getHistory(name);
            histories.put(name, new TimeSeries<>(series.dates(), series.values()));
        }
        return histories;
    }

    private static void restoreHistories(Map<String, TimeSeries<Double>> histories) {
        IndexManager.Instance.clearHistories();
        for (Map.Entry<String, TimeSeries<Double>> entry : histories.entrySet())
            IndexManager.Instance.setHistory(entry.getKey(), entry.getValue());
    }

    private static void testOvernightIborBootstrap(boolean externalDiscountCurve,
                                                   boolean bootstrapBaseCurve,
                                                   int paymentLag,
                                                   boolean linkDiscountCurveAfterConstruction,
                                                   RateAveraging averagingMethod,
                                                   boolean telescopicValueDates) {
        // The official test suite runs on a business day, where the first
        // USDLibor fixing of a spot-starting schedule is not in the past.
        // Pin such a date so that the test does not depend on the weekday
        // on which it happens to run.
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
        try {
            testOvernightIborBootstrapImpl(externalDiscountCurve, bootstrapBaseCurve,
                    paymentLag, linkDiscountCurveAfterConstruction, averagingMethod,
                    telescopicValueDates);
        } finally {
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    private static void testOvernightIborBootstrapImpl(boolean externalDiscountCurve,
                                                       boolean bootstrapBaseCurve,
                                                       int paymentLag,
                                                       boolean linkDiscountCurveAfterConstruction,
                                                       RateAveraging averagingMethod,
                                                       boolean telescopicValueDates) {
        BasisSwapQuote[] quotes = {
                new BasisSwapQuote(1, TimeUnit.YEARS, 0.0010),
                new BasisSwapQuote(2, TimeUnit.YEARS, 0.0012),
                new BasisSwapQuote(3, TimeUnit.YEARS, 0.0015),
                new BasisSwapQuote(5, TimeUnit.YEARS, 0.0015),
                new BasisSwapQuote(8, TimeUnit.YEARS, 0.0018),
                new BasisSwapQuote(10, TimeUnit.YEARS, 0.0020),
                new BasisSwapQuote(15, TimeUnit.YEARS, 0.0021),
                new BasisSwapQuote(20, TimeUnit.YEARS, 0.0021),
        };

        int settlementDays = 2;
        Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
        BusinessDayConvention convention = BusinessDayConvention.FOLLOWING;
        boolean endOfMonth = false;

        Handle<YieldTermStructure> knownForecastCurve =
                new Handle<>(flatRate(0.01, new Actual365Fixed()), false);

        RelinkableHandle<YieldTermStructure> discountCurve = new RelinkableHandle<>();
        if (externalDiscountCurve && !linkDiscountCurveAfterConstruction)
            discountCurve.linkTo(flatRate(0.005, new Actual365Fixed()), true);

        OvernightIndex baseIndex;
        IborIndex otherIndex;
        if (bootstrapBaseCurve) {
            baseIndex = new Sofr();
            otherIndex = new USDLibor(new Period(6, TimeUnit.MONTHS), knownForecastCurve);
        } else {
            baseIndex = new Sofr(knownForecastCurve);
            otherIndex = new USDLibor(new Period(6, TimeUnit.MONTHS));
        }

        List<RateHelper> helpers = new ArrayList<>();
        for (BasisSwapQuote q : quotes) {
            helpers.add(new OvernightIborBasisSwapRateHelper(
                    quote(q.basis), new Period(q.n, q.units), settlementDays,
                    calendar, convention, endOfMonth, baseIndex, otherIndex,
                    discountCurve, bootstrapBaseCurve, paymentLag, null, null,
                    DateGenerationRule.BACKWARD, averagingMethod,
                    telescopicValueDates));
        }

        PiecewiseYieldCurve bootstrappedCurve = new PiecewiseYieldCurve(
                referenceDate(calendar), helpers, new Actual365Fixed(),
                BootstrapTraits.ZeroYield, new Linear());

        if (linkDiscountCurveAfterConstruction) {
            bootstrappedCurve.discount(helpers.get(helpers.size() - 1).pillarDate(), false);
            discountCurve.linkTo(flatRate(0.005, new Actual365Fixed()), true);
        }

        Date today = Settings.instance.evaluationDate().Date();
        Date spot = calendar.advance(today, settlementDays, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);

        if (bootstrapBaseCurve) {
            baseIndex = new Sofr(new Handle<>(bootstrappedCurve, false));
            otherIndex = new USDLibor(new Period(6, TimeUnit.MONTHS), knownForecastCurve);
        } else {
            baseIndex = new Sofr(knownForecastCurve);
            otherIndex = new USDLibor(new Period(6, TimeUnit.MONTHS),
                    new Handle<>(bootstrappedCurve, false));
        }

        for (BasisSwapQuote q : quotes) {
            Date maturity = spot.add(new Period(q.n, q.units));

            Schedule s = new MakeSchedule()
                    .from(spot)
                    .to(maturity)
                    .withTenor(otherIndex.tenor())
                    .withCalendar(calendar)
                    .withConvention(convention)
                    .withRule(DateGenerationRule.BACKWARD)
                    .toSchedule();

            List<jquant.CashFlow> leg1 = new OvernightLeg(s, baseIndex)
                    .withSpreads(q.basis)
                    .withNotionals(100.0)
                    .withPaymentLag(paymentLag)
                    .withTelescopicValueDates(telescopicValueDates
                            && averagingMethod == RateAveraging.Compound)
                    .withAveragingMethod(averagingMethod)
                    .build();
            List<jquant.CashFlow> leg2 = new IborLeg(s, otherIndex)
                    .withNotionals(100.0)
                    .withPaymentLag(paymentLag)
                    .leg();

            Swap swap = new Swap(leg1, leg2);
            swap.setPricingEngine(externalDiscountCurve
                    ? new DiscountingMultiLegSwapEngine(discountCurve)
                    : new DiscountingMultiLegSwapEngine(
                            new Handle<>(bootstrappedCurve, false)));

            double npv = swap.NPV();
            assertEquals(0.0, npv, 1e-8,
                    "Failed to price fair " + q.n + "-year(s) swap: " + npv);
        }
    }

    private static void testOvernightOvernightBootstrap(boolean externalDiscountCurve,
                                                         boolean bootstrapBaseCurve,
                                                         boolean linkDiscountCurveAfterConstruction) {
        // See testOvernightIborBootstrap above: pin a business-day evaluation
        // date so that the first fixing of the spot-starting swaps is not
        // already in the past.
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
        try {
            testOvernightOvernightBootstrapImpl(externalDiscountCurve, bootstrapBaseCurve,
                    linkDiscountCurveAfterConstruction);
        } finally {
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    private static void testOvernightOvernightBootstrapImpl(boolean externalDiscountCurve,
                                                             boolean bootstrapBaseCurve,
                                                             boolean linkDiscountCurveAfterConstruction) {
        BasisSwapQuote[] quotes = {
                new BasisSwapQuote(1, TimeUnit.YEARS, 0.0008),
                new BasisSwapQuote(2, TimeUnit.YEARS, 0.0010),
                new BasisSwapQuote(3, TimeUnit.YEARS, 0.0011),
                new BasisSwapQuote(5, TimeUnit.YEARS, 0.0013),
                new BasisSwapQuote(10, TimeUnit.YEARS, 0.0015),
        };

        int settlementDays = 2;
        Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
        BusinessDayConvention convention = BusinessDayConvention.FOLLOWING;
        boolean endOfMonth = false;
        int paymentLag = 2;
        Frequency paymentFrequency = Frequency.QUARTERLY;
        RateAveraging baseAveragingMethod = RateAveraging.Simple;
        RateAveraging otherAveragingMethod = RateAveraging.Compound;

        Handle<YieldTermStructure> knownForecastCurve =
                new Handle<>(flatRate(0.01, new Actual365Fixed()), false);

        RelinkableHandle<YieldTermStructure> discountCurve = new RelinkableHandle<>();
        if (externalDiscountCurve && !linkDiscountCurveAfterConstruction)
            discountCurve.linkTo(flatRate(0.005, new Actual365Fixed()), true);

        OvernightIndex baseIndex;
        OvernightIndex otherIndex;
        if (bootstrapBaseCurve) {
            baseIndex = new FedFunds();
            otherIndex = new Sofr(knownForecastCurve);
        } else {
            baseIndex = new FedFunds(knownForecastCurve);
            otherIndex = new Sofr();
        }

        List<OvernightOvernightBasisSwapRateHelper> basisHelpers = new ArrayList<>();
        List<RateHelper> helpers = new ArrayList<>();
        for (BasisSwapQuote q : quotes) {
            OvernightOvernightBasisSwapRateHelper h =
                    new OvernightOvernightBasisSwapRateHelper(
                            quote(q.basis), new Period(q.n, q.units), settlementDays,
                            calendar, convention, endOfMonth, baseIndex, otherIndex,
                            discountCurve, bootstrapBaseCurve, paymentLag,
                            paymentFrequency, baseAveragingMethod,
                            otherAveragingMethod);
            basisHelpers.add(h);
            helpers.add(h);
        }

        OvernightIndexedCoupon firstBaseCoupon =
                (OvernightIndexedCoupon) basisHelpers.get(0).swap().leg(0).get(0);
        OvernightIndexedCoupon firstOtherCoupon =
                (OvernightIndexedCoupon) basisHelpers.get(0).swap().leg(1).get(0);
        assertEquals(baseAveragingMethod, firstBaseCoupon.averagingMethod());
        assertEquals(otherAveragingMethod, firstOtherCoupon.averagingMethod());
        assertEquals(4, basisHelpers.get(0).swap().leg(0).size());
        assertEquals(4, basisHelpers.get(0).swap().leg(1).size());

        PiecewiseYieldCurve bootstrappedCurve = new PiecewiseYieldCurve(
                referenceDate(calendar), helpers, new Actual365Fixed(),
                BootstrapTraits.ZeroYield, new Linear());
        Handle<YieldTermStructure> bootstrappedCurveHandle =
                new Handle<>(bootstrappedCurve, false);

        if (linkDiscountCurveAfterConstruction) {
            bootstrappedCurve.discount(
                    basisHelpers.get(basisHelpers.size() - 1).pillarDate(), false);
            discountCurve.linkTo(flatRate(0.005, new Actual365Fixed()), true);
        }

        if (bootstrapBaseCurve) {
            baseIndex = new FedFunds(bootstrappedCurveHandle);
            otherIndex = new Sofr(knownForecastCurve);
        } else {
            baseIndex = new FedFunds(knownForecastCurve);
            otherIndex = new Sofr(bootstrappedCurveHandle);
        }

        Date today = Settings.instance.evaluationDate().Date();
        Date spot = calendar.advance(today, settlementDays, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);

        for (int i = 0; i < quotes.length; ++i) {
            BasisSwapQuote q = quotes[i];
            Date maturity = calendar.advance(spot, new Period(q.n, q.units),
                    convention, false);

            Schedule schedule = new MakeSchedule()
                    .from(spot)
                    .to(maturity)
                    .withFrequency(paymentFrequency)
                    .withCalendar(calendar)
                    .withConvention(convention)
                    .withRule(DateGenerationRule.FORWARD)
                    .toSchedule();

            List<jquant.CashFlow> baseLeg = new OvernightLeg(schedule, baseIndex)
                    .withNotionals(100.0)
                    .withSpreads(q.basis)
                    .withPaymentLag(paymentLag)
                    .withAveragingMethod(baseAveragingMethod)
                    .build();
            List<jquant.CashFlow> otherLeg = new OvernightLeg(schedule, otherIndex)
                    .withNotionals(100.0)
                    .withPaymentLag(paymentLag)
                    .withAveragingMethod(otherAveragingMethod)
                    .build();

            Swap swap = new Swap(baseLeg, otherLeg);
            swap.setPricingEngine(externalDiscountCurve
                    ? new DiscountingMultiLegSwapEngine(discountCurve)
                    : new DiscountingMultiLegSwapEngine(bootstrappedCurveHandle));

            assertEquals(0.0, swap.NPV(), 1e-8);
            assertEquals(q.basis, basisHelpers.get(i).impliedQuote(), 1e-8);
        }
    }

    @Test
    void testBasisSwapRateHelpersRejectNullIndexes() {
        Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
        Handle<YieldTermStructure> curve =
                new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
        Handle<Quote> basis = quote(0.0010);
        OvernightIndex overnightIndex = new Sofr(curve);
        IborIndex iborIndex = new USDLibor(new Period(3, TimeUnit.MONTHS), curve);

        assertThrows(IllegalArgumentException.class, () ->
                new IborIborBasisSwapRateHelper(basis, new Period(2, TimeUnit.YEARS),
                        2, calendar, BusinessDayConvention.FOLLOWING, false,
                        null, iborIndex, curve, false));
        assertThrows(IllegalArgumentException.class, () ->
                new IborIborBasisSwapRateHelper(basis, new Period(2, TimeUnit.YEARS),
                        2, calendar, BusinessDayConvention.FOLLOWING, false,
                        iborIndex, null, curve, false));
        assertThrows(IllegalArgumentException.class, () ->
                new OvernightIborBasisSwapRateHelper(basis, new Period(2, TimeUnit.YEARS),
                        2, calendar, BusinessDayConvention.FOLLOWING, false,
                        null, iborIndex, curve, false));
        assertThrows(IllegalArgumentException.class, () ->
                new OvernightIborBasisSwapRateHelper(basis, new Period(2, TimeUnit.YEARS),
                        2, calendar, BusinessDayConvention.FOLLOWING, false,
                        overnightIndex, null, curve, false));
    }

    @Test
    void testOvernightIborDifferentPaymentFrequencies() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> curve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex overnightIndex = new Sofr(curve);
            IborIndex iborIndex = new USDLibor(new Period(3, TimeUnit.MONTHS), curve);

            // the overnight leg pays annually, the ibor leg at the tenor of its index
            OvernightIborBasisSwapRateHelper helper =
                    new OvernightIborBasisSwapRateHelper(
                            quote(0.0010), new Period(2, TimeUnit.YEARS), 2,
                            calendar, BusinessDayConvention.FOLLOWING, false,
                            overnightIndex, iborIndex, curve, false, 0,
                            Frequency.ANNUAL);
            OvernightIborBasisSwapRateHelper zeroCouponHelper =
                    new OvernightIborBasisSwapRateHelper(
                            quote(0.0010), new Period(2, TimeUnit.YEARS), 2,
                            calendar, BusinessDayConvention.FOLLOWING, false,
                            overnightIndex, iborIndex, curve, false, 0,
                            Frequency.NO_FREQUENCY);

            assertEquals(2, helper.swap().leg(0).size());
            assertEquals(8, helper.swap().leg(1).size());
            assertEquals(1, zeroCouponHelper.swap().leg(0).size());
            assertEquals(8, zeroCouponHelper.swap().leg(1).size());
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborIndexedCouponOverride() {
        boolean initiallyUsingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            // see testIborIborIndexedCouponOverride for the choice of date
            Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> curve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex overnightIndex = new Sofr(curve);
            IborIndex iborIndex = new USDLibor(new Period(3, TimeUnit.MONTHS), curve);
            Handle<Quote> basis = quote(0.0010);

            IborCoupon.Settings.instance().createAtParCoupons();
            OvernightIborBasisSwapRateHelper indexedHelper =
                    new OvernightIborBasisSwapRateHelper(
                            basis, new Period(2, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, true);

            IborCoupon.Settings.instance().createIndexedCoupons();
            OvernightIborBasisSwapRateHelper atParHelper =
                    new OvernightIborBasisSwapRateHelper(
                            basis, new Period(2, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, false);

            IborCoupon indexedCoupon =
                    (IborCoupon) indexedHelper.swap().leg(1).get(0);
            IborCoupon atParCoupon =
                    (IborCoupon) atParHelper.swap().leg(1).get(0);
            assertTrue(TimeUtils.equals(indexedCoupon.fixingEndDate(),
                    indexedCoupon.fixingMaturityDate()));
            assertTrue(TimeUtils.neq(atParCoupon.fixingEndDate(),
                    atParCoupon.fixingMaturityDate()));
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            if (initiallyUsingAtParCoupons)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborDateGenerationRule() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> curve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex overnightIndex = new Sofr(curve);
            // an annual index, so that both legs have the same stub
            IborIndex iborIndex = new USDLibor(new Period(1, TimeUnit.YEARS), curve);
            Handle<Quote> basis = quote(0.0010);

            Date spot = calendar.advance(Settings.instance.evaluationDate().Date(),
                    2, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);

            // 18M against an annual frequency leaves a six-month stub
            OvernightIborBasisSwapRateHelper backwardHelper =
                    new OvernightIborBasisSwapRateHelper(
                            basis, new Period(18, TimeUnit.MONTHS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, null,
                            DateGenerationRule.BACKWARD);
            OvernightIborBasisSwapRateHelper forwardHelper =
                    new OvernightIborBasisSwapRateHelper(
                            basis, new Period(18, TimeUnit.MONTHS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, null,
                            DateGenerationRule.FORWARD);

            for (int i = 0; i < 2; ++i) {
                assertEquals(2, backwardHelper.swap().leg(i).size());
                assertEquals(2, forwardHelper.swap().leg(i).size());
                assertTrue(TimeUtils.equals(
                        ((Coupon) backwardHelper.swap().leg(i).get(0)).accrualEndDate(),
                        calendar.adjust(spot.add(new Period(6, TimeUnit.MONTHS)),
                                BusinessDayConvention.FOLLOWING)));
                assertTrue(TimeUtils.equals(
                        ((Coupon) forwardHelper.swap().leg(i).get(0)).accrualEndDate(),
                        calendar.adjust(spot.add(new Period(1, TimeUnit.YEARS)),
                                BusinessDayConvention.FOLLOWING)));
            }

            Date expectedMaturity = calendar.adjust(
                    spot.add(new Period(18, TimeUnit.MONTHS)),
                    BusinessDayConvention.FOLLOWING);
            assertTrue(TimeUtils.equals(backwardHelper.maturityDate(), expectedMaturity));
            assertTrue(TimeUtils.equals(forwardHelper.maturityDate(), expectedMaturity));
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborBootstrapWithoutDiscountCurve() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightIborBootstrap(false, false, 0, false,
                    RateAveraging.Compound, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborBootstrapWithDiscountCurve() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightIborBootstrap(true, false, 0, false,
                    RateAveraging.Compound, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborBootstrapWithLateLinkedDiscountCurve() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightIborBootstrap(true, false, 0, true,
                    RateAveraging.Compound, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborBaseCurveBootstrapWithoutDiscountCurve() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightIborBootstrap(false, true, 0, false,
                    RateAveraging.Compound, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborBaseCurveBootstrapWithDiscountCurve() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightIborBootstrap(true, true, 0, false,
                    RateAveraging.Compound, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborBootstrapWithPaymentLag() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightIborBootstrap(false, false, 2, false,
                    RateAveraging.Compound, false);
            testOvernightIborBootstrap(true, false, 2, false,
                    RateAveraging.Compound, false);
            testOvernightIborBootstrap(false, true, 2, false,
                    RateAveraging.Compound, false);
            testOvernightIborBootstrap(true, true, 2, false,
                    RateAveraging.Compound, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Disabled("JQuant does not yet provide StubIborCoupon, StubIndexSelection, "
            + "Bkbm1M/Bkbm3M or the NewZealand calendar required by the official "
            + "testOvernightIborStubIndexBootstrap")
    @Test
    void testOvernightIborStubIndexBootstrap() {
    }

    @Test
    void testOvernightIborAveragingAndTelescopicValueDates() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> curve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex overnightIndex = new Sofr(curve);
            IborIndex iborIndex = new USDLibor(new Period(3, TimeUnit.MONTHS), curve);
            Handle<Quote> basis = quote(0.0010);

            OvernightIborBasisSwapRateHelper fullSimpleHelper =
                    new OvernightIborBasisSwapRateHelper(basis,
                            new Period(2, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, null,
                            DateGenerationRule.BACKWARD, RateAveraging.Simple, false);
            OvernightIborBasisSwapRateHelper telescopicSimpleHelper =
                    new OvernightIborBasisSwapRateHelper(basis,
                            new Period(2, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, null,
                            DateGenerationRule.BACKWARD, RateAveraging.Simple, true);
            OvernightIborBasisSwapRateHelper fullCompoundedHelper =
                    new OvernightIborBasisSwapRateHelper(basis,
                            new Period(2, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, null,
                            DateGenerationRule.BACKWARD, RateAveraging.Compound, false);
            OvernightIborBasisSwapRateHelper telescopicCompoundedHelper =
                    new OvernightIborBasisSwapRateHelper(basis,
                            new Period(2, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, overnightIndex,
                            iborIndex, curve, false, 0, Frequency.ANNUAL, null,
                            DateGenerationRule.BACKWARD, RateAveraging.Compound, true);

            OvernightIndexedCoupon fullSimpleCoupon =
                    (OvernightIndexedCoupon) fullSimpleHelper.swap().leg(0).get(0);
            OvernightIndexedCoupon telescopicSimpleCoupon =
                    (OvernightIndexedCoupon) telescopicSimpleHelper.swap().leg(0).get(0);
            OvernightIndexedCoupon fullCompoundedCoupon =
                    (OvernightIndexedCoupon) fullCompoundedHelper.swap().leg(0).get(0);
            OvernightIndexedCoupon telescopicCompoundedCoupon =
                    (OvernightIndexedCoupon) telescopicCompoundedHelper.swap().leg(0).get(0);

            assertEquals(RateAveraging.Simple, fullSimpleCoupon.averagingMethod());
            assertEquals(RateAveraging.Compound, fullCompoundedCoupon.averagingMethod());
            assertEquals(fullSimpleCoupon.valueDates().size(),
                    telescopicSimpleCoupon.valueDates().size());
            assertTrue(telescopicCompoundedCoupon.valueDates().size()
                            < fullCompoundedCoupon.valueDates().size(),
                    "telescopic value dates should shorten the compounded coupon");

            OvernightIndexedCoupon lastBaseCoupon = (OvernightIndexedCoupon)
                    telescopicCompoundedHelper.swap().leg(0)
                            .get(telescopicCompoundedHelper.swap().leg(0).size() - 1);
            IborCoupon lastOtherCoupon = (IborCoupon)
                    telescopicCompoundedHelper.swap().leg(1)
                            .get(telescopicCompoundedHelper.swap().leg(1).size() - 1);

            Date lastBaseFixingEndDate = overnightIndex.maturityDate(
                    overnightIndex.valueDate(lastBaseCoupon.fixingDate()));
            Date lastPaymentDate = TimeUtils.max(
                    telescopicCompoundedHelper.swap().leg(0).get(
                            telescopicCompoundedHelper.swap().leg(0).size() - 1).date(),
                    telescopicCompoundedHelper.swap().leg(1).get(
                            telescopicCompoundedHelper.swap().leg(1).size() - 1).date());
            Date expectedLatestRelevantDate =
                    TimeUtils.max(telescopicCompoundedHelper.maturityDate(), lastPaymentDate);
            expectedLatestRelevantDate =
                    TimeUtils.max(expectedLatestRelevantDate, lastBaseFixingEndDate);
            expectedLatestRelevantDate =
                    TimeUtils.max(expectedLatestRelevantDate, lastOtherCoupon.fixingEndDate());

            assertTrue(TimeUtils.equals(
                    telescopicCompoundedHelper.latestRelevantDate(),
                    expectedLatestRelevantDate));

            testOvernightIborBootstrap(true, false, 0, false,
                    RateAveraging.Simple, true);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightOvernightOtherCurveBootstrap() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightOvernightBootstrap(false, false, false);
            testOvernightOvernightBootstrap(true, false, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightOvernightBaseCurveBootstrap() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightOvernightBootstrap(false, true, false);
            testOvernightOvernightBootstrap(true, true, false);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightOvernightBootstrapWithLateLinkedDiscountCurve() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            testOvernightOvernightBootstrap(true, false, true);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightOvernightDateGenerationRule() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> curve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex baseIndex = new FedFunds(curve);
            OvernightIndex otherIndex = new Sofr(curve);
            Handle<Quote> basis = quote(0.0010);

            OvernightOvernightBasisSwapRateHelper backwardHelper =
                    new OvernightOvernightBasisSwapRateHelper(
                            basis, new Period(18, TimeUnit.MONTHS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, baseIndex,
                            otherIndex, curve, false, 0, Frequency.ANNUAL,
                            RateAveraging.Compound, RateAveraging.Compound, false,
                            DateGenerationRule.BACKWARD);
            OvernightOvernightBasisSwapRateHelper defaultHelper =
                    new OvernightOvernightBasisSwapRateHelper(
                            basis, new Period(18, TimeUnit.MONTHS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, baseIndex,
                            otherIndex, curve);
            OvernightOvernightBasisSwapRateHelper forwardHelper =
                    new OvernightOvernightBasisSwapRateHelper(
                            basis, new Period(18, TimeUnit.MONTHS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, baseIndex,
                            otherIndex, curve, false, 0, Frequency.ANNUAL,
                            RateAveraging.Compound, RateAveraging.Compound, false,
                            DateGenerationRule.FORWARD);

            Date spot = calendar.advance(Settings.instance.evaluationDate().Date(),
                    2, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
            for (int i = 0; i < 2; ++i) {
                assertEquals(2, backwardHelper.swap().leg(i).size());
                assertEquals(2, defaultHelper.swap().leg(i).size());
                assertEquals(2, forwardHelper.swap().leg(i).size());
                assertTrue(TimeUtils.equals(
                        ((Coupon) backwardHelper.swap().leg(i).get(0)).accrualEndDate(),
                        calendar.adjust(spot.add(new Period(6, TimeUnit.MONTHS)),
                                BusinessDayConvention.FOLLOWING)));
                assertTrue(TimeUtils.equals(
                        ((Coupon) defaultHelper.swap().leg(i).get(0)).accrualEndDate(),
                        ((Coupon) backwardHelper.swap().leg(i).get(0)).accrualEndDate()));
                assertTrue(TimeUtils.equals(
                        ((Coupon) forwardHelper.swap().leg(i).get(0)).accrualEndDate(),
                        calendar.adjust(spot.add(new Period(1, TimeUnit.YEARS)),
                                BusinessDayConvention.FOLLOWING)));
            }

            Date expectedMaturity = calendar.adjust(
                    spot.add(new Period(18, TimeUnit.MONTHS)),
                    BusinessDayConvention.FOLLOWING);
            assertTrue(TimeUtils.equals(backwardHelper.maturityDate(), expectedMaturity));
            assertTrue(TimeUtils.equals(defaultHelper.maturityDate(), expectedMaturity));
            assertTrue(TimeUtils.equals(forwardHelper.maturityDate(), expectedMaturity));
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightOvernightNoFrequency() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> curve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex baseIndex = new FedFunds(curve);
            OvernightIndex otherIndex = new Sofr(curve);
            Handle<Quote> basis = quote(0.0010);

            OvernightOvernightBasisSwapRateHelper helper =
                    new OvernightOvernightBasisSwapRateHelper(
                            basis, new Period(5, TimeUnit.YEARS), 2, calendar,
                            BusinessDayConvention.FOLLOWING, false, baseIndex,
                            otherIndex, curve, false, 0, Frequency.NO_FREQUENCY);

            assertEquals(1, helper.swap().leg(0).size());
            assertEquals(1, helper.swap().leg(1).size());
            Coupon baseCoupon = (Coupon) helper.swap().leg(0).get(0);
            Coupon otherCoupon = (Coupon) helper.swap().leg(1).get(0);
            assertTrue(TimeUtils.equals(baseCoupon.accrualStartDate(),
                    helper.earliestDate()));
            assertTrue(TimeUtils.equals(otherCoupon.accrualStartDate(),
                    helper.earliestDate()));
            assertTrue(TimeUtils.equals(baseCoupon.accrualEndDate(),
                    helper.maturityDate()));
            assertTrue(TimeUtils.equals(otherCoupon.accrualEndDate(),
                    helper.maturityDate()));
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightOvernightTelescopicValueDatesWithSimpleAveraging() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        try {
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            Handle<YieldTermStructure> forecastCurve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            OvernightIndex baseIndex = new FedFunds(forecastCurve);
            OvernightIndex otherIndex = new Sofr();

            java.util.function.Function<Boolean, OvernightOvernightBasisSwapRateHelper> makeHelper =
                    telescopicValueDates -> new OvernightOvernightBasisSwapRateHelper(
                            quote(0.0010), new Period(1, TimeUnit.YEARS), 2,
                            calendar, BusinessDayConvention.FOLLOWING, false,
                            baseIndex, otherIndex, new Handle<>(null, false),
                            false, 0, Frequency.ANNUAL, RateAveraging.Simple,
                            RateAveraging.Compound, telescopicValueDates);

            OvernightOvernightBasisSwapRateHelper fullScheduleHelper = makeHelper.apply(false);
            OvernightOvernightBasisSwapRateHelper telescopicHelper = makeHelper.apply(true);

            OvernightIndexedCoupon fullSimpleCoupon = (OvernightIndexedCoupon)
                    fullScheduleHelper.swap().leg(0).get(0);
            OvernightIndexedCoupon telescopicSimpleCoupon = (OvernightIndexedCoupon)
                    telescopicHelper.swap().leg(0).get(0);
            OvernightIndexedCoupon fullCompoundedCoupon = (OvernightIndexedCoupon)
                    fullScheduleHelper.swap().leg(1).get(0);
            OvernightIndexedCoupon telescopicCompoundedCoupon = (OvernightIndexedCoupon)
                    telescopicHelper.swap().leg(1).get(0);

            assertEquals(fullSimpleCoupon.valueDates().size(),
                    telescopicSimpleCoupon.valueDates().size());
            assertTrue(telescopicCompoundedCoupon.valueDates().size()
                    < fullCompoundedCoupon.valueDates().size());
            assertEquals(telescopicSimpleCoupon.rate(), fullSimpleCoupon.rate(), 1.0e-14);
        } finally {
            restoreHistories(histories);
        }
    }

    @Test
    void testOvernightIborMarginOnIborLeg() {
        Map<String, TimeSeries<Double>> histories = snapshotHistories();
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Settings.instance.evaluationDate().equal(new Date(27, Month.JANUARY, 2010));
        try {
            BasisSwapQuote[] quotes = {
                    new BasisSwapQuote(1, TimeUnit.YEARS, -0.0010),
                    new BasisSwapQuote(2, TimeUnit.YEARS, -0.0012),
                    new BasisSwapQuote(5, TimeUnit.YEARS, -0.0015),
                    new BasisSwapQuote(10, TimeUnit.YEARS, -0.0020),
            };

            int settlementDays = 2;
            Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
            BusinessDayConvention convention = BusinessDayConvention.FOLLOWING;
            boolean endOfMonth = false;

            Handle<YieldTermStructure> knownForecastCurve =
                    new Handle<>(flatRate(0.01, new Actual365Fixed()), false);
            Handle<YieldTermStructure> discountCurve =
                    new Handle<>(flatRate(0.005, new Actual365Fixed()), false);

            OvernightIndex baseIndex = new Sofr(knownForecastCurve);
            IborIndex otherIndex = new USDLibor(new Period(6, TimeUnit.MONTHS));

            List<RateHelper> helpers = new ArrayList<>();
            for (BasisSwapQuote q : quotes) {
                helpers.add(new OvernightIborBasisSwapRateHelper(
                        quote(q.basis), new Period(q.n, q.units), settlementDays,
                        calendar, convention, endOfMonth, baseIndex, otherIndex,
                        discountCurve, false, 0, null, null,
                        DateGenerationRule.BACKWARD, RateAveraging.Compound, false,
                        true));
            }

            PiecewiseYieldCurve bootstrappedCurve = new PiecewiseYieldCurve(
                    referenceDate(calendar), helpers, new Actual365Fixed(),
                    BootstrapTraits.ZeroYield, new Linear());

            Date today = Settings.instance.evaluationDate().Date();
            Date spot = calendar.advance(today, settlementDays, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);

            otherIndex = new USDLibor(new Period(6, TimeUnit.MONTHS),
                    new Handle<>(bootstrappedCurve, false));

            for (BasisSwapQuote q : quotes) {
                Date maturity = spot.add(new Period(q.n, q.units));

                Schedule s = new MakeSchedule()
                        .from(spot)
                        .to(maturity)
                        .withTenor(otherIndex.tenor())
                        .withCalendar(calendar)
                        .withConvention(convention)
                        .withRule(DateGenerationRule.BACKWARD)
                        .toSchedule();

                List<jquant.CashFlow> leg1 = new OvernightLeg(s, baseIndex)
                        .withNotionals(100.0)
                        .build();
                List<jquant.CashFlow> leg2 = new IborLeg(s, otherIndex)
                        .withSpreads(q.basis)
                        .withNotionals(100.0)
                        .leg();

                Swap swap = new Swap(leg1, leg2);
                swap.setPricingEngine(new DiscountingMultiLegSwapEngine(discountCurve));

                double npv = swap.NPV();
                assertEquals(0.0, npv, 1e-8,
                        "Failed to price fair " + q.n + "-year(s) swap: " + npv);
            }
        } finally {
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
            restoreHistories(histories);
        }
    }
}
