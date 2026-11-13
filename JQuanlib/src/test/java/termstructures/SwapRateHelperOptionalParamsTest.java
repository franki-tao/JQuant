package termstructures;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.TimeSeries;
import jquant.cashflows.BlackIborCouponPricer;
import jquant.cashflows.CashflowUtil;
import jquant.cashflows.FloatingRateCoupon;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.USDLibor;
import jquant.instruments.VanillaSwap;
import jquant.math.Rounding;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.LogLinear;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.Mexico;
import jquant.time.calendars.Target;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official tests for the optional {@code SwapRateHelper} constructor arguments
 * (test-suite/piecewiseyieldcurve.cpp).
 *
 * <p>Dates, quotes, fixings and tolerances are copied from QuantLib. The only
 * test that is not reproduced statement-for-statement is
 * {@code testSwapRateHelperSpotDate}: JQuant's {@link RateHelper} has no
 * relative-date observer, so the evaluation date has to be set before the
 * helper is built (the official test builds the helper first, then moves the
 * evaluation date). The expected spot date and the assertion are unchanged.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 */
public class SwapRateHelperOptionalParamsTest {

    @Test
    public void testSwapRateHelperLastRelevantDate() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            Date today = new Date(22, Month.DECEMBER, 2016);
            Settings.instance.evaluationDate().equal(today);

            Handle<YieldTermStructure> flat3m = new Handle<>(
                    new FlatForward(today,
                            new Handle<>(new SimpleQuote(0.02), true),
                            new Actual365Fixed(), Compounding.Continuous,
                            Frequency.ANNUAL), true);
            IborIndex usdLibor3m = new USDLibor(new Period(3, TimeUnit.MONTHS), flat3m);

            // The calendar should be US+UK here, but the US calendar alone
            // also works for this regression.
            SwapRateHelper helper = new SwapRateHelper(
                    new Handle<>(new SimpleQuote(0.02), true),
                    new Period(50, TimeUnit.YEARS),
                    new UnitedStates(UnitedStates.Market.GovernmentBond),
                    Frequency.SEMIANNUAL, BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.BondBasis), usdLibor3m);

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    today, new ArrayList<RateHelper>(List.of(helper)),
                    new Actual365Fixed());

            assertDoesNotThrow(() -> curve.discount(1.0, false));
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    @Test
    public void testSwapRateHelperSpotDate() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            // Deviation: the helper computes its dates at construction time,
            // so the evaluation date is set before building it.
            IborIndex usdLibor3m = new USDLibor(new Period(3, TimeUnit.MONTHS));

            Settings.instance.evaluationDate().equal(new Date(11, Month.OCTOBER, 2019));

            SwapRateHelper helper = new SwapRateHelper(
                    new Handle<>(new SimpleQuote(0.02), true),
                    new Period(5, TimeUnit.YEARS),
                    new UnitedStates(UnitedStates.Market.GovernmentBond),
                    Frequency.SEMIANNUAL, BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.BondBasis), usdLibor3m);

            // Advancing 2 days on the US calendar would yield October 16th
            // (because October 14th is Columbus day), but the LIBOR spot is
            // calculated advancing on the UK calendar, resulting in October
            // 15th, which is also a business day for the US calendar.
            Date expected = new Date(15, Month.OCTOBER, 2019);
            Date calculated = helper.swap().startDate();
            assertTrue(jquant.time.TimeUtils.equals(expected, calculated),
                    "expected spot date: " + expected + ", calculated: " + calculated);
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    @Test
    public void testDatedSwapHelpers() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            Date today = new Date(28, Month.OCTOBER, 2024);
            Settings.instance.evaluationDate().equal(today);

            Date[] starts = {
                    new Date(1, Month.NOVEMBER, 2024),
                    new Date(15, Month.OCTOBER, 2024),
                    new Date(28, Month.OCTOBER, 2024),
                    new Date(4, Month.NOVEMBER, 2024),
                    new Date(11, Month.OCTOBER, 2024)
            };
            Date[] ends = {
                    new Date(1, Month.NOVEMBER, 2025),
                    new Date(15, Month.OCTOBER, 2026),
                    new Date(1, Month.NOVEMBER, 2029),
                    new Date(4, Month.NOVEMBER, 2034),
                    new Date(11, Month.OCTOBER, 2044)
            };
            double[] quotes = {4.54, 4.63, 4.99, 5.47, 5.89};

            IborIndex euribor6m = euribor6m(null);
            euribor6m.addFixing(new Date(9, Month.OCTOBER, 2024), 0.0447, false);
            euribor6m.addFixing(new Date(11, Month.OCTOBER, 2024), 0.045, false);
            euribor6m.addFixing(new Date(24, Month.OCTOBER, 2024), 0.0442, false);

            Target calendar = new Target();
            Frequency fixedLegFrequency = Frequency.ANNUAL;
            BusinessDayConvention fixedLegConvention = BusinessDayConvention.UNADJUSTED;
            Thirty360 fixedLegDayCounter = new Thirty360(Thirty360.Convention.BondBasis);

            List<RateHelper> helpers = new ArrayList<>();
            for (int i = 0; i < quotes.length; i++) {
                helpers.add(new SwapRateHelper(
                        new Handle<>(new SimpleQuote(quotes[i] / 100.0), false),
                        starts[i], ends[i], calendar, fixedLegFrequency,
                        fixedLegConvention, fixedLegDayCounter, euribor6m));
            }

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    today, helpers, new Actual365Fixed(),
                    BootstrapTraits.ZeroYield, new Linear());
            IborIndex pricingIndex = euribor6m(new Handle<>(curve, false));

            double tolerance = 1.0e-9;
            for (int i = 0; i < quotes.length; i++) {
                // Mirrors MakeVanillaSwap with an explicit effective date and
                // termination date, annual Unadjusted fixed leg and the
                // index's floating conventions.
                Schedule fixed = new Schedule(starts[i], ends[i],
                        new Period(fixedLegFrequency), calendar,
                        fixedLegConvention, fixedLegConvention,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                Schedule floating = new Schedule(starts[i], ends[i],
                        pricingIndex.tenor(), calendar,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, 1.0,
                        fixed, 0.0, fixedLegDayCounter, floating, pricingIndex, 0.0,
                        new Actual360(false));
                swap.setPricingEngine(new DiscountingSwapEngine(
                        new Handle<>(curve, false)));

                double expectedRate = quotes[i] / 100.0;
                assertEquals(expectedRate, swap.fairRate(), tolerance,
                        "swap from " + starts[i] + " to " + ends[i]);
            }
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    @Test
    public void testSwapHelpersWithOnceFrequency() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            IborIndex index = new IborIndex("TestIndex",
                    new Period(4, TimeUnit.WEEKS), 1, mxn(), new Mexico(),
                    BusinessDayConvention.FOLLOWING, false, new Actual360(false), null);
            Handle<Quote> r = new Handle<>(new SimpleQuote(0.02), false);

            // Only the SwapRateHelper part of the official test is ported;
            // OISRateHelper is not part of this step.
            assertDoesNotThrow(() -> new SwapRateHelper(r,
                    new Period(4, TimeUnit.WEEKS), new Mexico(), Frequency.ONCE,
                    BusinessDayConvention.FOLLOWING, new Actual360(false), index));
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    @Test
    public void testSwapRateHelperWithCouponPricer() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            Date today = new Date(15, Month.JUNE, 2020);
            Settings.instance.evaluationDate().equal(today);

            int[] tenors = {2, 5, 10, 20};
            double[] rates = {0.015, 0.020, 0.025, 0.030};

            Handle<OptionletVolatilityStructure> vol = new Handle<>(
                    new ConstantOptionletVolatility(today, new Target(),
                            BusinessDayConvention.FOLLOWING, 0.20,
                            new Actual365Fixed()), true);
            BlackIborCouponPricer pricer = new BlackIborCouponPricer(vol);

            RelinkableHandle<YieldTermStructure> curveHandle = new RelinkableHandle<>();
            IborIndex index = euribor6m(curveHandle);

            List<SwapRateHelper> helpers = new ArrayList<>();
            for (int i = 0; i < tenors.length; i++) {
                helpers.add(new SwapRateHelper(
                        new Handle<>(new SimpleQuote(rates[i]), false),
                        new Period(tenors[i], TimeUnit.YEARS), new Target(),
                        Frequency.ANNUAL, BusinessDayConvention.MODIFIED_FOLLOWING,
                        new Thirty360(Thirty360.Convention.BondBasis), index,
                        null, new Period(0, TimeUnit.DAYS), null,
                        null, SwapRateHelper.Pillar.LastRelevantDate,
                        new Date(), false, Optional.empty(), Optional.empty(), pricer));
            }

            // The supplied pricer must be attached to every floating coupon
            // of the swap the helper builds internally.
            for (SwapRateHelper helper : helpers) {
                for (CashFlow cashflow : helper.swap().floatingLeg()) {
                    assertTrue(cashflow instanceof FloatingRateCoupon,
                            "floating leg cashflow is not a floating-rate coupon");
                    assertTrue(((FloatingRateCoupon) cashflow).pricer() == pricer,
                            "the coupon pricer was not attached to the floating "
                                    + "leg of the rate helper's internal swap");
                }
            }

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    today, new ArrayList<RateHelper>(helpers),
                    new Actual365Fixed(), BootstrapTraits.Discount,
                    new LogLinear());
            curveHandle.linkTo(curve, false);

            double tolerance = 1.0e-6;
            for (int i = 0; i < tenors.length; i++) {
                // Mirrors MakeVanillaSwap for the helper's internal swap.
                Target calendar = new Target();
                Date start = calendar.adjust(today, BusinessDayConvention.FOLLOWING);
                start = index.valueDate(start);
                Date end = start.add(new Period(tenors[i], TimeUnit.YEARS));

                Schedule fixed = new Schedule(start, end,
                        new Period(Frequency.ANNUAL), calendar,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                Schedule floating = new Schedule(start, end,
                        index.tenor(), calendar,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, 1.0,
                        fixed, rates[i],
                        new Thirty360(Thirty360.Convention.BondBasis), floating,
                        index, 0.0, new Actual360(false));
                CashflowUtil.setCouponPricer(swap.floatingLeg(), pricer);
                swap.setPricingEngine(new DiscountingSwapEngine(curveHandle));

                assertEquals(0.0, swap.NPV(), tolerance,
                        "swap priced with the bootstrapping coupon pricer is "
                                + "not at par: tenor " + tenors[i] + "Y");
            }
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    /** Official {@code QL_REQUIRE} checks for the custom pillar date. */
    @Test
    public void testCustomPillarDateValidation() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            Settings.instance.evaluationDate().equal(new Date(15, Month.JUNE, 2020));
            IborIndex index = euribor6m(null);
            Handle<Quote> quote = new Handle<>(new SimpleQuote(0.02), false);

            assertThrows(IllegalArgumentException.class, () -> new SwapRateHelper(
                    quote, new Period(5, TimeUnit.YEARS), new Target(),
                    Frequency.ANNUAL, BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.BondBasis), index,
                    null, new Period(0, TimeUnit.DAYS), null, null,
                    SwapRateHelper.Pillar.CustomDate,
                    new Date(16, Month.JUNE, 2020), false,
                    Optional.empty(), Optional.empty(), null));

            assertThrows(IllegalArgumentException.class, () -> new SwapRateHelper(
                    quote, new Period(5, TimeUnit.YEARS), new Target(),
                    Frequency.ANNUAL, BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.BondBasis), index,
                    null, new Period(0, TimeUnit.DAYS), null, null,
                    SwapRateHelper.Pillar.CustomDate,
                    new Date(15, Month.JUNE, 2030), false,
                    Optional.empty(), Optional.empty(), null));
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    /** Official {@code QL_REQUIRE} check for a dated helper with Once. */
    @Test
    public void testDatedSwapHelperRejectsOnceFrequency() {
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        Map<String, TimeSeries<Double>> savedHistories = snapshotHistories();
        try {
            IborIndex index = euribor6m(null);
            assertThrows(IllegalArgumentException.class, () -> new SwapRateHelper(
                    new Handle<>(new SimpleQuote(0.02), false),
                    new Date(15, Month.JUNE, 2020),
                    new Date(15, Month.JUNE, 2021),
                    new Target(), Frequency.ONCE,
                    BusinessDayConvention.FOLLOWING, new Actual360(false), index));
        } finally {
            restoreHistories(savedHistories);
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
        }
    }

    private static IborIndex euribor6m(Handle<YieldTermStructure> handle) {
        return new IborIndex("Euribor", new Period(6, TimeUnit.MONTHS), 2, eur(),
                new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, false,
                new Actual360(false), handle);
    }

    private static Currency eur() {
        return new Currency("European Euro", "EUR", 978, "", "", 100,
                new Rounding(), new Currency(), Set.of());
    }

    private static Currency mxn() {
        return new Currency("Mexican peso", "MXN", 484, "Mex$", "", 100,
                new Rounding(), new Currency(), Set.of());
    }

    private static Map<String, TimeSeries<Double>> snapshotHistories() {
        Map<String, TimeSeries<Double>> snapshot = new HashMap<>();
        for (String name : IndexManager.Instance.histories()) {
            TimeSeries<Double> history = IndexManager.Instance.getHistory(name);
            snapshot.put(name, new TimeSeries<>(history.dates(), history.values()));
        }
        return snapshot;
    }

    private static void restoreHistories(Map<String, TimeSeries<Double>> snapshot) {
        IndexManager.Instance.clearHistories();
        for (Map.Entry<String, TimeSeries<Double>> entry : snapshot.entrySet()) {
            IndexManager.Instance.setHistory(entry.getKey(), entry.getValue());
        }
    }
}
