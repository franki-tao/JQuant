package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.indexes.IborIndex;
import jquant.instruments.VanillaSwap;
import jquant.math.interpolations.impl.LogLinear;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.GlobalBootstrap;
import jquant.termstructures.yield.PiecewiseSpreadYieldCurve;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SpreadDiscountCurve;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * QuantLib {@code testPiecewiseSpreadYieldCurve}
 * (test-suite/piecewiseyieldcurve.cpp, lines 1871-1987).
 *
 * <p>The base curve is an ordinary discount {@link PiecewiseYieldCurve} built
 * from the official deposits and swaps with Actual/365 (Fixed) and log-linear
 * interpolation. The spread curve has fewer pillars (six swaps only) and uses
 * Euribor 3M, while the base swaps use Euribor 6M. Both the iterative and the
 * global bootstrapper are exercised, exactly as the official test does.
 *
 * <p>Official tolerances are kept verbatim:
 * <ul>
 *   <li>swap repricing: {@code 1.0e-9} absolute;</li>
 *   <li>curve shape: strict inequality against {@code 1e-4};</li>
 *   <li>constant-spread extrapolation and raw-curve rebuild:
 *       {@code QL_CHECK_CLOSE(..., 1e-9)}, i.e. Boost's percentage tolerance
 *       of 1e-9 percent (relative {@code 1e-11}), never an absolute 1e-9.</li>
 * </ul>
 *
 * <p>The official suite runs with the QuantLib default
 * {@code IborCoupon::Settings::usingAtParCoupons() == true}; JQuant's Java
 * default is false, so the setting is switched and restored here. This is what
 * keeps the 3M and 6M swap termination dates aligned.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/piecewisespreadyieldcurve.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/spreaddiscountcurve.hpp
 */
public class PiecewiseSpreadYieldCurveTest {
    private static final double TOLERANCE = 1.0e-9;
    /** Official {@code QL_CHECK_CLOSE} percentage tolerance. */
    private static final double CLOSE_PERCENT_TOLERANCE = 1.0e-9;

    private static final double[] DEPOSIT_RATES = {4.559, 4.581, 4.573, 4.557, 4.496, 4.490};
    private static final int[] DEPOSIT_LENGTHS = {1, 1, 2, 3, 6, 9};
    private static final TimeUnit[] DEPOSIT_UNITS = {
            TimeUnit.WEEKS, TimeUnit.MONTHS, TimeUnit.MONTHS, TimeUnit.MONTHS,
            TimeUnit.MONTHS, TimeUnit.MONTHS
    };
    private static final double[] SWAP_RATES = {
            4.54, 4.63, 4.75, 4.86, 4.99, 5.11, 5.23, 5.33, 5.41, 5.47, 5.60, 5.75, 5.89, 5.95, 5.96
    };
    private static final int[] SWAP_YEARS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};

    // Official spread benchmarks (test-suite/piecewiseyieldcurve.cpp).
    private static final int[] SPREAD_SWAP_YEARS = {1, 3, 6, 9, 15, 30};
    private static final double[] SPREAD_SWAP_RATES = {4.44, 4.55, 4.81, 5.01, 5.25, 5.36};

    @Test
    public void testPiecewiseSpreadYieldCurveIterative() {
        testPiecewiseSpreadYieldCurve(false);
    }

    @Test
    public void testPiecewiseSpreadYieldCurveGlobal() {
        testPiecewiseSpreadYieldCurve(true);
    }

    private static void testPiecewiseSpreadYieldCurve(boolean useGlobalBootstrap) {
        boolean savedAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        Date savedEvaluationDate = Settings.instance.evaluationDate().Date().copy();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();

            Calendar target = new Target();
            Date today = target.adjust(new Date(23, Month.SEPTEMBER, 2019),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);

            DayCounter actual360 = new Actual360(false);
            DayCounter actual365 = new Actual365Fixed();
            DayCounter fixedLegDayCounter = new Thirty360(Thirty360.Convention.BondBasis);

            // Base curve: official depositData + swapData.
            IborIndex euribor6m = euribor(new Period(6, TimeUnit.MONTHS));
            List<RateHelper> instruments = new ArrayList<>();
            for (int i = 0; i < DEPOSIT_RATES.length; i++) {
                instruments.add(new DepositRateHelper(
                        new Handle<>(new SimpleQuote(DEPOSIT_RATES[i] / 100.0), false),
                        new Period(DEPOSIT_LENGTHS[i], DEPOSIT_UNITS[i]), 2, target,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));
            }
            for (int i = 0; i < SWAP_RATES.length; i++) {
                instruments.add(new SwapRateHelper(
                        new Handle<>(new SimpleQuote(SWAP_RATES[i] / 100.0), false),
                        new Period(SWAP_YEARS[i], TimeUnit.YEARS), target, Frequency.ANNUAL,
                        BusinessDayConvention.UNADJUSTED, fixedLegDayCounter, euribor6m));
            }

            PiecewiseYieldCurve baseCurve = new PiecewiseYieldCurve(
                    settlement, instruments, actual365,
                    BootstrapTraits.Discount, new LogLinear());
            baseCurve.enableExtrapolation();
            Handle<YieldTermStructure> baseCurveHandle = new Handle<>(baseCurve, true);

            // Spread curve: the six official swap benchmarks, quoted on 3M.
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS));
            List<RateHelper> spreadHelpers = new ArrayList<>();
            for (int i = 0; i < SPREAD_SWAP_YEARS.length; i++) {
                spreadHelpers.add(new SwapRateHelper(
                        new Handle<>(new SimpleQuote(SPREAD_SWAP_RATES[i] / 100.0), false),
                        new Period(SPREAD_SWAP_YEARS[i], TimeUnit.YEARS), target,
                        Frequency.ANNUAL, BusinessDayConvention.UNADJUSTED,
                        fixedLegDayCounter, euribor3m));
            }

            PiecewiseSpreadYieldCurve curve = useGlobalBootstrap
                    ? new PiecewiseSpreadYieldCurve(baseCurveHandle, spreadHelpers,
                            new LogLinear(), new GlobalBootstrap())
                    : new PiecewiseSpreadYieldCurve(baseCurveHandle, spreadHelpers,
                            new LogLinear());
            curve.enableExtrapolation();

            // Check that the swaps are repriced on the spread curve.
            IborIndex forecast = euribor(new Period(3, TimeUnit.MONTHS),
                    new Handle<>(curve, false));
            for (int i = 0; i < SPREAD_SWAP_YEARS.length; i++) {
                Date maturity = settlement.add(new Period(SPREAD_SWAP_YEARS[i], TimeUnit.YEARS));
                Schedule fixed = new Schedule(settlement, maturity,
                        new Period(Frequency.ANNUAL), target,
                        BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                Schedule floating = new Schedule(settlement, maturity,
                        new Period(3, TimeUnit.MONTHS), target,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, 1.0,
                        fixed, 0.0, fixedLegDayCounter, floating, forecast, 0.0, actual360);
                swap.setPricingEngine(new DiscountingSwapEngine(curve));
                double expected = SPREAD_SWAP_RATES[i] / 100.0;
                assertEquals(expected, swap.fairRate(), TOLERANCE,
                        SPREAD_SWAP_YEARS[i] + " year spread swap");
            }

            // Check that the curve has shape between pillars.
            Date prev = settlement;
            for (RateHelper helper : spreadHelpers) {
                Date pillar = helper.pillarDate();
                double rate1 = curve.forwardRate(prev, pillar, actual365,
                        Compounding.Continuous, Frequency.ANNUAL, false).rate();
                Date mid = prev.add(TimeUtils.substract(pillar, prev) / 2);
                double rate2 = curve.forwardRate(prev, mid, actual365,
                        Compounding.Continuous, Frequency.ANNUAL, false).rate();
                assertTrue(Math.abs(rate1 - rate2) > 1e-4,
                        "flat forward between " + prev + " and " + pillar
                                + ": " + rate1 + " vs " + rate2);
                prev = pillar;
            }

            // Check that extrapolation preserves a constant spread.
            Date maxDate = curve.maxDate();
            assertTrue(TimeUtils.equals(baseCurve.maxDate(), maxDate),
                    "spread curve max date " + maxDate + " != base max date "
                            + baseCurve.maxDate());
            Period oneYear = new Period(1, TimeUnit.YEARS);
            double rate1 = curve.forwardRate(maxDate.substract(oneYear), maxDate, actual365,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            double rate2 = curve.forwardRate(maxDate, maxDate.add(oneYear), actual365,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            double baseRate1 = baseCurve.forwardRate(maxDate.substract(oneYear), maxDate, actual365,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            double baseRate2 = baseCurve.forwardRate(maxDate, maxDate.add(oneYear), actual365,
                    Compounding.Continuous, Frequency.ANNUAL, false).rate();
            assertClosePercentage(rate1 - baseRate1, rate2 - baseRate2,
                    CLOSE_PERCENT_TOLERANCE, "constant spread beyond max date");

            // Check accessors.
            int n = spreadHelpers.size() + 1;
            assertEquals(n, curve.dates().size(), "dates size");
            assertEquals(n, curve.times().length, "times size");
            assertEquals(n, curve.data().length, "data size");
            List<Map.Entry<Date, Double>> nodes = curve.nodes();
            assertEquals(n, nodes.size(), "nodes size");

            assertTrue(TimeUtils.equals(settlement, curve.dates().get(0)),
                    "reference date node");
            assertEquals(0.0, curve.times()[0], 0.0, "reference time");
            assertEquals(1.0, curve.data()[0], 0.0, "reference spread discount");
            assertTrue(TimeUtils.equals(settlement, nodes.get(0).getKey()), "first node date");
            assertEquals(1.0, nodes.get(0).getValue(), 0.0, "first node value");
            for (int i = 0; i < spreadHelpers.size(); i++) {
                Date pillar = spreadHelpers.get(i).pillarDate();
                assertTrue(TimeUtils.equals(pillar, curve.dates().get(i + 1)),
                        "pillar date " + (i + 1) + ": " + curve.dates().get(i + 1)
                                + " != " + pillar);
                assertEquals(curve.timeFromReference(pillar), curve.times()[i + 1], 0.0,
                        "pillar time " + (i + 1));
                assertTrue(TimeUtils.equals(curve.dates().get(i + 1),
                                nodes.get(i + 1).getKey()), "node date " + (i + 1));
                assertEquals(curve.data()[i + 1], nodes.get(i + 1).getValue(), 0.0,
                        "node value " + (i + 1));
            }

            // Check that the curve can be rebuilt from the raw data.
            SpreadDiscountCurve rawCurve = new SpreadDiscountCurve(
                    curve.baseCurve(), curve.dates(), curve.data());
            rawCurve.enableExtrapolation();

            int maxSwapYears = SPREAD_SWAP_YEARS[SPREAD_SWAP_YEARS.length - 1];
            for (int i = 0; i < maxSwapYears + 3; ++i) {
                Date d = settlement.add(new Period(i, TimeUnit.YEARS));
                assertClosePercentage(curve.discount(d, false), rawCurve.discount(d, false),
                        CLOSE_PERCENT_TOLERANCE, "raw-curve discount at " + d);
            }
        } finally {
            Settings.instance.evaluationDate().equal(savedEvaluationDate);
            if (savedAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
    }

    /**
     * Boost's {@code BOOST_CHECK_CLOSE} / QuantLib's {@code QL_CHECK_CLOSE}:
     * the tolerance is a percentage of the second (expected) value. A tolerance
     * of {@code 1e-9} therefore means a relative error of at most {@code 1e-11}.
     */
    private static void assertClosePercentage(double actual, double expected,
                                              double percentTolerance, String message) {
        if (actual == expected)
            return;
        double relativeTolerance = percentTolerance / 100.0;
        double error = Math.abs(actual - expected);
        boolean close = expected == 0.0
                ? error == 0.0
                : error / Math.abs(expected) <= relativeTolerance;
        if (!close) {
            fail(message + ": expected " + expected + " but was " + actual
                    + " (relative error " + (expected == 0.0 ? Double.POSITIVE_INFINITY
                    : error / Math.abs(expected)) + " > " + relativeTolerance + ")");
        }
    }

    private static IborIndex euribor(Period tenor) {
        return euribor(tenor, new Handle<>(null, false));
    }

    private static IborIndex euribor(Period tenor, Handle<YieldTermStructure> curve) {
        return new IborIndex("Euribor", tenor, 2, new Currency(), new Target(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true, new Actual360(false),
                curve);
    }
}
