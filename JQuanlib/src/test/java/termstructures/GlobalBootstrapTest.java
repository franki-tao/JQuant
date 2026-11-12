package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.indexes.IborIndex;
import jquant.math.Array;
import jquant.math.interpolations.impl.BackwardFlat;
import jquant.math.interpolations.impl.Linear;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.FraRateHelper;
import jquant.termstructures.yield.GlobalBootstrap;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * <p><b>Not covered here:</b> the remaining official GlobalBootstrap tests
 * ({@code testGlobalBootstrapVariables}, {@code testGlobalBootstrapInitialGuessFn},
 * {@code testGlobalBootstrapInstrumentWeights}) are left unimplemented: they
 * need JQuant types or fixtures that do not exist yet
 * ({@code FuturesConvAdjustmentQuote}/{@code SimpleQuoteVariables}, the
 * {@code CommonVars} futures fixture, and a PiecewiseYieldCurve with an empty
 * regular helper set). Multi-curve joint bootstrap is also not ported.
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
