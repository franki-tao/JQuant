package instruments;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.cashflows.FixedRateCoupon;
import jquant.instruments.FixedRateBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FixedRateBondHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
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
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code testCachedFixed} and the bond leg of
 * {@code testLogLinearDiscountConsistency}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/bonds.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 */
public class FixedRateBondTest {
    @Test
    public void testCachedUsGovernmentBond() {
        // testCachedFixed: 22 Nov 2004, US GovernmentBond, semiannual,
        // unadjusted, end-of-month false, Actual/Actual (ISMA),
        // discount 3% continuous Actual/360. Clean price is cached.
        Date today = new Date(22, Month.NOVEMBER, 2004);
        Settings.instance.evaluationDate().equal(today);
        Calendar us = new UnitedStates(UnitedStates.Market.GovernmentBond);
        Schedule schedule = new Schedule(
                new Date(30, Month.NOVEMBER, 2004),
                new Date(30, Month.NOVEMBER, 2008),
                new Period(Frequency.SEMIANNUAL),
                us,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD,
                false,
                new Date(),
                new Date());
        double face = 1_000_000.0;
        FixedRateBond bond = new FixedRateBond(
                1, face, schedule, new double[] {0.02875},
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                100.0, new Date(30, Month.NOVEMBER, 2004));
        bond.setPricingEngine(new DiscountingBondEngine(
                new FlatForward(today, 0.03, new Actual360(false),
                        Compounding.Continuous, Frequency.ANNUAL)));

        assertTrue(TimeUtils.equals(bond.settlementDate(), new Date(30, Month.NOVEMBER, 2004)));
        assertEquals(0.0, bond.accruedAmount(bond.settlementDate()), 0.0);
        for (int i = 0; i < bond.cashflows().size() - 1; i++) {
            FixedRateCoupon coupon = (FixedRateCoupon) bond.cashflows().get(i);
            assertEquals(face * 0.02875 * 0.5, coupon.amount(), 1.0e-10);
        }
        assertEquals(99.298100, bond.cleanPrice(), 1.0e-6);
        assertEquals(bond.cleanPrice(), bond.dirtyPrice(), 0.0);

        FixedRateBond varying = new FixedRateBond(
                1, face, schedule, new double[] {0.02875, 0.03, 0.03125, 0.0325},
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                100.0, new Date(30, Month.NOVEMBER, 2004));
        varying.setPricingEngine(new DiscountingBondEngine(
                new FlatForward(today, 0.03, new Actual360(false),
                        Compounding.Continuous, Frequency.ANNUAL)));
        assertEquals(100.334149, varying.cleanPrice(), 1.0e-6);
    }

    @Test
    public void testParBondWhenCouponMatchesCompoundedCurve() {
        // Same percentage, same Thirty/360 semiannual compounding. Settlement
        // is a coupon date, so the clean price is par.
        Date today = new Date(15, Month.JANUARY, 2025);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Thirty360(Thirty360.Convention.BondBasis);
        Schedule schedule = new Schedule(
                today, new Date(15, Month.JANUARY, 2027),
                new Period(Frequency.SEMIANNUAL),
                new NullCalendar(),
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD,
                false,
                new Date(),
                new Date());
        FixedRateBond bond = new FixedRateBond(
                0, 100.0, schedule, new double[] {0.05}, dc,
                BusinessDayConvention.UNADJUSTED, 100.0, today);
        bond.setPricingEngine(new DiscountingBondEngine(
                new FlatForward(today, 0.05, dc, Compounding.Compounded, Frequency.SEMIANNUAL)));
        assertEquals(0.0, bond.accruedAmount(bond.settlementDate()), 0.0);
        assertEquals(100.0, bond.cleanPrice(), 1.0e-10);
    }

    @Test
    public void testUnadjustedActual365QuarterlySchedule() {
        Date start = new Date(15, Month.JANUARY, 2025);
        Settings.instance.evaluationDate().equal(start);
        Schedule schedule = new Schedule(
                start, new Date(15, Month.JANUARY, 2027),
                new Period(Frequency.QUARTERLY),
                new NullCalendar(),
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD,
                false,
                new Date(),
                new Date());
        assertEquals(9, schedule.size());
        assertEquals(15, schedule.date(1).dayOfMonth());
        assertEquals(Month.APRIL, schedule.date(1).month());
        assertEquals(15, schedule.date(3).dayOfMonth());
        assertEquals(Month.OCTOBER, schedule.date(3).month());
        assertTrue(!schedule.endOfMonth());

        DayCounter dc = new Actual365Fixed();
        double rate = 0.04;
        FixedRateBond bond = new FixedRateBond(
                0, 100.0, schedule, new double[] {rate}, dc,
                BusinessDayConvention.UNADJUSTED, 100.0, start);
        assertEquals(8, bond.cashflows().size() - 1);
        for (int i = 0; i < 8; i++) {
            FixedRateCoupon coupon = (FixedRateCoupon) bond.cashflows().get(i);
            double yearFraction = dc.yearFraction(schedule.date(i), schedule.date(i + 1),
                    new Date(), new Date());
            assertEquals(100.0 * rate * yearFraction, coupon.amount(), 1.0e-12);
        }
    }

    @Test
    public void testBondHelpersRepriceOnLogLinearCurve() {
        // One Euribor deposit plus two bonds from bondData[]. The pillar is
        // the redemption date, which is also the final coupon date.
        Date today = new Date(15, Month.JANUARY, 2025);
        Settings.instance.evaluationDate().equal(today);
        Calendar target = new Target();
        Date curveSettlement = target.advance(today, 2, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        DayCounter depositDayCounter = new Actual360(false);
        SimpleQuote depositQuote = new SimpleQuote(0.04559);
        RateHelper deposit = new DepositRateHelper(
                new Handle<>(depositQuote, true),
                new Period(1, TimeUnit.WEEKS),
                2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, depositDayCounter);

        SimpleQuote price2y = new SimpleQuote(105.650);
        SimpleQuote price5y = new SimpleQuote(113.610);
        FixedRateBondHelper bond2y = bondHelper(target, today, 2, TimeUnit.YEARS, 5, 0.05, price2y);
        FixedRateBondHelper bond5y = bondHelper(target, today, 5, TimeUnit.YEARS, 11, 0.055, price5y);
        assertTrue(bond5y.bond().cashflows().size() > 6);
        assertTrue(TimeUtils.equals(bond2y.pillarDate(), bond2y.latestRelevantDate()));
        assertTrue(TimeUtils.equals(bond5y.pillarDate(),
                bond5y.bond().cashflows().get(bond5y.bond().cashflows().size() - 1).date()));

        PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                curveSettlement, List.of(deposit, bond2y, bond5y), new Actual360(false));
        assertEquals(0.04559, deposit.impliedQuote(), 1.0e-9);
        assertEquals(105.650, bond2y.impliedQuote(), 1.0e-9);
        assertEquals(113.610, bond5y.impliedQuote(), 1.0e-9);
        assertEquals(0.04559, depositQuote.value(), 0.0);
        assertEquals(105.650, price2y.value(), 0.0);
        assertEquals(113.610, price5y.value(), 0.0);
        assertEquals(4, curve.dates().size());
        assertTrue(TimeUtils.equals(curve.dates().get(0), curveSettlement));
        assertTrue(TimeUtils.equals(curve.dates().get(2), bond2y.pillarDate()));
        assertTrue(TimeUtils.equals(curve.dates().get(3), bond5y.pillarDate()));
    }

    @Test
    public void testAccruedAcrossCouponAndPastCashFlowExcluded() {
        // Semiannual 5% Thirty/360 from 15 Jan 2025 to 15 Jan 2027. A 30/360
        // half-year is 180 days. 15 Jan to 20 Apr is 95 days, so the accrued
        // interest is the semiannual coupon times 95/180, which is
        // face * 0.05 * 95/360. QuantLib does not multiply the annual rate
        // by 95/180.
        Date issue = new Date(15, Month.JANUARY, 2025);
        Date maturity = new Date(15, Month.JANUARY, 2027);
        Date savedDate = Settings.instance.evaluationDate().Date().copy();
        boolean savedInclude = Settings.instance.includeReferenceDateEvents();
        try {
            Settings.instance.setIncludeReferenceDateEvents(false);
            Settings.instance.evaluationDate().equal(issue);
            DayCounter dc = new Thirty360(Thirty360.Convention.BondBasis);
            Schedule schedule = new Schedule(
                    issue, maturity, new Period(Frequency.SEMIANNUAL), new NullCalendar(),
                    BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                    DateGenerationRule.BACKWARD, false, new Date(), new Date());
            FixedRateBond bond = new FixedRateBond(
                    0, 100.0, schedule, new double[] {0.05}, dc,
                    BusinessDayConvention.UNADJUSTED, 100.0, issue);
            YieldTermStructure curve = new FlatForward(
                    issue, 0.05, dc, Compounding.Compounded, Frequency.SEMIANNUAL);
            bond.setPricingEngine(new DiscountingBondEngine(curve));

            Date firstEnd = new Date(15, Month.JULY, 2025);
            FixedRateCoupon first = (FixedRateCoupon) bond.cashflows().get(0);
            assertTrue(TimeUtils.equals(first.referencePeriodStart(), issue));
            assertTrue(TimeUtils.equals(first.referencePeriodEnd(), firstEnd));
            assertEquals(180, dc.dayCount(issue, firstEnd));
            assertEquals(100.0 * 0.05 * 0.5, first.amount(), 1.0e-12);

            Date april15 = new Date(15, Month.APRIL, 2025);
            Date april20 = new Date(20, Month.APRIL, 2025);
            assertEquals(90, dc.dayCount(issue, april15));
            assertEquals(95, dc.dayCount(issue, april20));
            double accruedApril15 = 100.0 * 0.05 * (90.0 / 360.0);
            double accruedApril20 = 100.0 * 0.05 * (180.0 / 360.0) * (95.0 / 180.0);
            assertEquals(accruedApril15, accruedOf(first, april15), 1.0e-12);
            assertEquals(accruedApril20, accruedOf(first, april20), 1.0e-12);

            Settings.instance.evaluationDate().equal(april15);
            assertAccrual(bond, curve, april15, 90, 180);
            Settings.instance.evaluationDate().equal(april20);
            assertAccrual(bond, curve, april20, 95, 180);
            assertTrue(Math.abs(bond.cleanPrice() - 100.0) > 1.0e-4);
            assertEquals(invoice(bond, curve, april20), bond.settlementValue(), 1.0e-12);

            Date beforeIssue = new Date(10, Month.JANUARY, 2025);
            assertEquals(0.0, bond.accruedAmount(beforeIssue), 0.0);
            assertTrue(bond.accruedAmount(beforeIssue) >= 0.0);

            Date dayBefore = new Date(14, Month.JULY, 2025);
            Date couponDate = firstEnd;
            Date dayAfter = new Date(16, Month.JULY, 2025);
            assertEquals(179, dc.dayCount(issue, dayBefore));
            Settings.instance.evaluationDate().equal(dayBefore);
            assertAccrual(bond, curve, dayBefore, 179, 180);
            double withoutCouponBefore = bond.settlementValue() - first.amount()
                    * curve.discount(first.date(), false) / curve.discount(dayBefore, false);
            assertTrue(Math.abs(withoutCouponBefore - bond.settlementValue()) > 1.0);

            Settings.instance.evaluationDate().equal(couponDate);
            assertEquals(0.0, bond.accruedAmount(bond.settlementDate()), 1.0e-12);
            assertEquals(bond.dirtyPrice(), bond.cleanPrice(), 0.0);
            assertEquals(100.0, bond.cleanPrice(), 1.0e-10);
            assertEquals(100.0, bond.settlementValue(), 1.0e-10);
            double withoutCoupon = invoice(bond, curve, couponDate);
            double withCoupon = withoutCoupon + first.amount()
                    * curve.discount(first.date(), false) / curve.discount(couponDate, false);
            assertEquals(withoutCoupon, bond.settlementValue(), 1.0e-12);
            assertTrue(Math.abs(withCoupon - bond.settlementValue()) > 1.0);

            Settings.instance.evaluationDate().equal(dayAfter);
            assertAccrual(bond, curve, dayAfter, 1, 180);
            assertTrue(Math.abs(bond.cleanPrice() - 100.0) > 1.0e-6);
            assertEquals(invoice(bond, curve, dayAfter), bond.settlementValue(), 1.0e-12);
            assertTrue(Math.abs(invoiceIncluding(bond, curve, dayAfter, first)
                    - bond.settlementValue()) > 1.0);

            double priceWithFlagOff = bond.cleanPrice();
            Settings.instance.setIncludeReferenceDateEvents(true);
            assertFalse(first.hasOccurred(couponDate, Optional.empty()));
            assertTrue(first.hasOccurred(couponDate, Optional.of(false)));
            assertTrue(first.hasOccurred(new Date(), Optional.empty()));
            assertEquals(priceWithFlagOff, bond.cleanPrice(), 0.0);
            assertEquals(invoice(bond, curve, dayAfter), bond.settlementValue(), 1.0e-12);
            Settings.instance.setIncludeReferenceDateEvents(false);
            assertTrue(first.hasOccurred(couponDate, Optional.empty()));
        } finally {
            Settings.instance.evaluationDate().equal(savedDate);
            Settings.instance.setIncludeReferenceDateEvents(savedInclude);
        }
    }

    private static void assertAccrual(FixedRateBond bond, YieldTermStructure curve, Date settlement,
                                      int accruedDays, int periodDays) {
        double accrued = 100.0 * 0.05 * accruedDays / 360.0;
        double dirty = 100.0 * Math.pow(1.025, accruedDays / (double) periodDays);
        assertEquals(accrued, bond.accruedAmount(settlement), 1.0e-12);
        assertEquals(dirty, bond.dirtyPrice(), 1.0e-10);
        assertEquals(dirty - accrued, bond.cleanPrice(), 1.0e-10);
        assertEquals(bond.dirtyPrice() - bond.accruedAmount(settlement), bond.cleanPrice(), 0.0);
    }

    private static double accruedOf(FixedRateCoupon coupon, Date settlement) {
        return coupon.nominal() * coupon.rate() * coupon.dayCounter().yearFraction(
                coupon.referencePeriodStart(), settlement,
                coupon.referencePeriodStart(), coupon.referencePeriodEnd());
    }

    private static double invoice(FixedRateBond bond, YieldTermStructure curve, Date settlement) {
        double dfSettlement = curve.discount(settlement, false);
        double sum = 0.0;
        for (CashFlow cashflow : bond.cashflows()) {
            if (!cashflow.hasOccurred(settlement, Optional.of(false)))
                sum += cashflow.amount() * curve.discount(cashflow.date(), false) / dfSettlement;
        }
        return sum;
    }

    private static double invoiceIncluding(FixedRateBond bond, YieldTermStructure curve,
                                           Date settlement, CashFlow extra) {
        return invoice(bond, curve, settlement)
                + extra.amount() * curve.discount(extra.date(), false) / curve.discount(settlement, false);
    }

    private static FixedRateBondHelper bondHelper(Calendar calendar, Date today, int n, TimeUnit units,
                                                  int lengthYears, double coupon, SimpleQuote price) {
        Date maturity = calendar.advance(today, n, units, BusinessDayConvention.FOLLOWING, false);
        Date issue = calendar.advance(maturity, -lengthYears, TimeUnit.YEARS,
                BusinessDayConvention.FOLLOWING, false);
        Schedule schedule = new Schedule(
                issue, maturity, new Period(Frequency.SEMIANNUAL), calendar,
                BusinessDayConvention.FOLLOWING, BusinessDayConvention.FOLLOWING,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        return new FixedRateBondHelper(
                new Handle<>(price, true),
                3, 100.0, schedule, new double[] {coupon},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING, 100.0, issue);
    }
}
