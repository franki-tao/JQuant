package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.BMAIndex;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.USDLibor;
import jquant.instruments.BMASwap;
import jquant.instruments.Swap;
import jquant.instruments.VanillaSwap;
import jquant.math.interpolations.impl.LogCubic;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BMASwapRateHelper;
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
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.JointCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code testLogCubicDiscountConsistency}: the deposit and swap sections of
 * {@code testCurveConsistency}, plus the log-cubic {@code testBMACurveConsistency}.
 * Expected values are the published quotes.
 * Tolerance is the official {@code 1e-9}. The interpolator is
 * {@code LogCubic(Spline, true, SecondDerivative, 0, SecondDerivative, 0)}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/QuantLib-v1.16/test-suite/piecewiseyieldcurve.cpp
 */
public class LogCubicDiscountConsistencyTest {
    private static final double TOLERANCE = 1.0e-9;
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
    private static final double[][] BMA = {
            {1, 67.56}, {2, 68.00}, {3, 68.25}, {4, 68.50}, {5, 68.81},
            {7, 69.50}, {10, 70.44}, {15, 71.69}, {20, 72.69}, {30, 73.81}
    };

    @Test
    public void testLogCubicDepositsAndSwaps() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor6m = euribor(new Period(6, TimeUnit.MONTHS), flat());
            List<RateHelper> depositAndSwaps = new ArrayList<>();
            for (int i = 0; i < DEPOSIT_RATES.length; i++) {
                depositAndSwaps.add(new DepositRateHelper(
                        new Handle<>(new SimpleQuote(DEPOSIT_RATES[i] / 100.0), false),
                        new Period(DEPOSIT_LENGTHS[i], DEPOSIT_UNITS[i]), 2, target,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));
            }
            for (int i = 0; i < SWAP_RATES.length; i++) {
                depositAndSwaps.add(new SwapRateHelper(
                        new Handle<>(new SimpleQuote(SWAP_RATES[i] / 100.0), false),
                        new Period(SWAP_YEARS[i], TimeUnit.YEARS), target, Frequency.ANNUAL,
                        BusinessDayConvention.UNADJUSTED,
                        new Thirty360(Thirty360.Convention.BondBasis), euribor6m));
            }
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, depositAndSwaps, actual360, monotonicLogCubic(), 1.0e-12);
            assertTrue(curve.bootstrapIterations() > 1);
            IborIndex forecast = euribor(new Period(6, TimeUnit.MONTHS), curve);
            for (int i = 0; i < DEPOSIT_RATES.length; i++) {
                IborIndex depositIndex = euribor(new Period(DEPOSIT_LENGTHS[i], DEPOSIT_UNITS[i]), curve);
                assertEquals(DEPOSIT_RATES[i] / 100.0, depositIndex.fixing(today, false), TOLERANCE,
                        DEPOSIT_LENGTHS[i] + " " + DEPOSIT_UNITS[i] + " deposit");
            }
            for (int i = 0; i < SWAP_RATES.length; i++) {
                Date maturity = target.advance(settlement, SWAP_YEARS[i], TimeUnit.YEARS,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true);
                Schedule fixed = new Schedule(settlement, maturity, new Period(Frequency.ANNUAL), target,
                        BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                        DateGenerationRule.FORWARD, false, new Date(), new Date());
                Schedule floating = new Schedule(settlement, maturity, new Period(6, TimeUnit.MONTHS), target,
                        BusinessDayConvention.MODIFIED_FOLLOWING, BusinessDayConvention.MODIFIED_FOLLOWING,
                        DateGenerationRule.FORWARD, false, new Date(), new Date());
                VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, 1.0, fixed, 0.0,
                        new Thirty360(Thirty360.Convention.BondBasis), floating, forecast, 0.0, actual360);
                swap.setPricingEngine(new DiscountingSwapEngine(curve));
                assertEquals(SWAP_RATES[i] / 100.0, swap.fairRate(), TOLERANCE, SWAP_YEARS[i] + " year swap");
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testLogCubicBMACurveConsistency() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            BMAIndex fixingIndex = new BMAIndex();
            USDLibor calendarLibor = new USDLibor(new Period(3, TimeUnit.MONTHS));
            Calendar calendar = new JointCalendar(fixingIndex.fixingCalendar(),
                    calendarLibor.fixingCalendar(), JointCalendar.Rule.JoinHolidays);
            Date today = calendar.adjust(new Date(10, Month.NOVEMBER, 2026), BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            Date settlement = calendar.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            YieldTermStructure riskFree = new FlatForward(settlement, 0.04, new Actual360(false),
                    Compounding.Continuous, Frequency.ANNUAL);
            USDLibor libor = new USDLibor(new Period(3, TimeUnit.MONTHS), new Handle<>(riskFree, false));
            List<RateHelper> helpers = new ArrayList<>();
            for (double[] row : BMA) {
                helpers.add(new BMASwapRateHelper(
                        new Handle<>(new SimpleQuote(row[1] / 100.0), false),
                        new Period((int) row[0], TimeUnit.YEARS),
                        2, calendar, new Period(Frequency.QUARTERLY),
                        BusinessDayConvention.FOLLOWING,
                        new ActualActual(ActualActual.Convention.ISDA),
                        fixingIndex, libor));
            }
            int weekday = today.weekday().getValue();
            Date lastWednesday = weekday >= 4
                    ? today.add(-(weekday - 4))
                    : today.add(4 - weekday - 7);
            Date lastFixing = fixingIndex.fixingCalendar().adjust(lastWednesday, BusinessDayConvention.FOLLOWING);
            fixingIndex.addFixing(lastFixing, 0.03, false);

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    today, helpers, new Actual360(false), monotonicLogCubic(), 1.0e-12);
            assertTrue(curve.bootstrapIterations() > 1);
            BMAIndex bma = new BMAIndex(new Handle<>(curve, false));
            for (double[] row : BMA) {
                int years = (int) row[0];
                Date maturity = settlement.add(new Period(years, TimeUnit.YEARS));
                Schedule bmaSchedule = new MakeSchedule().from(settlement).to(maturity)
                        .withFrequency(Frequency.QUARTERLY)
                        .withCalendar(bma.fixingCalendar())
                        .withConvention(BusinessDayConvention.FOLLOWING)
                        .backwards()
                        .toSchedule();
                Schedule liborSchedule = new MakeSchedule().from(settlement).to(maturity)
                        .withTenor(libor.tenor())
                        .withCalendar(libor.fixingCalendar())
                        .withConvention(libor.businessDayConvention())
                        .endOfMonth(libor.endOfMonth())
                        .backwards()
                        .toSchedule();
                BMASwap swap = new BMASwap(Swap.Type.Payer, 100.0,
                        liborSchedule, 0.75, 0.0, libor, libor.dayCounter(),
                        bmaSchedule, bma, new ActualActual(ActualActual.Convention.ISDA));
                swap.setPricingEngine(new DiscountingMultiLegSwapEngine(riskFree));
                assertEquals(row[1] / 100.0, swap.fairLiborFraction(), TOLERANCE, years + " year BMA swap");
            }
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static LogCubic monotonicLogCubic() {
        return new LogCubic(Spline, true, SecondDerivative, 0.0, SecondDerivative, 0.0);
    }

    private static YieldTermStructure flat() {
        return new FlatForward(new Date(15, Month.JANUARY, 2025), 0.04, new Actual360(false),
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static IborIndex euribor(Period tenor, YieldTermStructure curve) {
        return new IborIndex("Euribor", tenor, 2, new Currency(), new Target(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true, new Actual360(false),
                new Handle<>(curve, false));
    }
}
