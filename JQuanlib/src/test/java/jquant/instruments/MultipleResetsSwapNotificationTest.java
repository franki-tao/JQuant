package jquant.instruments;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.Euribor3M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering regression tests for the notification chain of
 * {@link MultipleResetsSwapRateHelper} (not ports of official BOOST cases).
 *
 * <p>Official semantics: a relative-date bootstrap helper is an observer of
 * the evaluation date, rebuilds its dates on a change and notifies the curve
 * using it; the curve re-bootstraps on the next access and tells its own
 * observers. These tests assert that a fixed-reference piecewise curve and an
 * already-built swap depending on it update without reconstruction, manual
 * {@code recalculate()} or reading the helper first.
 *
 * <p>The market data is the official {@code CommonVars} from
 * {@code test-suite/multipleresetsswap.cpp} (15 Jan 2024, TARGET,
 * Actual365Fixed, Euribor3M, flat 5%). Where official fixes no expected
 * value, the existing objects are compared with freshly built equivalents.
 */
public class MultipleResetsSwapNotificationTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        // Same isolation as the official fixture used by MultipleResetsSwapTest.
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private static final double INPUT_RATE = 0.05;

    private static final Period[] TENORS = {
            new Period(1, TimeUnit.YEARS),
            new Period(2, TimeUnit.YEARS),
            new Period(3, TimeUnit.YEARS)
    };

    /** Official {@code CommonVars} data from test-suite/multipleresetsswap.cpp. */
    private static class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();
        final IborIndex euribor3m;

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
    }

    /**
     * Official {@code testRateHelper} market: 1Y/2Y/3Y multiple-resets swap
     * helpers on a fixed-reference piecewise curve, plus a relinkable handle
     * and an index on that curve.
     */
    private static final class Market {
        final List<SimpleQuote> quotes = new ArrayList<>();
        final List<RateHelper> helpers;
        /** Same helpers with their concrete type, for {@code swap()} access. */
        final List<MultipleResetsSwapRateHelper> helperImpls;
        final PiecewiseYieldCurve curve;
        final RelinkableHandle<YieldTermStructure> bootstrapped =
                new RelinkableHandle<>();
        final Euribor3M indexOnCurve;

        Market(CommonVars vars, double... rates) {
            for (int i = 0; i < TENORS.length; i++)
                quotes.add(new SimpleQuote(rates[i]));
            List<MultipleResetsSwapRateHelper> built = new ArrayList<>();
            for (int i = 0; i < TENORS.length; i++)
                built.add(new MultipleResetsSwapRateHelper(
                        0, TENORS[i],
                        new Handle<Quote>(quotes.get(i), true),
                        vars.euribor3m, 2));
            helperImpls = List.copyOf(built);
            helpers = new ArrayList<>(built);
            curve = new PiecewiseYieldCurve(vars.today, helpers, vars.dayCount);
            bootstrapped.linkTo(curve, true);
            indexOnCurve = new Euribor3M(bootstrapped);
        }

        MultipleResetsSwap checkSwap(Period tenor, Period forwardStart) {
            MakeMultipleResetsSwap maker =
                    new MakeMultipleResetsSwap(tenor, indexOnCurve, 2)
                            .withFixedRate(0.0)
                            .withSettlementDays(0)
                            .withNominal(1.0e6)
                            .withDiscountingTermStructure(bootstrapped);
            if (forwardStart != null)
                maker = maker.withForwardStart(forwardStart);
            return maker.makeSwap();
        }
    }

    /** Minimal engineering flag, as in the official test-observability case. */
    private static final class Flag implements Observer {
        private boolean up_ = false;
        private final Set<Observable> observables_ = new HashSet<>();

        public void registerWith(Observable observable) {
            observable.registerObserver(this);
            observables_.add(observable);
        }

        public void unregisterWith(Observable observable) {
            observable.unregisterObserver(this);
            observables_.remove(observable);
        }

        public void unregisterWithAll() {
            for (Observable observable : observables_)
                observable.unregisterObserver(this);
            observables_.clear();
        }

        public void update() {
            up_ = true;
        }

        boolean isUp() {
            return up_;
        }
    }

    @Test
    public void quoteChangeNotifiesCurveAndExistingSwap() {
        CommonVars vars = new CommonVars();
        Market market = new Market(vars, 0.05, 0.05, 0.05);
        MultipleResetsSwap check = market.checkSwap(TENORS[1], null);

        double baseFairRate = check.fairRate();
        double baseNpv = check.NPV();
        // Sanity: a flat 5% input bootstraps to a flat 5% output.
        assertEquals(INPUT_RATE, baseFairRate, 1.0e-6);

        double t1 = vars.dayCount.yearFraction(vars.today,
                market.helpers.get(0).pillarDate());
        double baseDiscount = market.curve.discount(t1);

        Flag flag = new Flag();
        flag.registerWith(market.curve);

        market.quotes.get(0).setValue(INPUT_RATE * 1.01);

        assertTrue(flag.isUp(),
                "observer of the curve was not notified of a quote change");
        assertFalse(check.isCalculated(),
                "quote change must invalidate the existing swap");

        double changedDiscount = market.curve.discount(t1);
        assertTrue(Math.abs(changedDiscount - baseDiscount) > 1.0e-10,
                "quote change did not trigger a curve recalculation");

        double changedFairRate = check.fairRate();
        double changedNpv = check.NPV();
        // A check swap is pinned to its own pillar quote by the bootstrap, so
        // its fair rate stays at the (unchanged) 2Y input. The price evidence
        // that the 1Y pillar moved is the NPV: a zero-fixed-rate swap is worth
        // the floating leg, whose fixed-leg annuity depends on the 1Y discount.
        assertTrue(Math.abs(changedNpv - baseNpv) > 1.0e-3,
                "existing swap was not repriced after the quote change");

        // Fresh comparison with the changed quote.
        Market fresh = new Market(vars, INPUT_RATE * 1.01, INPUT_RATE, INPUT_RATE);
        MultipleResetsSwap freshCheck = fresh.checkSwap(TENORS[1], null);
        assertEquals(fresh.curve.discount(t1), changedDiscount, 1.0e-12);
        // Official testRateHelper absolute tolerance.
        assertEquals(freshCheck.fairRate(), changedFairRate, 1.0e-6);
        assertEquals(freshCheck.NPV(), changedNpv, 1.0e-3);

        // Restoring the quote restores the original prices.
        market.quotes.get(0).setValue(INPUT_RATE);
        assertEquals(baseDiscount, market.curve.discount(t1), 1.0e-12);
        assertEquals(baseFairRate, check.fairRate(), 1.0e-10);
        assertEquals(baseNpv, check.NPV(), 1.0e-3);
    }

    @Test
    public void evaluationDateAdvanceRebuildsCurveAndExistingSwap() {
        CommonVars vars = new CommonVars();
        Market market = new Market(vars, 0.05, 0.05, 0.05);

        Date newToday = vars.calendar.advance(vars.today, 1, TimeUnit.YEARS);

        // Helpers rebuilt at newToday start on newToday, so their first fixing
        // (newToday - 2 business days) is historical by then. Store the same
        // flat 5% fixing the official CommonVars stores for vars.today, so the
        // rebuilt curve and a fresh curve are both priceable. Engineering
        // scaffolding, not an official expected value.
        vars.euribor3m.addFixing(vars.euribor3m.fixingDate(newToday),
                INPUT_RATE, false);

        // Built before the move. It starts one year forward, i.e. on newToday,
        // so after the move it is a spot 2Y swap and can be compared with a
        // freshly built one.
        MultipleResetsSwap check = market.checkSwap(TENORS[1],
                new Period(1, TimeUnit.YEARS));
        double baseFairRate = check.fairRate();
        double baseNpv = check.NPV();
        assertEquals(INPUT_RATE, baseFairRate, 1.0e-6);

        MultipleResetsSwap helperSwapBefore = market.helperImpls.get(1).swap();
        Date oldMaxDate = market.curve.maxDate();

        Settings.instance.evaluationDate().equal(newToday);

        assertFalse(check.isCalculated(),
                "evaluation-date change must invalidate the existing swap "
                        + "without recalculate()");

        // First access after the move is the curve itself, before helper.swap()
        // is read: the pre-move curve does not reach this time.
        double tBeyondOldMax = 3.5;
        double existingDiscount = market.curve.discount(tBeyondOldMax);

        Market fresh = new Market(vars, 0.05, 0.05, 0.05);
        assertEquals(fresh.curve.discount(tBeyondOldMax), existingDiscount, 1.0e-12);

        assertTrue(TimeUtils.equals(fresh.curve.maxDate(), market.curve.maxDate()),
                "curve dates do not match a fresh curve at the new date");
        assertFalse(TimeUtils.equals(oldMaxDate, market.curve.maxDate()),
                "curve kept its pre-move pillar dates");
        List<Date> existingDates = market.curve.dates();
        List<Date> freshDates = fresh.curve.dates();
        assertEquals(freshDates.size(), existingDates.size());
        for (int i = 0; i < freshDates.size(); i++)
            assertTrue(TimeUtils.equals(freshDates.get(i), existingDates.get(i)),
                    "curve date " + i + " does not match a fresh curve");

        MultipleResetsSwap freshCheck = fresh.checkSwap(TENORS[1], null);
        double newFairRate = check.fairRate();
        double newNpv = check.NPV();
        assertEquals(INPUT_RATE, newFairRate, 1.0e-6);
        assertEquals(freshCheck.fairRate(), newFairRate, 1.0e-6);
        assertEquals(freshCheck.NPV(), newNpv, 1.0e-3);

        // Official testRateHelperSwap identity and start-date checks.
        MultipleResetsSwapRateHelper twoYear = market.helperImpls.get(1);
        assertNotSame(helperSwapBefore, twoYear.swap());
        assertTrue(TimeUtils.equals(twoYear.swap().startDate(), twoYear.earliestDate()));
    }
}
