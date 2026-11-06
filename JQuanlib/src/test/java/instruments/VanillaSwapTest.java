package instruments;

import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code test-suite/swap.cpp} {@code testFairRate} and
 * {@code testCachedValue}, and the swap leg of
 * {@code testLogLinearDiscountConsistency}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/swap.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 */
public class VanillaSwapTest {
    @Test
    public void testCachedValueAndFairRate() {
        // 17 Jun 2002, flat 5% continuous Actual/365 from settlement, payer
        // 10y fixed 6% annual 30/360 vs 6m Euribor plus 1bp. Indexed-coupon
        // cached NPV is -5.872342992212 within 1e-11. At-par coupons would
        // be -5.872863313209.
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(17, Month.JUNE, 2002);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            YieldTermStructure curve = new FlatForward(settlement, 0.05, new Actual365Fixed(),
                    Compounding.Continuous, Frequency.ANNUAL);
            IborIndex euribor6m = euribor(curve);
            VanillaSwap payer = makeSwap(VanillaSwap.Type.Payer, settlement, 10, 0.06, 0.001,
                    euribor6m, curve);
            VanillaSwap receiver = makeSwap(VanillaSwap.Type.Receiver, settlement, 10, 0.06, 0.001,
                    euribor6m, curve);
            assertEquals(-5.872342992212, payer.NPV(), 1.0e-11);
            assertEquals(5.872342992212, receiver.NPV(), 1.0e-11);
            assertTrue(Math.abs(payer.fairRate() - 0.06) > 1.0e-4);
            assertEquals(payer.fairRate(), receiver.fairRate(), 1.0e-12);

            double[] lengths = {1, 5, 10};
            double[] spreads = {0.0, 0.001, -0.001};
            for (double length : lengths) {
                for (double spread : spreads) {
                    VanillaSwap probe = makeSwap(VanillaSwap.Type.Payer, settlement, (int) length,
                            0.0, spread, euribor6m, curve);
                    double fair = probe.fairRate();
                    VanillaSwap repriced = makeSwap(VanillaSwap.Type.Payer, settlement, (int) length,
                            fair, spread, euribor6m, curve);
                    assertEquals(0.0, repriced.NPV(), 1.0e-10);
                }
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testSwapHelpersOnLogLinearCurve() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            DayCounter depositDayCount = new Actual360(false);
            SimpleQuote depositQuote = new SimpleQuote(0.04496);
            RateHelper deposit = new DepositRateHelper(
                    new Handle<>(depositQuote, true),
                    new Period(6, TimeUnit.MONTHS),
                    2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, depositDayCount);

            // piecewiseyieldcurve.cpp swapData, percent.
            double[][] swaps = {{1, 4.54}, {2, 4.63}, {5, 4.99}};
            List<RateHelper> helpers = new ArrayList<>();
            helpers.add(deposit);
            List<SimpleQuote> quotes = new ArrayList<>();
            List<SwapRateHelper> swapHelpers = new ArrayList<>();
            IborIndex index = euribor(new FlatForward(settlement, 0.05, depositDayCount,
                    Compounding.Continuous, Frequency.ANNUAL));
            for (double[] row : swaps) {
                SimpleQuote quote = new SimpleQuote(row[1] / 100.0);
                quotes.add(quote);
                SwapRateHelper helper = new SwapRateHelper(
                        new Handle<>(quote, true),
                        new Period((int) row[0], TimeUnit.YEARS),
                        target, Frequency.ANNUAL, BusinessDayConvention.UNADJUSTED,
                        new Thirty360(Thirty360.Convention.BondBasis), index);
                swapHelpers.add(helper);
                helpers.add(helper);
            }

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(settlement, helpers, depositDayCount);
            assertEquals(1, curve.bootstrapIterations());
            assertEquals(0.04496, deposit.impliedQuote(), 1.0e-9);
            double maxError = 0.0;
            for (int i = 0; i < swapHelpers.size(); i++) {
                double error = Math.abs(swapHelpers.get(i).impliedQuote() - quotes.get(i).value());
                maxError = Math.max(maxError, error);
                assertEquals(swaps[i][1] / 100.0, swapHelpers.get(i).impliedQuote(), 1.0e-9);
                assertEquals(swaps[i][1] / 100.0, quotes.get(i).value(), 0.0);
            }
            assertTrue(maxError <= 1.0e-9);
            assertEquals(0.04496, depositQuote.value(), 0.0);

            // 2 Jan 2025: the 1-year maturity is 6 Jan 2026 and the last
            // 6-month fixing ends on 7 Jan 2026. A maturity pillar is then
            // before the date the forward uses, so the next node changes
            // the earlier fair rate and the bootstrap has to pass again.
            Date rolled = new Date(2, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(rolled);
            Date rolledSettlement = target.advance(rolled, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            SimpleQuote rolledDepositQuote = new SimpleQuote(0.04496);
            RateHelper rolledDeposit = new DepositRateHelper(
                    new Handle<>(rolledDepositQuote, true),
                    new Period(6, TimeUnit.MONTHS),
                    2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, depositDayCount);
            IborIndex rolledIndex = euribor(new FlatForward(rolledSettlement, 0.05, depositDayCount,
                    Compounding.Continuous, Frequency.ANNUAL));
            List<RateHelper> maturityPillars = new ArrayList<>();
            maturityPillars.add(rolledDeposit);
            List<SimpleQuote> rolledQuotes = new ArrayList<>();
            for (double[] row : swaps) {
                SimpleQuote quote = new SimpleQuote(row[1] / 100.0);
                rolledQuotes.add(quote);
                maturityPillars.add(new SwapRateHelper(
                        new Handle<>(quote, true),
                        new Period((int) row[0], TimeUnit.YEARS),
                        target, Frequency.ANNUAL, BusinessDayConvention.UNADJUSTED,
                        new Thirty360(Thirty360.Convention.BondBasis), rolledIndex,
                        SwapRateHelper.Pillar.MaturityDate));
            }
            assertTrue(TimeUtils.less(maturityPillars.get(1).pillarDate(),
                    maturityPillars.get(1).latestRelevantDate()));
            PiecewiseYieldCurve retried = new PiecewiseYieldCurve(
                    rolledSettlement, maturityPillars, depositDayCount);
            assertTrue(retried.bootstrapIterations() >= 2);
            for (int i = 0; i < rolledQuotes.size(); i++) {
                assertEquals(rolledQuotes.get(i).value(), maturityPillars.get(i + 1).impliedQuote(), 1.0e-9);
                assertEquals(swaps[i][1] / 100.0, rolledQuotes.get(i).value(), 0.0);
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static IborIndex euribor(YieldTermStructure curve) {
        return new IborIndex("Euribor", new Period(6, TimeUnit.MONTHS), 2, new jquant.Currency(),
                new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, false,
                new Actual360(false), new Handle<>(curve, false));
    }

    private static VanillaSwap makeSwap(VanillaSwap.Type type, Date settlement, int length,
                                        double fixedRate, double spread, IborIndex index,
                                        YieldTermStructure curve) {
        Calendar calendar = index.fixingCalendar();
        Date maturity = calendar.advance(settlement, length, TimeUnit.YEARS,
                BusinessDayConvention.MODIFIED_FOLLOWING, false);
        Schedule fixed = new Schedule(settlement, maturity, new Period(Frequency.ANNUAL), calendar,
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        Schedule floating = new Schedule(settlement, maturity, new Period(Frequency.SEMIANNUAL), calendar,
                BusinessDayConvention.MODIFIED_FOLLOWING, BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        VanillaSwap swap = new VanillaSwap(type, 100.0, fixed, fixedRate,
                new Thirty360(Thirty360.Convention.BondBasis), floating, index, spread,
                index.dayCounter());
        swap.setPricingEngine(new DiscountingSwapEngine(curve));
        return swap;
    }
}
