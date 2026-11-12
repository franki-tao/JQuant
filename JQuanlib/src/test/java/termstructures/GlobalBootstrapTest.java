package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.indexes.IborIndex;
import jquant.instruments.Futures;
import jquant.math.Array;
import jquant.math.interpolations.impl.BackwardFlat;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.LogLinear;
import jquant.quotes.FuturesConvAdjustmentQuote;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.FraRateHelper;
import jquant.termstructures.yield.FuturesRateHelper;
import jquant.termstructures.yield.GlobalBootstrap;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SimpleQuoteVariables;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.IMM;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code testGlobalBootstrap} and {@code testGlobalBootstrapPenalty}
 * (test-suite/piecewiseyieldcurve.cpp, lines 1311 and 1393). Market rates,
 * expected pillars and expected zero rates are copied verbatim from the
 * official file; the tolerance is the official 1E-6 (0.01 basis points).
 *
 * <p>The official tests are preconditioned on {@code usingAtParCoupons()}, so
 * this test switches {@link IborCoupon.Settings} to at-par coupons and restores
 * the previous setting afterwards.
 *
 * <p>JQuant has no settlement-days {@code PiecewiseYieldCurve} constructor, so
 * the official {@code PiecewiseYieldCurve(2, TARGET(), ...)} reference date is
 * reproduced explicitly as {@code TARGET.advance(today, 2, Days)}.
 *
 * <p>The remaining official GlobalBootstrap tests are ported here as well:
 * {@code testGlobalBootstrapVariables} (convexity-adjusted IMM futures and a
 * {@code SimpleQuoteVariables} volatility variable), {@code
 * testGlobalBootstrapInitialGuessFn} (exact official node dates and discount
 * factors, tolerance 1E-10) and {@code testGlobalBootstrapInstrumentWeights}
 * (weighted overdetermined deposits, tolerance 1E-13). Multi-curve joint
 * bootstrap is not ported.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/globalbootstrap.hpp
 */
public class GlobalBootstrapTest {
    /** Official test tolerance: 0.01 basis points. */
    private static final double TOLERANCE = 1.0e-6;

    private static final double[] REF_MKT_RATE = {
            -0.373, -0.388, -0.402, -0.418, -0.431, -0.441, -0.45,
            -0.457, -0.463, -0.469, -0.461, -0.463, -0.479, -0.4511,
            -0.45418, -0.439, -0.4124, -0.37703, -0.3335, -0.28168, -0.22725,
            -0.1745, -0.12425, -0.07746, 0.0385, 0.1435, 0.17525, 0.17275,
            0.1515, 0.1225, 0.095, 0.0644
    };

    private static final Date[] REF_DATE = {
            new Date(31, Month.MARCH, 2020), new Date(30, Month.APRIL, 2020),
            new Date(29, Month.MAY, 2020), new Date(30, Month.JUNE, 2020),
            new Date(31, Month.JULY, 2020), new Date(31, Month.AUGUST, 2020),
            new Date(30, Month.SEPTEMBER, 2020), new Date(30, Month.OCTOBER, 2020),
            new Date(30, Month.NOVEMBER, 2020), new Date(31, Month.DECEMBER, 2020),
            new Date(29, Month.JANUARY, 2021), new Date(26, Month.FEBRUARY, 2021),
            new Date(31, Month.MARCH, 2021), new Date(30, Month.SEPTEMBER, 2021),
            new Date(30, Month.SEPTEMBER, 2022), new Date(29, Month.SEPTEMBER, 2023),
            new Date(30, Month.SEPTEMBER, 2024), new Date(30, Month.SEPTEMBER, 2025),
            new Date(30, Month.SEPTEMBER, 2026), new Date(30, Month.SEPTEMBER, 2027),
            new Date(29, Month.SEPTEMBER, 2028), new Date(28, Month.SEPTEMBER, 2029),
            new Date(30, Month.SEPTEMBER, 2030), new Date(30, Month.SEPTEMBER, 2031),
            new Date(29, Month.SEPTEMBER, 2034), new Date(30, Month.SEPTEMBER, 2039),
            new Date(30, Month.SEPTEMBER, 2044), new Date(30, Month.SEPTEMBER, 2049),
            new Date(30, Month.SEPTEMBER, 2054), new Date(30, Month.SEPTEMBER, 2059),
            new Date(30, Month.SEPTEMBER, 2064), new Date(30, Month.SEPTEMBER, 2069)
    };

    private static final double[] REF_ZERO_RATE = {
            -0.00373354, -0.00381005, -0.00387689, -0.00394124, -0.00407706, -0.00413633, -0.00411935,
            -0.00416370, -0.00420557, -0.00424431, -0.00427824, -0.00430977, -0.00434401, -0.00445243,
            -0.00448506, -0.00433690, -0.00407401, -0.00372752, -0.00330050, -0.00279139, -0.00225477,
            -0.00173422, -0.00123688, -0.00077237, 0.00038554, 0.00144248, 0.00175995, 0.00172873,
            0.00150782, 0.00121145, 0.000933912, 0.000628946
    };

    private static final double[] REF_ZERO_RATE_NP = {
            -0.00373354, -0.00386194, -0.00395205, -0.00403303, -0.00408033, -0.00410875, -0.00411935,
            -0.00419161, -0.00424817, -0.00429923, -0.00428029, -0.00429178, -0.00434401, -0.00445243,
            -0.00448506, -0.0043369, -0.00407401, -0.00372752, -0.0033005, -0.00279139, -0.00225477,
            -0.00173422, -0.00123688, -0.00077236, 0.00038550, 0.00144208, 0.00175947, 0.00172834,
            0.00150757, 0.00121131, 0.00093384, 0.00062891
    };

    private static final double[] REF_ZERO_RATE_GP = {
            -0.00377892, -0.00386127, -0.00394737, -0.00402914, -0.00409541, -0.00413252, -0.00415463,
            -0.00419484, -0.00424238, -0.00427875, -0.00429712, -0.00431898, -0.00436027, -0.00445297,
            -0.00448502, -0.00433694, -0.00407406, -0.00372755, -0.00330018, -0.00279133, -0.00225491,
            -0.00173429, -0.00123643, -0.00077298, 0.00038547, 0.00144206, 0.00175948, 0.00172834,
            0.00150756, 0.00121135, 0.00093379, 0.00062895
    };

    private static final int[] SWAP_TENORS = {
            2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 25, 30, 35, 40, 45, 50
    };

    // Official CommonVars fixture (test-suite/piecewiseyieldcurve.cpp).
    private static final double[] DEPOSIT_RATES = {
            4.559, 4.581, 4.573, 4.557, 4.496, 4.490
    };
    private static final int[] DEPOSIT_LENGTHS = {1, 1, 2, 3, 6, 9};
    private static final TimeUnit[] DEPOSIT_UNITS = {
            TimeUnit.WEEKS, TimeUnit.MONTHS, TimeUnit.MONTHS, TimeUnit.MONTHS,
            TimeUnit.MONTHS, TimeUnit.MONTHS
    };
    private static final double[] SWAP_RATES = {
            4.54, 4.63, 4.75, 4.86, 4.99, 5.11, 5.23, 5.33, 5.41, 5.47, 5.60, 5.75,
            5.89, 5.95, 5.96
    };
    private static final int[] SWAP_YEARS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30
    };
    private static final double[] IMM_FUT_RATES = {4.581, 4.573, 4.557};

    // Official expected nodes of testGlobalBootstrapInitialGuessFn.
    private static final Date[] EXPECTED_DATES = {
            new Date(27, Month.SEPTEMBER, 2019), new Date(1, Month.OCTOBER, 2019),
            new Date(15, Month.NOVEMBER, 2019), new Date(30, Month.DECEMBER, 2019),
            new Date(13, Month.FEBRUARY, 2020), new Date(30, Month.MARCH, 2020),
            new Date(14, Month.MAY, 2020), new Date(29, Month.JUNE, 2020),
            new Date(13, Month.AUGUST, 2020), new Date(28, Month.SEPTEMBER, 2020),
            new Date(27, Month.SEPTEMBER, 2021), new Date(27, Month.SEPTEMBER, 2022),
            new Date(27, Month.SEPTEMBER, 2023), new Date(27, Month.SEPTEMBER, 2024),
            new Date(29, Month.SEPTEMBER, 2025), new Date(28, Month.SEPTEMBER, 2026),
            new Date(27, Month.SEPTEMBER, 2027), new Date(27, Month.SEPTEMBER, 2028),
            new Date(27, Month.SEPTEMBER, 2029), new Date(29, Month.SEPTEMBER, 2031),
            new Date(27, Month.SEPTEMBER, 2034), new Date(27, Month.SEPTEMBER, 2039),
            new Date(27, Month.SEPTEMBER, 2044), new Date(27, Month.SEPTEMBER, 2049)
    };

    private static final double[] EXPECTED_DFS = {
            1.0,                0.9994923431162580, 0.9938069118457880, 0.9882401711181712,
            0.9828381250775845, 0.9774079566030049, 0.9721182498392723, 0.9667951580943512,
            0.9617336341824015, 0.9564548068987965, 0.9135007024272714, 0.8698607442445871,
            0.8266650007699866, 0.7829682196126155, 0.7399209887960153, 0.6973589159124343,
            0.6565792747227167, 0.6180250998622848, 0.5818438569698231, 0.5127051907762759,
            0.4218543471337612, 0.3050840671400172, 0.2225943633033588, 0.165544906093695
    };

    @Test
    public void testGlobalBootstrap() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();
            Date today = new Date(26, Month.SEPTEMBER, 2019);
            Settings.instance.evaluationDate().equal(today);

            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex index = euribor6m(settlement);

            List<RateHelper> helpers = marketHelpers(target, actual360, index);

            // global bootstrap constraints
            List<RateHelper> additionalHelpers = new ArrayList<>();
            for (int i = 0; i < 7; ++i)
                additionalHelpers.add(fra(-0.004, 12 + i, target, actual360));

            final List<RateHelper> additional = additionalHelpers;
            java.util.function.Supplier<List<Date>> additionalDates = () -> {
                Calendar cal = new Target();
                Date now = Settings.instance.evaluationDate().Date();
                Date settl = cal.advance(now, 2, TimeUnit.DAYS,
                        BusinessDayConvention.FOLLOWING, false);
                List<Date> dates = new ArrayList<>();
                for (int i = 0; i < 5; ++i)
                    dates.add(cal.advance(settl, 1 + i, TimeUnit.MONTHS,
                            BusinessDayConvention.FOLLOWING, false));
                // Add dates before the referenceDate and not in sorted order.
                // These should be skipped by GlobalBootstrap::initialize().
                dates.add(0, now.add(-1));
                dates.add(now.add(-2));
                return dates;
            };
            java.util.function.Supplier<Array> additionalErrors = () -> {
                Array errors = new Array(5);
                double a = additional.get(0).impliedQuote();
                double b = additional.get(6).impliedQuote();
                for (int k = 0; k < 5; ++k)
                    errors.set(k, (5.0 - k) / 6.0 * a + (1.0 + k) / 6.0 * b
                            - additional.get(1 + k).impliedQuote());
                return errors;
            };

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, helpers, new Actual365Fixed(),
                    BootstrapTraits.SimpleZeroYield, new Linear(), 1.0e-12,
                    new GlobalBootstrap(additionalHelpers, additionalDates,
                            additionalErrors, 1.0e-12));
            curve.enableExtrapolation();

            // check expected pillar dates
            for (int i = 0; i < REF_DATE.length; ++i)
                assertEquals(REF_DATE[i].serialNumber(), helpers.get(i).pillarDate().serialNumber(),
                        "pillar date " + (i + 1));

            // check expected zero rates
            for (int i = 0; i < REF_ZERO_RATE.length; ++i) {
                double zero = curve.zeroRate(REF_DATE[i], actual360,
                        Compounding.Continuous, Frequency.ANNUAL, false).rate();
                assertEquals(REF_ZERO_RATE[i], zero, TOLERANCE, "zero rate " + (i + 1));
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
    public void testGlobalBootstrapPenalty() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();
            Date today = new Date(26, Month.SEPTEMBER, 2019);
            Settings.instance.evaluationDate().equal(today);

            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex index = euribor6m(settlement);
            List<RateHelper> helpers = marketHelpers(target, actual360, index);

            // build the curve without penalties first
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, helpers, new Actual365Fixed(),
                    BootstrapTraits.ForwardRate, new BackwardFlat(), 1.0e-12,
                    new GlobalBootstrap(new ArrayList<>(), null,
                            (GlobalBootstrap.AdditionalPenalties) null, 1.0e-12));
            curve.enableExtrapolation();

            // check expected pillar dates
            for (int i = 0; i < REF_DATE.length; ++i)
                assertEquals(REF_DATE[i].serialNumber(), helpers.get(i).pillarDate().serialNumber(),
                        "pillar date " + (i + 1));

            // check expected zero rates
            for (int i = 0; i < REF_ZERO_RATE_NP.length; ++i) {
                double zero = curve.zeroRate(REF_DATE[i], actual360,
                        Compounding.Continuous, Frequency.ANNUAL, false).rate();
                assertEquals(REF_ZERO_RATE_NP[i], zero, TOLERANCE,
                        "zero rate without penalty " + (i + 1));
            }

            // build the curve with gradient penalties
            GlobalBootstrap.AdditionalPenalties gradientPenalty = (times, data) -> {
                Array errors = new Array(times.length - 1);
                for (int i = 0; i < times.length - 1; ++i)
                    errors.set(i, 0.01 * (data[i + 1] - data[i])
                            / (times[i + 1] - times[i]));
                return errors;
            };
            curve = new PiecewiseYieldCurve(
                    settlement, helpers, new Actual365Fixed(),
                    BootstrapTraits.ForwardRate, new BackwardFlat(), 1.0e-12,
                    new GlobalBootstrap(new ArrayList<>(), null,
                            gradientPenalty, 1.0e-12));
            curve.enableExtrapolation();

            // check expected zero rates
            for (int i = 0; i < REF_ZERO_RATE_GP.length; ++i) {
                double zero = curve.zeroRate(REF_DATE[i], actual360,
                        Compounding.Continuous, Frequency.ANNUAL, false).rate();
                assertEquals(REF_ZERO_RATE_GP[i], zero, TOLERANCE,
                        "zero rate with penalty " + (i + 1));
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
     * Official {@code testGlobalBootstrapVariables}: the futures convexity
     * adjustment is re-priced through a {@link SimpleQuoteVariables} volatility
     * until the deposit and swap pillars match the plain curve.
     */
    @Test
    public void testGlobalBootstrapVariables() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();
            CommonVars vars = commonVars(new Date(25, Month.SEPTEMBER, 2019));

            // First, build the curve without futures.
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    vars.settlement, vars.instruments, new Actual365Fixed(),
                    BootstrapTraits.Discount, new LogLinear(), new GlobalBootstrap());

            // Now build the curve with futures adjusted to the same level as
            // swaps. Remove the first swap so it does not create a pillar.
            List<RateHelper> helpers = new ArrayList<>(vars.instruments);
            RateHelper firstSwap = helpers.remove(vars.deposits);

            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), vars.settlement);
            Date immDate = vars.today;
            // Optimize vol as an additional variable during bootstrapping.
            SimpleQuote vol = new SimpleQuote(Double.NaN);
            Handle<Quote> mr = new Handle<>(new SimpleQuote(0.03), true);
            for (int i = 0; i < vars.immFuts; ++i) {
                Handle<Quote> r = new Handle<>(vars.immFutPrices.get(i), true);
                // Official test: IMM::nextDate(immDate) uses mainCycle=true.
                immDate = IMM.nextDate(immDate, true);
                // if the fixing is before today, jump by one future maturity
                if (TimeUtils.less(euribor3m.fixingDate(immDate), vars.today))
                    immDate = IMM.nextDate(immDate, true);
                FuturesConvAdjustmentQuote convAdj = new FuturesConvAdjustmentQuote(
                        euribor3m, immDate, r, new Handle<>(vol, true), mr);
                // registerAsObserver = false, as in the official test.
                helpers.add(new FuturesRateHelper(r, immDate, euribor3m,
                        new Handle<>(convAdj, false), Futures.Type.IMM));
            }

            Supplier<Array> penalties =
                    () -> new Array(new double[]{1e4 * firstSwap.quoteError()});
            SimpleQuoteVariables variables = new SimpleQuoteVariables(
                    List.of(vol), List.of(1.0), List.of(0.0));
            PiecewiseYieldCurve curveFutures = new PiecewiseYieldCurve(
                    vars.settlement, helpers, new Actual365Fixed(),
                    BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(List.of(firstSwap), null, penalties, 1e-12,
                            null, null, variables, null, null));

            // Check that the pillars are not the same.
            assertFalse(sameDates(curve.dates(), curveFutures.dates()));

            // Check that all deposit and swap rates are the same in both curves.
            for (RateHelper helper : vars.instruments) {
                assertRelativeClose(curve.discount(helper.pillarDate(), false),
                        curveFutures.discount(helper.pillarDate(), false), 1e-6,
                        "discount at pillar " + helper.pillarDate());
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
     * Official {@code testGlobalBootstrapInitialGuessFn}: an empty regular
     * helper list, all instruments as additional helpers, official expected
     * node dates and discount factors checked at the official 1E-10.
     */
    @Test
    public void testGlobalBootstrapInitialGuessFn() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            // Official precondition: usingAtParCoupons().
            IborCoupon.Settings.instance().createAtParCoupons();
            CommonVars vars = commonVars(new Date(25, Month.SEPTEMBER, 2019));

            Calendar calendar = euribor(new Period(6, TimeUnit.MONTHS),
                    vars.settlement).fixingCalendar();
            List<Date> pillarDates = new ArrayList<>();
            pillarDates.add(new Date(1, Month.OCTOBER, 2019));
            for (int i = 0; i < 8; ++i) {
                Date last = pillarDates.get(pillarDates.size() - 1);
                pillarDates.add(calendar.adjust(last.add(45),
                        BusinessDayConvention.FOLLOWING));
            }
            for (RateHelper helper : vars.instruments) {
                if (TimeUtils.greater(helper.pillarDate(),
                        pillarDates.get(pillarDates.size() - 1)))
                    pillarDates.add(helper.pillarDate());
            }

            GlobalBootstrap.AdditionalPenalties penalties = (times, data) -> {
                int nInst = vars.instruments.size();
                Array errors = new Array(nInst + times.length - 2);
                // instrument errors
                for (int i = 0; i < nInst; ++i)
                    errors.set(i, vars.instruments.get(i).quoteError());
                // gradient penalties
                double[] rates = new double[times.length - 1];
                for (int i = 0; i < times.length - 1; ++i) {
                    rates[i] = (data[i] / data[i + 1] - 1.0)
                            / (times[i + 1] - times[i]);
                }
                for (int i = 0; i < times.length - 2; ++i) {
                    errors.set(nInst + i, 1e-2 * (rates[i + 1] - rates[i])
                            / (times[i + 1] - times[i]));
                }
                return errors;
            };

            int[] initialGuessCalls = {0};
            GlobalBootstrap.InitialGuessFn initialGuessFn = (times, data) -> {
                ++initialGuessCalls[0];
                return new Array(times.length - 1, 1.0);
            };

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    vars.settlement, new ArrayList<>(), new Actual365Fixed(),
                    BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(vars.instruments, () -> pillarDates, penalties,
                            1e-12, null, null, null, new ArrayList<>(), initialGuessFn));

            List<Date> nodes = curve.dates();
            assertEquals(EXPECTED_DATES.length, nodes.size(), "node count");
            for (int i = 0; i < EXPECTED_DATES.length; ++i) {
                assertEquals(EXPECTED_DATES[i].serialNumber(), nodes.get(i).serialNumber(),
                        "node date " + i);
                assertEquals(EXPECTED_DFS[i], curve.discount(nodes.get(i), false),
                        1e-10, "node discount " + i);
            }
            assertEquals(1, initialGuessCalls[0], "initial guess calls");
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            if (savedAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
    }

    /**
     * Official {@code testGlobalBootstrapInstrumentWeights}: two deposits with
     * the same pillar weighted {@code 0.1}/{@code 0.9} must price the same
     * whether expressed through instrument weights or through additional dates
     * and penalties. Official tolerance 1E-13.
     */
    @Test
    public void testGlobalBootstrapInstrumentWeights() {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();
            Calendar target = new Target();
            Date today = target.adjust(new Date(23, Month.OCTOBER, 2025),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);

            DayCounter actual360 = new Actual360(false);
            List<RateHelper> helpers = new ArrayList<>();
            helpers.add(new DepositRateHelper(
                    new Handle<>(new SimpleQuote(0.01), false),
                    new Period(6, TimeUnit.MONTHS), 2, target,
                    BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));
            helpers.add(new DepositRateHelper(
                    new Handle<>(new SimpleQuote(0.02), false),
                    new Period(6, TimeUnit.MONTHS), 2, target,
                    BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));

            double w1 = 0.1, w2 = 0.9;

            // curve1 uses traditional helpers with weights w1 and w2
            PiecewiseYieldCurve curve1 = new PiecewiseYieldCurve(
                    today, helpers, actual360, BootstrapTraits.Discount, new LogLinear(),
                    new GlobalBootstrap(1e-10, null, null, List.of(w1, w2), null));

            // curve2 uses custom dates and penalties with the same weights
            Supplier<List<Date>> addDates = () -> List.of(
                    helpers.get(0).pillarDate(), helpers.get(1).pillarDate());
            Supplier<Array> addPenalties = () -> new Array(new double[]{
                    w1 * helpers.get(0).quoteError(), w2 * helpers.get(1).quoteError()});
            PiecewiseYieldCurve curve2 = new PiecewiseYieldCurve(
                    today, new ArrayList<>(), actual360, BootstrapTraits.Discount,
                    new LogLinear(),
                    new GlobalBootstrap(helpers, addDates, addPenalties, 1e-10));

            // check that both approaches result in the same curve
            assertRelativeClose(curve1.discount(0.3, false),
                    curve2.discount(0.3, false), 1e-13, "discount at 0.3");
        } finally {
            Settings.instance.evaluationDate().equal(saved);
            if (savedAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
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

    private static boolean sameDates(List<Date> a, List<Date> b) {
        if (a.size() != b.size())
            return false;
        for (int i = 0; i < a.size(); ++i) {
            if (!TimeUtils.equals(a.get(i), b.get(i)))
                return false;
        }
        return true;
    }

    /** Official CommonVars fixture, with the evaluation date already set. */
    private static CommonVars commonVars(Date evaluationDate) {
        Calendar calendar = new Target();
        Date today = calendar.adjust(evaluationDate, BusinessDayConvention.FOLLOWING);
        Settings.instance.evaluationDate().equal(today);
        Date settlement = calendar.advance(today, 2, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);

        DayCounter actual360 = new Actual360(false);
        IborIndex euribor6m = euribor(new Period(6, TimeUnit.MONTHS), settlement);

        List<RateHelper> instruments = new ArrayList<>();
        for (int i = 0; i < DEPOSIT_RATES.length; ++i) {
            instruments.add(new DepositRateHelper(
                    new Handle<>(new SimpleQuote(DEPOSIT_RATES[i] / 100.0), false),
                    new Period(DEPOSIT_LENGTHS[i], DEPOSIT_UNITS[i]), 2, calendar,
                    BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));
        }
        for (int i = 0; i < SWAP_RATES.length; ++i) {
            instruments.add(new SwapRateHelper(
                    new Handle<>(new SimpleQuote(SWAP_RATES[i] / 100.0), false),
                    new Period(SWAP_YEARS[i], TimeUnit.YEARS), calendar, Frequency.ANNUAL,
                    BusinessDayConvention.UNADJUSTED,
                    new Thirty360(Thirty360.Convention.BondBasis), euribor6m));
        }

        List<SimpleQuote> immFutPrices = new ArrayList<>();
        for (double rate : IMM_FUT_RATES)
            immFutPrices.add(new SimpleQuote(100.0 - rate));

        return new CommonVars(today, settlement, instruments, immFutPrices,
                DEPOSIT_RATES.length, IMM_FUT_RATES.length);
    }

    private static IborIndex euribor(Period tenor, Date referenceDate) {
        DayCounter actual360 = new Actual360(false);
        YieldTermStructure flat = new FlatForward(referenceDate, 0.04, actual360,
                Compounding.Continuous, Frequency.ANNUAL);
        return new IborIndex("Euribor", tenor, 2, new Currency(), new Target(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360,
                new Handle<>(flat, false));
    }

    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final List<RateHelper> instruments;
        final List<SimpleQuote> immFutPrices;
        final int deposits;
        final int immFuts;

        CommonVars(Date today, Date settlement, List<RateHelper> instruments,
                   List<SimpleQuote> immFutPrices, int deposits, int immFuts) {
            this.today = today;
            this.settlement = settlement;
            this.instruments = instruments;
            this.immFutPrices = immFutPrices;
            this.deposits = deposits;
            this.immFuts = immFuts;
        }
    }

    private static List<RateHelper> marketHelpers(Calendar target,
                                                  DayCounter actual360,
                                                  IborIndex index) {
        List<RateHelper> helpers = new ArrayList<>();
        helpers.add(new DepositRateHelper(
                new Handle<>(new SimpleQuote(REF_MKT_RATE[0] / 100.0), false),
                new Period(6, TimeUnit.MONTHS), 2, target,
                BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));

        for (int i = 0; i < 12; ++i)
            helpers.add(fra(REF_MKT_RATE[1 + i] / 100.0, i + 1, target, actual360));

        for (int i = 0; i < SWAP_TENORS.length; ++i)
            helpers.add(new SwapRateHelper(
                    new Handle<>(new SimpleQuote(REF_MKT_RATE[13 + i] / 100.0), false),
                    new Period(SWAP_TENORS[i], TimeUnit.YEARS), target, Frequency.ANNUAL,
                    BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.BondBasis), index));
        return helpers;
    }

    /**
     * JQuant has no {@code FraRateHelper(rate, monthsToStart, IborIndex)}
     * overload, so this reproduces the official helper with the index's
     * parameters: Euribor 6M fixing days, TARGET, ModifiedFollowing,
     * end-of-month, Actual/360, LastRelevantDate pillar and indexed coupon.
     */
    private static FraRateHelper fra(double rate, int monthsToStart,
                                     Calendar target, DayCounter actual360) {
        return new FraRateHelper(
                new Handle<>(new SimpleQuote(rate), false),
                monthsToStart, monthsToStart + 6, 2, target,
                BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360,
                FraRateHelper.Pillar.LastRelevantDate, true);
    }

    private static IborIndex euribor6m(Date settlement) {
        DayCounter actual360 = new Actual360(false);
        YieldTermStructure flat = new FlatForward(settlement, 0.04, actual360,
                Compounding.Continuous, Frequency.ANNUAL);
        return new IborIndex("Euribor", new Period(6, TimeUnit.MONTHS), 2,
                new Currency(), new Target(), BusinessDayConvention.MODIFIED_FOLLOWING,
                true, actual360, new Handle<>(flat, false));
    }
}
