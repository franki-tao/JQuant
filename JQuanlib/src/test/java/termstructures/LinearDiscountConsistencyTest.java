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
import jquant.instruments.FixedRateBond;
import jquant.instruments.ForwardRateAgreement;
import jquant.instruments.Futures;
import jquant.instruments.Swap;
import jquant.instruments.VanillaSwap;
import jquant.math.interpolations.impl.Linear;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BMASwapRateHelper;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FixedRateBondHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.FraRateHelper;
import jquant.termstructures.yield.FuturesRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.ASX;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.IMM;
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.JointCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code testLinearDiscountConsistency}:
 * {@code testCurveConsistency<Discount, Linear, IterativeBootstrap>} plus
 * {@code testBMACurveConsistency<Discount, Linear, IterativeBootstrap>}. The
 * fixture data is the official {@code CommonVars} of
 * {@code test-suite/piecewiseyieldcurve.cpp} and every expected value is a
 * published quote, never a value computed from the curve under test.
 * Tolerance is the official {@code 1e-9}.
 *
 * <p>The curve is built as
 * {@code new PiecewiseYieldCurve(..., BootstrapTraits.Discount, new Linear())}
 * with the default accuracy.
 *
 * <p>Sections: deposits+swaps, bonds, indexed FRA, at-par FRA, IMM futures,
 * ASX futures and BMA swaps.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/discountcurve.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/math/interpolations/linearinterpolation.hpp
 */
public class LinearDiscountConsistencyTest {
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
    /** Official {@code fraData}: months to start; the end is start + 3 months. */
    private static final int[] FRA_MONTHS = {1, 2, 3, 6, 9};
    private static final double[] FRA_RATES = {4.581, 4.573, 4.557, 4.496, 4.490};
    private static final double[] FUTURES_RATES = {4.581, 4.573, 4.557};
    // n, length in years, coupon percent, clean price. Semiannual, redemption 100.
    private static final double[][] BONDS = {
            {0.5, 5, 4.75, 101.320},
            {1, 3, 2.75, 100.590},
            {2, 5, 5.00, 105.650},
            {5, 11, 5.50, 113.610},
            {10, 11, 3.75, 104.070}
    };
    private static final double[][] BMA = {
            {1, 67.56}, {2, 68.00}, {3, 68.25}, {4, 68.50}, {5, 68.81},
            {7, 69.50}, {10, 70.44}, {15, 71.69}, {20, 72.69}, {30, 73.81}
    };

    @Test
    public void testDepositsAndSwaps() {
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
                    settlement, depositAndSwaps, actual360,
                    BootstrapTraits.Discount, new Linear());
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
    public void testBonds() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            List<RateHelper> bondHelpers = new ArrayList<>();
            List<FixedRateBond> bonds = new ArrayList<>();
            for (double[] row : BONDS) {
                Date maturity = target.advance(today, (int) row[0], row[0] < 1 ? TimeUnit.MONTHS : TimeUnit.YEARS,
                        BusinessDayConvention.FOLLOWING, false);
                if (row[0] < 1)
                    maturity = target.advance(today, 6, TimeUnit.MONTHS, BusinessDayConvention.FOLLOWING, false);
                Date issue = target.advance(maturity, -(int) row[1], TimeUnit.YEARS,
                        BusinessDayConvention.FOLLOWING, false);
                Schedule schedule = new Schedule(issue, maturity, new Period(Frequency.SEMIANNUAL), target,
                        BusinessDayConvention.FOLLOWING, BusinessDayConvention.FOLLOWING,
                        DateGenerationRule.BACKWARD, false, new Date(), new Date());
                FixedRateBond bond = new FixedRateBond(3, 100.0, schedule, new double[]{row[2] / 100.0},
                        new ActualActual(ActualActual.Convention.ISDA), BusinessDayConvention.FOLLOWING,
                        100.0, issue);
                bonds.add(bond);
                bondHelpers.add(new FixedRateBondHelper(
                        new Handle<>(new SimpleQuote(row[3]), false), 3, 100.0, schedule,
                        new double[]{row[2] / 100.0}, new ActualActual(ActualActual.Convention.ISDA),
                        BusinessDayConvention.FOLLOWING, 100.0, issue));
            }
            PiecewiseYieldCurve bondCurve = new PiecewiseYieldCurve(
                    settlement, bondHelpers, actual360,
                    BootstrapTraits.Discount, new Linear());
            for (int i = 0; i < BONDS.length; i++) {
                bonds.get(i).setPricingEngine(new jquant.pricingengines.DiscountingBondEngine(bondCurve));
                assertEquals(BONDS[i][3], bonds.get(i).cleanPrice(), TOLERANCE, "bond " + (i + 1));
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testFraIndexed() {
        fraConsistency(true);
    }

    @Test
    public void testFraAtPar() {
        fraConsistency(false);
    }

    private static void fraConsistency(boolean useIndexedFra) {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            List<RateHelper> fraHelpers = new ArrayList<>();
            for (int i = 0; i < FRA_MONTHS.length; i++) {
                fraHelpers.add(new FraRateHelper(
                        new Handle<>(new SimpleQuote(FRA_RATES[i] / 100.0), false),
                        FRA_MONTHS[i], FRA_MONTHS[i] + 3,
                        2, target, BusinessDayConvention.MODIFIED_FOLLOWING, true,
                        actual360, FraRateHelper.Pillar.LastRelevantDate, useIndexedFra));
            }
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, fraHelpers, actual360,
                    BootstrapTraits.Discount, new Linear());
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), curve);
            for (int i = 0; i < FRA_MONTHS.length; i++) {
                Date start = target.advance(settlement, FRA_MONTHS[i], TimeUnit.MONTHS,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true);
                double expected = FRA_RATES[i] / 100.0;
                Date end;
                if (useIndexedFra) {
                    end = euribor3m.maturityDate(start);
                } else {
                    end = target.advance(settlement, 3 + FRA_MONTHS[i], TimeUnit.MONTHS,
                            BusinessDayConvention.MODIFIED_FOLLOWING, true);
                }
                // QuantLib's index-forecasted constructor is used when the
                // helper is indexed; the at-par constructor otherwise.
                ForwardRateAgreement fra = new ForwardRateAgreement(euribor3m, start, end,
                        ForwardRateAgreement.Position.Long, expected, 100.0, curve, useIndexedFra);
                assertEquals(expected, fra.forwardRate(), TOLERANCE,
                        (useIndexedFra ? "indexed" : "at par") + " FRA " + (i + 1));
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testImmFutures() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), flat());
            List<Date> immDates = new ArrayList<>();
            List<RateHelper> futures = new ArrayList<>();
            Date immDate = new Date();
            for (double rate : FUTURES_RATES) {
                immDate = IMM.nextDate(immDate, false);
                if (TimeUtils.less(euribor3m.fixingDate(immDate), today))
                    immDate = IMM.nextDate(immDate, false);
                assertTrue(IMM.isIMMdate(immDate, false));
                immDates.add(immDate);
                futures.add(new FuturesRateHelper(
                        new Handle<>(new SimpleQuote(100.0 - rate), false), immDate, euribor3m,
                        null, Futures.Type.IMM));
            }
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, futures, actual360,
                    BootstrapTraits.Discount, new Linear());
            IborIndex forecast = euribor(new Period(3, TimeUnit.MONTHS), curve);
            for (int i = 0; i < FUTURES_RATES.length; i++) {
                double expected = FUTURES_RATES[i] / 100.0;
                Date start = immDates.get(i);
                ForwardRateAgreement fra = new ForwardRateAgreement(forecast, start,
                        forecast.maturityDate(start), ForwardRateAgreement.Position.Long,
                        expected, 100.0, curve, true);
                assertEquals(expected, fra.forwardRate(), TOLERANCE, "IMM future " + (i + 1));
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testAsxFutures() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS), flat());
            List<Date> asxDates = new ArrayList<>();
            List<Double> rates = new ArrayList<>();
            List<RateHelper> futures = new ArrayList<>();
            Date asxDate = new Date();
            for (double rate : FUTURES_RATES) {
                asxDate = ASX.nextDate(asxDate, false);
                if (TimeUtils.less(euribor3m.fixingDate(asxDate), today))
                    asxDate = ASX.nextDate(asxDate, false);
                if (!target.isBusinessDay(asxDate))
                    continue;
                assertTrue(ASX.isASXdate(asxDate, false));
                asxDates.add(asxDate);
                rates.add(rate);
                futures.add(new FuturesRateHelper(
                        new Handle<>(new SimpleQuote(100.0 - rate), false),
                        asxDate, euribor3m, null, Futures.Type.ASX));
            }
            assertTrue(!futures.isEmpty());
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, futures, actual360,
                    BootstrapTraits.Discount, new Linear());
            IborIndex forecast = euribor(new Period(3, TimeUnit.MONTHS), curve);
            for (int i = 0; i < futures.size(); i++) {
                double expected = rates.get(i) / 100.0;
                Date start = asxDates.get(i);
                ForwardRateAgreement fra = new ForwardRateAgreement(forecast, start,
                        forecast.maturityDate(start), ForwardRateAgreement.Position.Long,
                        expected, 100.0, curve, true);
                assertEquals(expected, fra.forwardRate(), TOLERANCE, "ASX future " + (i + 1));
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testBMACurveConsistency() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            BMAIndex fixingIndex = new BMAIndex();
            USDLibor calendarLibor = new USDLibor(new Period(3, TimeUnit.MONTHS));
            Calendar calendar = new JointCalendar(fixingIndex.fixingCalendar(),
                    calendarLibor.fixingCalendar(), JointCalendar.Rule.JoinHolidays);
            Date today = calendar.adjust(Date.todaysDate(), BusinessDayConvention.FOLLOWING);
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
                    today, helpers, new Actual360(false),
                    BootstrapTraits.Discount, new Linear());
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
