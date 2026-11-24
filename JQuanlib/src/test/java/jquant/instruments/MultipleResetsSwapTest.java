package jquant.instruments;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.MultipleResetsCoupon;
import jquant.cashflows.RateAveraging;
import jquant.indexes.Euribor3M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.MultipleResetsSwapRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of the multiple-resets-swap cases from QuantLib
 * {@code test-suite/multipleresetsswap.cpp}.
 */
public class MultipleResetsSwapTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        // Official TopLevelFixture clears index histories before
        // SavedSettings restores the evaluation date and other settings.
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    /** Official {@code CommonVars} from test-suite/multipleresetsswap.cpp. */
    private static class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();
        final IborIndex euribor3m;

        // Flat 5% curve, 3M Euribor, 2 resets per semiannual coupon.
        CommonVars() {
            calendar = new Target();
            today = calendar.adjust(new Date(15, Month.JANUARY, 2024),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            dayCount = new Actual365Fixed();
            termStructure.linkTo(new FlatForward(today, 0.05, dayCount), true);
            euribor3m = new Euribor3M(termStructure);
            euribor3m.addFixing(new Date(11, Month.JANUARY, 2024), 0.05, false);
        }

        MultipleResetsSwap makeSwap(double fixedRate) {
            return makeSwap(fixedRate, RateAveraging.Compound);
        }

        MultipleResetsSwap makeSwap(double fixedRate, RateAveraging method) {
            return new MakeMultipleResetsSwap(new Period(2, TimeUnit.YEARS), euribor3m, 2)
                    .withFixedRate(fixedRate)
                    .withSettlementDays(0)
                    .withNominal(1.0e6)
                    .withAveragingMethod(method)
                    .makeSwap();
        }
    }

    @Test
    public void testFairRate() {
        CommonVars vars = new CommonVars();

        MultipleResetsSwap swap = vars.makeSwap(0.06);

        double fair = swap.fairRate();
        assertFalse(Double.isNaN(fair));

        // Rebuilding at the fair rate must give zero NPV.
        MultipleResetsSwap fairSwap = vars.makeSwap(fair);
        assertEquals(0.0, fairSwap.NPV(), 1.0e-8);

        // Cross-check: fixed-leg NPV + floating-leg NPV equals total NPV.
        double npvCheck = swap.fixedLegNPV() + swap.floatingLegNPV();
        assertEquals(0.0, npvCheck - swap.NPV(), 1.0e-10);

        // Omitting withFixedRate triggers auto-computation; NPV must be zero.
        MultipleResetsSwap autoFair =
                new MakeMultipleResetsSwap(new Period(2, TimeUnit.YEARS), vars.euribor3m, 2)
                        .withSettlementDays(0)
                        .withNominal(1.0e6)
                        .makeSwap();
        assertEquals(0.0, autoFair.NPV(), 1.0e-8);
    }

    @Test
    public void testConsistencyWithLeg() {
        CommonVars vars = new CommonVars();

        for (Swap.Type type : new Swap.Type[]{Swap.Type.Payer, Swap.Type.Receiver}) {
            MultipleResetsSwap swap =
                    new MakeMultipleResetsSwap(new Period(2, TimeUnit.YEARS), vars.euribor3m, 2)
                            .withFixedRate(0.05)
                            .withSettlementDays(0)
                            .withNominal(1.0e6)
                            .withType(type)
                            .makeSwap();

            double legSum = swap.fixedLegNPV() + swap.floatingLegNPV();
            assertEquals(0.0, legSum - swap.NPV(), 1.0e-10);
        }
    }

    @Test
    public void testAveragingVsCompounding() {
        CommonVars vars = new CommonVars();

        double fixedRate = 0.05;
        MultipleResetsSwap swapCompound = vars.makeSwap(fixedRate, RateAveraging.Compound);
        MultipleResetsSwap swapAverage = vars.makeSwap(fixedRate, RateAveraging.Simple);

        assertTrue(Math.abs(swapCompound.fairRate() - swapAverage.fairRate()) > 1.0e-10);
    }

    @Test
    public void testMaturityOnHoliday() {
        CommonVars vars = new CommonVars();

        // Four years from January 15th, 2024 is Saturday, January 15th, 2028.
        // The schedules must still start on the start date, without a stub.
        MultipleResetsSwap swap =
                new MakeMultipleResetsSwap(new Period(4, TimeUnit.YEARS), vars.euribor3m, 2)
                        .withFixedRate(0.05)
                        .withSettlementDays(0)
                        .makeSwap();

        assertEquals(8, swap.floatingLeg().size());
        assertEquals(8, swap.fixedLeg().size());
        assertTrue(TimeUtils.equals(swap.fixedSchedule().startDate(), vars.today));
        assertTrue(TimeUtils.equals(swap.floatingSchedule().startDate(), vars.today));
        assertTrue(TimeUtils.equals(swap.maturityDate(),
                new Date(17, Month.JANUARY, 2028)));

        // With one reset per coupon no error is raised, so a stub would go unnoticed
        MultipleResetsSwap single =
                new MakeMultipleResetsSwap(new Period(4, TimeUnit.YEARS), vars.euribor3m, 1)
                        .withFixedRate(0.05)
                        .withSettlementDays(0)
                        .makeSwap();

        assertEquals(16, single.floatingLeg().size());
        assertEquals(16, single.fixedLeg().size());
        assertTrue(TimeUtils.equals(single.fixedSchedule().startDate(), vars.today));
        assertTrue(TimeUtils.equals(single.fixedSchedule().at(1),
                new Date(15, Month.APRIL, 2024)));
    }

    @Test
    public void testPaymentLagPreservedOnConversion() {
        CommonVars vars = new CommonVars();

        MultipleResetsSwap baseSwap = vars.makeSwap(0.05);
        int paymentLag = 3;
        MultipleResetsSwap laggedSwap = new MultipleResetsSwap(
                Swap.Type.Payer, 1.0e6, baseSwap.fixedSchedule(), 0.05,
                baseSwap.fixedDayCount(), baseSwap.fullResetSchedule(),
                vars.euribor3m, baseSwap.resetsPerCoupon(), 0.0,
                RateAveraging.Compound, Optional.empty(), paymentLag, vars.calendar);

        NonstandardSwap converted = new NonstandardSwap(laggedSwap);

        assertEquals(paymentLag, converted.paymentLag());

        // Official Calendar::operator==: both empty or same name.
        Calendar convertedCalendar = converted.paymentCalendar();
        boolean sameCalendar =
                (convertedCalendar == null || convertedCalendar.empty())
                        ? (vars.calendar == null || vars.calendar.empty())
                        : (vars.calendar != null && !vars.calendar.empty()
                                && convertedCalendar.name().equals(vars.calendar.name()));
        assertTrue(sameCalendar);

        assertEquals(laggedSwap.fixedLeg().size(), converted.fixedLeg().size());
        assertEquals(laggedSwap.floatingLeg().size(), converted.floatingLeg().size());
        for (int i = 0; i < converted.fixedLeg().size(); ++i)
            assertTrue(TimeUtils.equals(converted.fixedLeg().get(i).date(),
                    laggedSwap.fixedLeg().get(i).date()));
        for (int i = 0; i < converted.floatingLeg().size(); ++i)
            assertTrue(TimeUtils.equals(converted.floatingLeg().get(i).date(),
                    laggedSwap.floatingLeg().get(i).date()));
    }

    @Test
    public void testRateHelperSwap() {
        CommonVars vars = new CommonVars();

        MultipleResetsSwapRateHelper helper = new MultipleResetsSwapRateHelper(
                0, new Period(2, TimeUnit.YEARS),
                new Handle<Quote>(new SimpleQuote(0.05), true),
                vars.euribor3m, 2);

        MultipleResetsSwap swap = helper.swap();
        assertNotNull(swap);
        assertTrue(TimeUtils.equals(swap.startDate(), helper.earliestDate()));
        assertEquals(4, swap.floatingLeg().size());

        Settings.instance.evaluationDate().equal(
                vars.calendar.advance(vars.today, 1, TimeUnit.YEARS));

        assertNotSame(swap, helper.swap());
        assertTrue(TimeUtils.equals(helper.swap().startDate(), helper.earliestDate()));
    }

    @Test
    public void testRateHelper() {
        CommonVars vars = new CommonVars();

        // Build a flat curve from multiple-resets swap quotes at 1Y, 2Y, 3Y.
        // A flat 5% input should bootstrap to a flat 5% output.
        double inputRate = 0.05;
        Period[] tenors = {
                new Period(1, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS),
                new Period(3, TimeUnit.YEARS)
        };
        List<RateHelper> helpers = new ArrayList<>();
        for (Period tenor : tenors) {
            helpers.add(new MultipleResetsSwapRateHelper(
                    0, tenor,
                    new Handle<Quote>(new SimpleQuote(inputRate), true),
                    vars.euribor3m, 2));
        }

        PiecewiseYieldCurve curve =
                new PiecewiseYieldCurve(vars.today, helpers, vars.dayCount);

        RelinkableHandle<YieldTermStructure> bootstrapped = new RelinkableHandle<>();
        bootstrapped.linkTo(curve, true);
        Euribor3M indexOnCurve = new Euribor3M(bootstrapped);

        double tolerance = 1.0e-6;
        for (Period tenor : tenors) {
            MultipleResetsSwap check =
                    new MakeMultipleResetsSwap(tenor, indexOnCurve, 2)
                            .withFixedRate(0.0)
                            .withSettlementDays(0)
                            .withNominal(1.0e6)
                            .withDiscountingTermStructure(bootstrapped)
                            .makeSwap();
            double implied = check.fairRate();
            assertEquals(inputRate, implied, tolerance);
        }
    }

    @Test
    public void testRateHelperLastRelevantDate() {
        // The last reset starts on Monday, October 10th, 2016, since the 8th
        // is a Saturday, so its fixing forecasts Euribor until January 10th,
        // 2017; that is one day after the end of the swap. The curve must
        // reach that date, or the bootstrap fails since the helper's pillar
        // is too early.
        Date today = new Date(6, Month.JANUARY, 2016);
        Settings.instance.evaluationDate().equal(today);

        Euribor3M euribor3m = new Euribor3M();
        MultipleResetsSwapRateHelper helper = new MultipleResetsSwapRateHelper(
                2, new Period(1, TimeUnit.YEARS), 0.02, euribor3m, 2);

        List<CashFlow> floatingLeg = helper.swap().floatingLeg();
        CashFlow lastCashFlow = floatingLeg.get(floatingLeg.size() - 1);
        assertTrue(lastCashFlow instanceof MultipleResetsCoupon);
        MultipleResetsCoupon lastCoupon = (MultipleResetsCoupon) lastCashFlow;
        List<Date> fixingDates = lastCoupon.fixingDates();
        Date lastFixingEndDate = euribor3m.maturityDate(
                euribor3m.valueDate(fixingDates.get(fixingDates.size() - 1)));
        assertFalse(TimeUtils.less(helper.latestRelevantDate(), lastFixingEndDate),
                "latest relevant date (" + helper.latestRelevantDate()
                        + ") is earlier than the end of the last fixing period ("
                        + lastFixingEndDate + ")");
        assertTrue(TimeUtils.equals(helper.maturityDate(),
                helper.swap().maturityDate()));

        PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                today, List.<RateHelper>of(helper), new Actual365Fixed());
        curve.discount(1.0);
    }
}
