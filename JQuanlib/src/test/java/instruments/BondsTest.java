package instruments;

import jquant.Compounding;
import jquant.Handle;
import jquant.InterestRate;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.CashFlows;
import jquant.cashflows.Duration;
import jquant.indexes.IndexManager;
import jquant.instruments.Bond;
import jquant.instruments.FixedRateBond;
import jquant.instruments.ZeroCouponBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.pricingengines.bond.BondFunctions;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
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
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code bonds.cpp} cases for fixed-coupon and zero-coupon
 * bonds: {@code testCachedZero}, {@code testYield}, {@code testAtmRate},
 * {@code testZspread}, {@code testTheoretical}, {@code testBasisPointValue},
 * {@code testYieldInFinalCouponPeriod} and
 * {@code testThirty360BondWithSettlementOn31st}.
 *
 * <p>The methods whose names start with {@code engineering} are <b>not</b>
 * official BOOST ports; each is labelled in its own Javadoc.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/bonds.cpp
 */
public class BondsTest {

    private SavedSettings savedSettings_;

    @BeforeEach
    public void setUp() {
        savedSettings_ = new SavedSettings();
    }

    @AfterEach
    public void tearDown() {
        // Official TopLevelFixture: clear the index histories, then restore
        // the saved settings.
        IndexManager.Instance.clearHistories();
        savedSettings_.restore();
    }

    /**
     * Official {@code CommonVars}: TARGET calendar, today adjusted on it, the
     * evaluation date set to today, and a one-million face amount.
     */
    private static final class CommonVars {
        final Calendar calendar = new Target();
        final Date today = calendar.adjust(Date.todaysDate(), BusinessDayConvention.FOLLOWING);
        final double faceAmount = 1_000_000.0;

        CommonVars() {
            Settings.instance.evaluationDate().equal(today);
        }
    }

    @Test
    public void testCachedZero() {
        // Official testCachedZero: 22 Nov 2004, US GovernmentBond, face 1e6,
        // ModifiedFollowing, redemption 100, issue 30 Nov 2004, 3% continuous
        // Actual/360 curve. Clean prices are cached.
        CommonVars vars = new CommonVars();

        Date today = new Date(22, Month.NOVEMBER, 2004);
        Settings.instance.evaluationDate().equal(today);

        int settlementDays = 1;

        YieldTermStructure discountCurve = new FlatForward(today, 0.03, new Actual360());
        double tolerance = 1.0e-6;

        Calendar us = new UnitedStates(UnitedStates.Market.GovernmentBond);
        Date issue = new Date(30, Month.NOVEMBER, 2004);
        DiscountingBondEngine bondEngine = new DiscountingBondEngine(discountCurve);

        ZeroCouponBond bond1 = new ZeroCouponBond(
                settlementDays, us, vars.faceAmount,
                new Date(30, Month.NOVEMBER, 2008),
                BusinessDayConvention.MODIFIED_FOLLOWING, 100.0, issue);
        bond1.setPricingEngine(bondEngine);
        assertEquals(88.551726, bond1.cleanPrice(), tolerance,
                "failed to reproduce cached zero-coupon price (2008)");

        ZeroCouponBond bond2 = new ZeroCouponBond(
                settlementDays, us, vars.faceAmount,
                new Date(30, Month.NOVEMBER, 2007),
                BusinessDayConvention.MODIFIED_FOLLOWING, 100.0, issue);
        bond2.setPricingEngine(bondEngine);
        assertEquals(91.278949, bond2.cleanPrice(), tolerance,
                "failed to reproduce cached zero-coupon price (2007)");

        ZeroCouponBond bond3 = new ZeroCouponBond(
                settlementDays, us, vars.faceAmount,
                new Date(30, Month.NOVEMBER, 2006),
                BusinessDayConvention.MODIFIED_FOLLOWING, 100.0, issue);
        bond3.setPricingEngine(bondEngine);
        assertEquals(94.098006, bond3.cleanPrice(), tolerance,
                "failed to reproduce cached zero-coupon price (2006)");
    }

    @Test
    public void testYield() {
        // Official testYield: price/yield consistency for clean and dirty
        // prices over the full official grid. When the recovered yield differs
        // from the input by more than the tolerance the official test only
        // requires the repriced clean (dirty) price to be relatively within
        // the same tolerance.
        CommonVars vars = new CommonVars();

        double tolerance = 1.0e-7;
        int maxEvaluations = 100;

        int[] issueMonths = {-24, -18, -12, -6, 0, 6, 12, 18, 24};
        int[] lengths = {3, 5, 10, 15, 20};
        int settlementDays = 3;
        double[] coupons = {0.02, 0.05, 0.08};
        Frequency[] frequencies = {Frequency.SEMIANNUAL, Frequency.ANNUAL};
        DayCounter bondDayCount = new Thirty360(Thirty360.Convention.BondBasis);
        BusinessDayConvention accrualConvention = BusinessDayConvention.UNADJUSTED;
        BusinessDayConvention paymentConvention = BusinessDayConvention.MODIFIED_FOLLOWING;
        double redemption = 100.0;

        double[] yields = {0.03, 0.04, 0.05, 0.06, 0.07};
        Compounding[] compoundings = {Compounding.Compounded, Compounding.Continuous};

        for (int issueMonth : issueMonths) {
            for (int length : lengths) {
                for (double coupon : coupons) {
                    for (Frequency frequency : frequencies) {
                        for (Compounding compounding : compoundings) {
                            Date dated = vars.calendar.advance(vars.today, issueMonth,
                                    TimeUnit.MONTHS);
                            Date issue = dated.copy();
                            Date maturity = vars.calendar.advance(issue, length,
                                    TimeUnit.YEARS);

                            Schedule schedule = new Schedule(
                                    dated, maturity, new Period(frequency), vars.calendar,
                                    accrualConvention, accrualConvention,
                                    DateGenerationRule.BACKWARD, false,
                                    new Date(), new Date());

                            FixedRateBond bond = new FixedRateBond(
                                    settlementDays, vars.faceAmount, schedule,
                                    new double[] {coupon}, bondDayCount,
                                    paymentConvention, redemption, issue);

                            for (double m : yields) {
                                String context = yieldContext(issue, maturity, coupon,
                                        frequency, m, compounding);

                                double clean = BondFunctions.cleanPrice(
                                        bond, m, bondDayCount, compounding, frequency);
                                Bond.Price price = new Bond.Price(
                                        clean, Bond.Price.Type.CLEAN);

                                double calculated = BondFunctions.yield(
                                        bond, price, bondDayCount, compounding, frequency,
                                        new Date(), tolerance, maxEvaluations, 0.05);

                                if (Math.abs(m - calculated) > tolerance) {
                                    // the difference might not matter
                                    double price2 = BondFunctions.cleanPrice(
                                            bond, calculated, bondDayCount,
                                            compounding, frequency);
                                    if (Math.abs(clean - price2) / clean > tolerance) {
                                        fail("yield recalculation failed:"
                                                + context
                                                + "\n    clean price:  " + clean
                                                + "\n    yield':       " + calculated
                                                + "\n    clean price': " + price2);
                                    }
                                }

                                double dirty = BondFunctions.dirtyPrice(
                                        bond, m, bondDayCount, compounding, frequency);
                                price = new Bond.Price(dirty, Bond.Price.Type.DIRTY);

                                calculated = BondFunctions.yield(
                                        bond, price, bondDayCount, compounding, frequency,
                                        new Date(), tolerance, maxEvaluations, 0.05);

                                if (Math.abs(m - calculated) > tolerance) {
                                    // the difference might not matter
                                    double price2 = BondFunctions.dirtyPrice(
                                            bond, calculated, bondDayCount,
                                            compounding, frequency);
                                    if (Math.abs(dirty - price2) / dirty > tolerance) {
                                        fail("yield recalculation failed:"
                                                + context
                                                + "\n    dirty price:  " + dirty
                                                + "\n    yield':       " + calculated
                                                + "\n    dirty price': " + price2);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testAtmRate() {
        // Official testAtmRate: the ATM rate recovered from the official clean
        // and dirty theoretical prices must be the coupon.
        CommonVars vars = new CommonVars();

        double tolerance = 1.0e-7;

        int[] issueMonths = {-24, -18, -12, -6, 0, 6, 12, 18, 24};
        int[] lengths = {3, 5, 10, 15, 20};
        int settlementDays = 3;
        double[] coupons = {0.02, 0.05, 0.08};
        Frequency[] frequencies = {Frequency.SEMIANNUAL, Frequency.ANNUAL};
        DayCounter bondDayCount = new Thirty360(Thirty360.Convention.BondBasis);
        BusinessDayConvention accrualConvention = BusinessDayConvention.UNADJUSTED;
        BusinessDayConvention paymentConvention = BusinessDayConvention.MODIFIED_FOLLOWING;
        double redemption = 100.0;
        YieldTermStructure disc = new FlatForward(vars.today, 0.03, new Actual360());
        DiscountingBondEngine bondEngine = new DiscountingBondEngine(disc);

        for (int issueMonth : issueMonths) {
            for (int length : lengths) {
                for (double coupon : coupons) {
                    for (Frequency frequency : frequencies) {
                        Date dated = vars.calendar.advance(vars.today, issueMonth,
                                TimeUnit.MONTHS);
                        Date issue = dated.copy();
                        Date maturity = vars.calendar.advance(issue, length,
                                TimeUnit.YEARS);

                        Schedule schedule = new Schedule(
                                dated, maturity, new Period(frequency), vars.calendar,
                                accrualConvention, accrualConvention,
                                DateGenerationRule.BACKWARD, false,
                                new Date(), new Date());

                        FixedRateBond bond = new FixedRateBond(
                                settlementDays, vars.faceAmount, schedule,
                                new double[] {coupon}, bondDayCount,
                                paymentConvention, redemption, issue);
                        bond.setPricingEngine(bondEngine);

                        Bond.Price price = new Bond.Price(
                                bond.cleanPrice(), Bond.Price.Type.CLEAN);
                        double calculated = BondFunctions.atmRate(
                                bond, disc, bond.settlementDate(), price);
                        assertEquals(coupon, calculated, tolerance,
                                atmContext(vars.today, bond.settlementDate(), issue, maturity,
                                        coupon, frequency, "clean price", price.amount(),
                                        calculated));

                        price = new Bond.Price(
                                bond.dirtyPrice(), Bond.Price.Type.DIRTY);
                        calculated = BondFunctions.atmRate(
                                bond, disc, bond.settlementDate(), price);
                        assertEquals(coupon, calculated, tolerance,
                                atmContext(vars.today, bond.settlementDate(), issue, maturity,
                                        coupon, frequency, "dirty price", price.amount(),
                                        calculated));
                    }
                }
            }
        }
    }

    @Test
    public void testZspread() {
        // Official testZspread: price/z-spread consistency for clean and dirty
        // prices over the full official grid, with the official relative-price
        // fallback.
        CommonVars vars = new CommonVars();

        double tolerance = 1.0e-7;
        int maxEvaluations = 100;

        YieldTermStructure discountCurve = new FlatForward(vars.today, 0.03, new Actual360());

        int[] issueMonths = {-24, -18, -12, -6, 0, 6, 12, 18, 24};
        int[] lengths = {3, 5, 10, 15, 20};
        int settlementDays = 3;
        double[] coupons = {0.02, 0.05, 0.08};
        Frequency[] frequencies = {Frequency.SEMIANNUAL, Frequency.ANNUAL};
        DayCounter bondDayCount = new Thirty360(Thirty360.Convention.BondBasis);
        BusinessDayConvention accrualConvention = BusinessDayConvention.UNADJUSTED;
        BusinessDayConvention paymentConvention = BusinessDayConvention.MODIFIED_FOLLOWING;
        double redemption = 100.0;

        double[] spreads = {-0.01, -0.005, 0.0, 0.005, 0.01};
        Compounding[] compoundings = {Compounding.Compounded, Compounding.Continuous};

        for (int issueMonth : issueMonths) {
            for (int length : lengths) {
                for (double coupon : coupons) {
                    for (Frequency frequency : frequencies) {
                        for (Compounding compounding : compoundings) {
                            Date dated = vars.calendar.advance(vars.today, issueMonth,
                                    TimeUnit.MONTHS);
                            Date issue = dated.copy();
                            Date maturity = vars.calendar.advance(issue, length,
                                    TimeUnit.YEARS);

                            Schedule schedule = new Schedule(
                                    dated, maturity, new Period(frequency), vars.calendar,
                                    accrualConvention, accrualConvention,
                                    DateGenerationRule.BACKWARD, false,
                                    new Date(), new Date());

                            FixedRateBond bond = new FixedRateBond(
                                    settlementDays, vars.faceAmount, schedule,
                                    new double[] {coupon}, bondDayCount,
                                    paymentConvention, redemption, issue);

                            for (double spread : spreads) {
                                String context = spreadContext(issue, maturity, coupon,
                                        frequency, spread, compounding);

                                // Clean price
                                double clean = BondFunctions.cleanPrice(
                                        bond, discountCurve, spread, compounding, frequency);
                                Bond.Price price = new Bond.Price(
                                        clean, Bond.Price.Type.CLEAN);
                                double calculated = BondFunctions.zSpread(
                                        bond, price, discountCurve, compounding, frequency,
                                        new Date(), tolerance, maxEvaluations);

                                if (Math.abs(spread - calculated) > tolerance) {
                                    // the difference might not matter
                                    double price2 = BondFunctions.cleanPrice(
                                            bond, discountCurve, calculated,
                                            compounding, frequency);
                                    if (Math.abs(clean - price2) / clean > tolerance) {
                                        fail("Z-spread recalculation failed:"
                                                + context
                                                + "\n    clean price:  " + clean
                                                + "\n    Z-spread':    " + calculated
                                                + "\n    clean price': " + price2);
                                    }
                                }

                                // Dirty price
                                double dirty = BondFunctions.dirtyPrice(
                                        bond, discountCurve, spread, compounding, frequency);
                                price = new Bond.Price(dirty, Bond.Price.Type.DIRTY);
                                calculated = BondFunctions.zSpread(
                                        bond, price, discountCurve, compounding, frequency,
                                        new Date(), tolerance, maxEvaluations);

                                if (Math.abs(spread - calculated) > tolerance) {
                                    // the difference might not matter
                                    double price2 = BondFunctions.dirtyPrice(
                                            bond, discountCurve, calculated,
                                            compounding, frequency);
                                    if (Math.abs(dirty - price2) / dirty > tolerance) {
                                        fail("Z-spread recalculation failed:"
                                                + context
                                                + "\n    dirty price:  " + dirty
                                                + "\n    Z-spread':    " + calculated
                                                + "\n    dirty price': " + price2);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testTheoretical() {
        // Official testTheoretical: a quote-driven 3M curve updated for every
        // yield; BondFunctions prices must match the engine prices and the
        // yield must be recovered.
        CommonVars vars = new CommonVars();

        double tolerance = 1.0e-7;
        int maxEvaluations = 100;

        int[] lengths = {3, 5, 10, 15, 20};
        int settlementDays = 3;
        double[] coupons = {0.02, 0.05, 0.08};
        Frequency[] frequencies = {Frequency.SEMIANNUAL, Frequency.ANNUAL};
        DayCounter bondDayCount = new Actual360();
        BusinessDayConvention accrualConvention = BusinessDayConvention.UNADJUSTED;
        BusinessDayConvention paymentConvention = BusinessDayConvention.MODIFIED_FOLLOWING;
        double redemption = 100.0;

        double[] yields = {0.03, 0.04, 0.05, 0.06, 0.07};

        for (int length : lengths) {
            for (double coupon : coupons) {
                for (Frequency frequency : frequencies) {
                    Date dated = vars.today.copy();
                    Date issue = dated.copy();
                    Date maturity = vars.calendar.advance(issue, length, TimeUnit.YEARS);

                    SimpleQuote rate = new SimpleQuote(0.0);
                    YieldTermStructure discountCurve = new FlatForward(
                            vars.today, new Handle<>(rate, true), bondDayCount);

                    Schedule schedule = new Schedule(
                            dated, maturity, new Period(frequency), vars.calendar,
                            accrualConvention, accrualConvention,
                            DateGenerationRule.BACKWARD, false,
                            new Date(), new Date());

                    FixedRateBond bond = new FixedRateBond(
                            settlementDays, vars.faceAmount, schedule,
                            new double[] {coupon}, bondDayCount, paymentConvention,
                            redemption, issue);
                    bond.setPricingEngine(new DiscountingBondEngine(discountCurve));

                    for (double m : yields) {
                        double previous = rate.value();
                        try {
                        rate.setValue(m);
                        String context = theoreticalContext(issue, maturity, coupon,
                                frequency, m);

                        // Test yield vs clean price
                        double price = BondFunctions.cleanPrice(
                                bond, m, bondDayCount, Compounding.Continuous, frequency);
                        double calculatedPrice = bond.cleanPrice();
                        assertEquals(price, calculatedPrice, tolerance,
                                "price calculation failed:" + context
                                        + "\n    expected:    " + price
                                        + "\n    calculated': " + calculatedPrice);

                        double calculatedYield = BondFunctions.yield(
                                bond, new Bond.Price(calculatedPrice, Bond.Price.Type.CLEAN),
                                bondDayCount, Compounding.Continuous, frequency,
                                bond.settlementDate(), tolerance, maxEvaluations);
                        assertEquals(m, calculatedYield, tolerance,
                                "yield calculation failed:" + context
                                        + "\n    clean price: " + price
                                        + "\n    yield':      " + calculatedYield);

                        // Test yield vs dirty price
                        price = BondFunctions.dirtyPrice(
                                bond, m, bondDayCount, Compounding.Continuous, frequency);
                        calculatedPrice = bond.dirtyPrice();
                        assertEquals(price, calculatedPrice, tolerance,
                                "price calculation failed:" + context
                                        + "\n    expected:    " + price
                                        + "\n    calculated': " + calculatedPrice);

                        calculatedYield = BondFunctions.yield(
                                bond, new Bond.Price(calculatedPrice, Bond.Price.Type.DIRTY),
                                bondDayCount, Compounding.Continuous, frequency,
                                bond.settlementDate(), tolerance, maxEvaluations, 0.05);
                        assertEquals(m, calculatedYield, tolerance,
                                "yield calculation failed:" + context
                                        + "\n    dirty price: " + price
                                        + "\n    yield':      " + calculatedYield);
                        } finally {
                            rate.setValue(previous);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testBasisPointValue() {
        // Official testBasisPointValue: fixed today of 29 Jan 2024, not
        // CommonVars' todaysDate.
        CommonVars vars = new CommonVars();

        Date today = new Date(29, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        Date datedDate = new Date(15, Month.NOVEMBER, 2023);
        Date maturity = new Date(15, Month.AUGUST, 2033);

        DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        Compounding compounding = Compounding.Compounded;
        Frequency frequency = Frequency.SEMIANNUAL;
        Period period = new Period(frequency);

        Schedule fixedBondSchedule = new Schedule(
                datedDate, maturity, period,
                new UnitedStates(UnitedStates.Market.GovernmentBond),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());

        FixedRateBond fixedRateBond = new FixedRateBond(
                1, vars.faceAmount, fixedBondSchedule,
                new double[] {0.045}, dayCounter,
                BusinessDayConvention.UNADJUSTED, 100.0);

        Date defaultSettlement = fixedRateBond.settlementDate();
        Bond.Price cleanPrice = new Bond.Price(102.890625, Bond.Price.Type.CLEAN);

        double tolerance = 1e-6;
        double bpvTolerance = 1e-3;

        double y = BondFunctions.yield(fixedRateBond, cleanPrice, dayCounter,
                compounding, frequency);
        assertEquals(0.041301, y, tolerance,
                "yield at default settlement " + defaultSettlement);

        Date[] settlements = {new Date(), defaultSettlement,
                new Date(12, Month.FEBRUARY, 2024)};
        double[] bpvs = {-795.098043, -795.098043, -792.789400};
        double[] yvbps = {-0.0012571287, -0.0012571287, -0.0012607913};

        for (int i = 0; i < settlements.length; i++) {
            Date settlement = settlements[i];

            double bpv1 = BondFunctions.basisPointValue(fixedRateBond, y, dayCounter,
                    compounding, frequency, settlement);
            assertEquals(bpvs[i], bpv1, tolerance,
                    "basisPointValue from yield at settlement " + settlement);
            double bpv2 = BondFunctions.basisPointValue(fixedRateBond,
                    new InterestRate(y, dayCounter, compounding, frequency), settlement);
            assertEquals(bpvs[i], bpv2, tolerance,
                    "basisPointValue from InterestRate at settlement " + settlement);

            double cleanPrice1 = BondFunctions.cleanPrice(fixedRateBond, y, dayCounter,
                    compounding, frequency, settlement);
            double cleanPrice2 = BondFunctions.cleanPrice(fixedRateBond, y + 0.0001,
                    dayCounter, compounding, frequency, settlement);
            double npvChange = (cleanPrice2 - cleanPrice1) * vars.faceAmount / 100.0;
            assertEquals(bpv1, npvChange, bpvTolerance,
                    "basisPointValue against 1bp reprice at settlement " + settlement);

            double yvbp1 = BondFunctions.yieldValueBasisPoint(fixedRateBond, y, dayCounter,
                    compounding, frequency, settlement);
            yvbp1 *= vars.faceAmount;
            assertEquals(yvbps[i], yvbp1, tolerance,
                    "yieldValueBasisPoint from yield at settlement " + settlement);
            double yvbp2 = BondFunctions.yieldValueBasisPoint(fixedRateBond,
                    new InterestRate(y, dayCounter, compounding, frequency), settlement);
            yvbp2 *= vars.faceAmount;
            assertEquals(yvbps[i], yvbp2, tolerance,
                    "yieldValueBasisPoint from InterestRate at settlement " + settlement);
        }
    }

    @Test
    public void testYieldInFinalCouponPeriod() {
        // Official testYieldInFinalCouponPeriod: the bond settles inside its
        // final coupon period, so SimpleThenCompounded and CompoundedThenSimple
        // must agree on the simple yield.
        Date issue = new Date(1, Month.JUNE, 2016);
        Date maturity = new Date(1, Month.JUNE, 2026);
        Date settlement = new Date(5, Month.MARCH, 2026);
        double couponRate = 0.02125;
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA);

        Schedule schedule = new Schedule(
                issue, maturity, new Period(Frequency.SEMIANNUAL), new NullCalendar(),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());

        FixedRateBond bond = new FixedRateBond(
                0, 100.0, schedule, new double[] {couponRate},
                dayCounter, BusinessDayConvention.UNADJUSTED, 100.0, issue);

        Bond.Price cleanPrice = new Bond.Price(100.185, Bond.Price.Type.CLEAN);

        double yieldSTC = BondFunctions.yield(bond, cleanPrice, dayCounter,
                Compounding.SimpleThenCompounded, Frequency.SEMIANNUAL, settlement);
        assertEquals(0.013498678862, yieldSTC, 1e-9,
                "simple-then-compounded yield at " + settlement);

        double yieldCTS = BondFunctions.yield(bond, cleanPrice, dayCounter,
                Compounding.CompoundedThenSimple, Frequency.SEMIANNUAL, settlement);
        assertEquals(0.013498678862, yieldCTS, 1e-9,
                "compounded-then-simple yield at " + settlement);
    }

    @Test
    public void testThirty360BondWithSettlementOn31st() {
        // Official bonds.cpp testThirty360BondWithSettlementOn31st. cusip
        // 3130A0X70; the data is from Bloomberg.
        Settings.instance.evaluationDate().equal(new Date(28, Month.JULY, 2017));

        Date datedDate = new Date(13, Month.FEBRUARY, 2014);
        Date settlement = new Date(31, Month.JULY, 2017);
        Date maturity = new Date(13, Month.AUGUST, 2018);

        DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        Compounding compounding = Compounding.Compounded;

        Schedule fixedBondSchedule = new Schedule(
                datedDate, maturity, new Period(Frequency.SEMIANNUAL),
                new UnitedStates(UnitedStates.Market.GovernmentBond),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());

        FixedRateBond fixedRateBond = new FixedRateBond(
                1, 100.0, fixedBondSchedule, new double[] {0.015},
                dayCounter, BusinessDayConvention.UNADJUSTED, 100.0);

        Bond.Price cleanPrice = new Bond.Price(100.0, Bond.Price.Type.CLEAN);

        double y = BondFunctions.yield(fixedRateBond, cleanPrice, dayCounter,
                compounding, Frequency.SEMIANNUAL, settlement);
        assertEquals(0.015, y, 1e-4, "yield at " + settlement);

        double duration = BondFunctions.duration(fixedRateBond,
                new InterestRate(y, dayCounter, compounding, Frequency.SEMIANNUAL),
                Duration.Type.Macaulay, settlement);
        assertEquals(1.022, duration, 1e-3, "duration at " + settlement);

        double convexity = BondFunctions.convexity(fixedRateBond,
                new InterestRate(y, dayCounter, compounding, Frequency.SEMIANNUAL),
                settlement) / 100;
        assertEquals(0.015, convexity, 1e-3, "convexity at " + settlement);

        double accrued = BondFunctions.accruedAmount(fixedRateBond, settlement);
        assertEquals(0.7, accrued, 1e-6, "accrued at " + settlement);
    }

    /**
     * Engineering regression, <b>not</b> an official BOOST port. Proves that
     * the BondFunctions duration/convexity adapters are the CashFlows values
     * on the same cash flows, with the settlement-date flows excluded and the
     * settlement date as npv date: there is no accidental 100/notional scale.
     * The default duration type is Modified, and the empty settlement date
     * resolves to {@code bond.settlementDate()}.
     */
    @Test
    public void engineeringDurationAndConvexityAdapterIdentity() {
        Settings.instance.evaluationDate().equal(new Date(28, Month.JULY, 2017));
        DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        FixedRateBond bond = thirty360Bond(dayCounter);
        Date settlement = bond.settlementDate();

        InterestRate rate = new InterestRate(0.015, dayCounter,
                Compounding.Compounded, Frequency.SEMIANNUAL);
        for (Duration.Type type : new Duration.Type[] {
                Duration.Type.Simple, Duration.Type.Macaulay, Duration.Type.Modified}) {
            double expected = CashFlows.duration(bond.cashflows(), rate, type,
                    false, settlement, settlement);
            assertEquals(expected,
                    BondFunctions.duration(bond, rate, type, settlement),
                    0.0, "duration " + type + " at " + settlement);
            assertEquals(expected,
                    BondFunctions.duration(bond, rate, type),
                    0.0, "duration " + type + " at default settlement");
            assertEquals(expected,
                    BondFunctions.duration(bond, 0.015, dayCounter,
                            Compounding.Compounded, Frequency.SEMIANNUAL, type, settlement),
                    0.0, "duration " + type + " from rate at " + settlement);
        }
        assertEquals(BondFunctions.duration(bond, rate, Duration.Type.Modified, settlement),
                BondFunctions.duration(bond, rate),
                0.0, "duration default type is Modified");
        assertEquals(BondFunctions.duration(bond, rate, Duration.Type.Modified, settlement),
                BondFunctions.duration(bond, 0.015, dayCounter,
                        Compounding.Compounded, Frequency.SEMIANNUAL),
                0.0, "duration from rate default type is Modified");

        double expectedConvexity = CashFlows.convexity(bond.cashflows(), rate,
                false, settlement, settlement);
        assertEquals(expectedConvexity,
                BondFunctions.convexity(bond, rate, settlement),
                0.0, "convexity at " + settlement);
        assertEquals(expectedConvexity,
                BondFunctions.convexity(bond, rate),
                0.0, "convexity at default settlement");
        assertEquals(expectedConvexity,
                BondFunctions.convexity(bond, 0.015, dayCounter,
                        Compounding.Compounded, Frequency.SEMIANNUAL, settlement),
                0.0, "convexity from rate at " + settlement);
    }

    /**
     * Engineering regression, <b>not</b> an official BOOST port. Reuses the
     * official {@code checkSensitivities} central finite-difference formula
     * (shift 1.0e-4; Modified duration tol 1e-7, convexity tol 1e-5) on the
     * already-ported FinalCouponPeriodVars bond, at the official yield
     * 0.013498678862 and settlement 5 Mar 2026, for both compounding
     * conventions used by official testYieldInFinalCouponPeriod.
     */
    @Test
    public void engineeringCheckSensitivitiesMatchesFiniteDifferences() {
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA);
        FixedRateBond bond = finalCouponBond(dayCounter);
        Date settlement = new Date(5, Month.MARCH, 2026);
        double rate = 0.013498678862;

        engineeringCheckSensitivities(bond, dayCounter, settlement, rate,
                Compounding.SimpleThenCompounded, "SimpleThenCompounded");
        engineeringCheckSensitivities(bond, dayCounter, settlement, rate,
                Compounding.CompoundedThenSimple, "CompoundedThenSimple");
    }

    /**
     * Engineering regression, <b>not</b> an official BOOST port. The
     * BondFunctions duration adapter must pass the official CashFlows
     * requirement through: a Macaulay duration with a continuously
     * compounded rate throws "compounded rate required".
     */
    @Test
    public void engineeringMacaulayRequiresCompoundedRate() {
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA);
        FixedRateBond bond = finalCouponBond(dayCounter);
        Date settlement = new Date(5, Month.MARCH, 2026);
        InterestRate continuous = new InterestRate(0.013498678862, dayCounter,
                Compounding.Continuous, Frequency.SEMIANNUAL);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> BondFunctions.duration(bond, continuous,
                        Duration.Type.Macaulay, settlement));
        assertTrue(exception.getMessage().contains("compounded rate required"),
                exception.getMessage());
    }

    /**
     * Engineering regression, <b>not</b> an official BOOST port. A settlement
     * after the final cash flow is non-tradable and must raise the official
     * "non tradable at ... (maturity being ...)" message from both adapters.
     */
    @Test
    public void engineeringDurationRejectsNonTradableSettlement() {
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA);
        FixedRateBond bond = finalCouponBond(dayCounter);
        Date settlement = new Date(2, Month.JUNE, 2026);
        InterestRate rate = new InterestRate(0.013498678862, dayCounter,
                Compounding.Compounded, Frequency.SEMIANNUAL);

        IllegalArgumentException durationException = assertThrows(IllegalArgumentException.class,
                () -> BondFunctions.duration(bond, rate,
                        Duration.Type.Modified, settlement));
        assertTrue(durationException.getMessage().contains("non tradable at"),
                durationException.getMessage());
        assertTrue(durationException.getMessage().contains("(maturity being"),
                durationException.getMessage());

        IllegalArgumentException convexityException = assertThrows(IllegalArgumentException.class,
                () -> BondFunctions.convexity(bond, rate, settlement));
        assertTrue(convexityException.getMessage().contains("non tradable at"),
                convexityException.getMessage());
    }

    // ------------------------------------------------------------------
    // Engineering helpers
    // ------------------------------------------------------------------

    /** Official Thirty/360 test bond: 1.5% semiannual, 13 Feb 2014-13 Aug 2018. */
    private static FixedRateBond thirty360Bond(DayCounter dayCounter) {
        Date datedDate = new Date(13, Month.FEBRUARY, 2014);
        Date maturity = new Date(13, Month.AUGUST, 2018);
        Schedule schedule = new Schedule(
                datedDate, maturity, new Period(Frequency.SEMIANNUAL),
                new UnitedStates(UnitedStates.Market.GovernmentBond),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
        return new FixedRateBond(
                1, 100.0, schedule, new double[] {0.015},
                dayCounter, BusinessDayConvention.UNADJUSTED, 100.0);
    }

    /** Official FinalCouponPeriodVars bond: 2.125% semiannual, 2016-2026. */
    private static FixedRateBond finalCouponBond(DayCounter dayCounter) {
        Date issue = new Date(1, Month.JUNE, 2016);
        Date maturity = new Date(1, Month.JUNE, 2026);
        Schedule schedule = new Schedule(
                issue, maturity, new Period(Frequency.SEMIANNUAL), new NullCalendar(),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        return new FixedRateBond(
                0, 100.0, schedule, new double[] {0.02125},
                dayCounter, BusinessDayConvention.UNADJUSTED, 100.0, issue);
    }

    private static void engineeringCheckSensitivities(Bond bond,
                                                      DayCounter dayCounter,
                                                      Date settlement,
                                                      double rate,
                                                      Compounding compounding,
                                                      String convention) {
        final double shift = 1.0e-4;
        double price = BondFunctions.dirtyPrice(bond,
                new InterestRate(rate, dayCounter, compounding, Frequency.SEMIANNUAL),
                settlement);
        double priceUp = BondFunctions.dirtyPrice(bond,
                new InterestRate(rate + shift, dayCounter, compounding,
                        Frequency.SEMIANNUAL), settlement);
        double priceDown = BondFunctions.dirtyPrice(bond,
                new InterestRate(rate - shift, dayCounter, compounding,
                        Frequency.SEMIANNUAL), settlement);
        double expectedDuration = -(priceUp - priceDown) / (2.0 * shift * price);
        double expectedConvexity = (priceUp - 2.0 * price + priceDown)
                / (shift * shift * price);

        InterestRate yield = new InterestRate(rate, dayCounter, compounding,
                Frequency.SEMIANNUAL);
        double duration = BondFunctions.duration(bond, yield,
                Duration.Type.Modified, settlement);
        assertEquals(expectedDuration, duration, 1e-7,
                convention + " modified duration at " + settlement);

        double convexity = BondFunctions.convexity(bond, yield, settlement);
        assertEquals(expectedConvexity, convexity, 1e-5,
                convention + " convexity at " + settlement);
    }

    // ------------------------------------------------------------------
    // Failure-message helpers (only used when an official check fails)
    // ------------------------------------------------------------------

    private static String yieldContext(Date issue, Date maturity, double coupon,
                                       Frequency frequency, double y,
                                       Compounding compounding) {
        return "\n    issue:        " + issue
                + "\n    maturity:     " + maturity
                + "\n    coupon:       " + coupon
                + "\n    frequency:    " + frequency
                + "\n    yield:        " + y
                + (compounding == Compounding.Compounded ? " compounded" : " continuous");
    }

    private static String spreadContext(Date issue, Date maturity, double coupon,
                                        Frequency frequency, double spread,
                                        Compounding compounding) {
        return "\n    issue:        " + issue
                + "\n    maturity:     " + maturity
                + "\n    coupon:       " + coupon
                + "\n    frequency:    " + frequency
                + "\n    Z-spread:     " + spread
                + (compounding == Compounding.Compounded ? " compounded" : " continuous");
    }

    private static String atmContext(Date today, Date settlement, Date issue, Date maturity,
                                     double coupon, Frequency frequency, String priceType,
                                     double price, double calculated) {
        return "\natm rate recalculation failed:"
                + "\n today:           " + today
                + "\n settlement date: " + settlement
                + "\n issue:           " + issue
                + "\n maturity:        " + maturity
                + "\n coupon:          " + coupon
                + "\n frequency:       " + frequency
                + "\n " + priceType + ":     " + price
                + "\n atm rate:        " + calculated;
    }

    private static String theoreticalContext(Date issue, Date maturity, double coupon,
                                             Frequency frequency, double y) {
        return "\n    issue:       " + issue
                + "\n    maturity:    " + maturity
                + "\n    coupon:      " + coupon
                + "\n    frequency:   " + frequency
                + "\n    yield:       " + y;
    }
}
