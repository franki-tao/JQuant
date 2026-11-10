package instruments;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.cashflows.AverageBMACoupon;
import jquant.cashflows.Coupon;
import jquant.indexes.BMAIndex;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.BMASwap;
import jquant.instruments.Swap;
import jquant.math.Rounding;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib BMA swap fair fraction and fair spread, plus the calendar-day
 * weighted average in {@code AverageBMACouponPricer::swapletRate}.
 *
 * <p>{@code test-suite/bmaswap.cpp} is not present from QuantLib tag
 * {@code QuantLib-v1.1} onward. The checks below follow
 * {@code SwapTest::testFairRate} / {@code testFairSpread} (NPV within
 * 1.0e-10 of zero) and {@code ql/cashflows/averagebmacoupon.cpp}.
 * The surviving curve test {@code testBMACurveConsistency} needs a joint
 * UK/US Libor calendar and a full deposit-futures-swap bootstrap, which
 * this tree does not have, so its quoted fractions are not asserted here.
 */
public class BMASwapTest {
    private static final double TOLERANCE = 1.0e-10;

    @Test
    public void testAverageRateAndHistoricalFixings() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date start = new Date(16, Month.JANUARY, 2025);
            Date end = new Date(16, Month.APRIL, 2025);
            Date today = new Date(16, Month.APRIL, 2025);
            YieldTermStructure curve = flat(today);
            BMAIndex index = new BMAIndex(new Handle<>(curve, false));
            Settings.instance.evaluationDate().equal(today);
            AverageBMACoupon coupon = coupon(start, end, index);
            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, coupon::rate);
            assertTrue(missing.getMessage().contains(index.name()));

            for (Date fixing : coupon.fixingDates()) {
                if (TimeUtils.less(fixing, today) && index.isValidFixingDate(fixing))
                    index.addFixing(fixing, 0.02, false);
            }
            // A constant fixing averages to itself. Act/Act (ISDA) accrual is 90/365.
            assertEquals(0.02, coupon.rate(), 1.0e-12);
            double accrual = new ActualActual(ActualActual.Convention.ISDA)
                    .yearFraction(start, end, new Date(), new Date());
            assertEquals(90.0 / 365.0, accrual, 1.0e-15);
            assertEquals(100.0 * 0.02 * accrual, coupon.amount(), 1.0e-12);
            assertEquals(0.4931506849315068, coupon.amount(), 1.0e-12);

            Date first = null;
            for (Date fixing : coupon.fixingDates()) {
                if (index.isValidFixingDate(fixing) && index.hasHistoricalFixing(fixing)) {
                    first = fixing;
                    break;
                }
            }
            index.addFixing(first, 0.04, true);
            double weighted = weightedAverage(coupon, index);
            assertEquals(weighted, coupon.rate(), 1.0e-12);
            assertTrue(Math.abs(coupon.rate() - 0.02) > 1.0e-4);
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testFairRateAndFairSpread() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            UnitedStates settlement = new UnitedStates(UnitedStates.Market.Settlement);
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Date start = settlement.advance(today, 2, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
            // The first BMA value date can be the business day before the accrual
            // start, so the forwarding curve has to begin on or before today.
            YieldTermStructure curve = flat(today);
            BMAIndex bma = new BMAIndex(new Handle<>(curve, false));
            IborIndex libor = libor(curve);
            int[] lengths = {1, 2, 5};
            double[] spreads = {-0.001, 0.0, 0.001};
            for (int length : lengths) {
                for (double spread : spreads) {
                    BMASwap probe = swap(start, length, 0.75, spread, libor, bma, curve);
                    double fairFraction = probe.fairLiborFraction();
                    BMASwap repriced = swap(start, length, fairFraction, spread, libor, bma, curve);
                    assertEquals(0.0, repriced.NPV(), TOLERANCE, "length " + length + " spread " + spread);

                    BMASwap spreadProbe = swap(start, length, 0.75, 0.0, libor, bma, curve);
                    double fairSpread = spreadProbe.fairLiborSpread();
                    BMASwap spreadSwap = swap(start, length, 0.75, fairSpread, libor, bma, curve);
                    assertEquals(0.0, spreadSwap.NPV(), TOLERANCE, "spread length " + length);
                }
            }

            // One historical Libor fixing and one historical BMA fixing change the 1-year NPV.
            BMASwap before = swap(start, 1, 0.75, 0.0, libor, bma, curve);
            double forecastNpv = before.NPV();
            Coupon firstLibor = (Coupon) before.leg(0).get(0);
            Date liborFixing = ((jquant.cashflows.IborCoupon) firstLibor).fixingDate();
            Settings.instance.evaluationDate().equal(liborFixing.add(1));
            assertThrows(IllegalArgumentException.class, before::NPV);
            libor.addFixing(liborFixing, 0.03, false);
            for (jquant.CashFlow flow : before.leg(1)) {
                AverageBMACoupon average = (AverageBMACoupon) flow;
                for (Date fixing : average.fixingDates()) {
                    if (TimeUtils.less(fixing, liborFixing.add(1)) && bma.isValidFixingDate(fixing)
                            && !bma.hasHistoricalFixing(fixing))
                        bma.addFixing(fixing, 0.02, false);
                }
            }
            double historicalNpv = before.NPV();
            assertTrue(Math.abs(historicalNpv - forecastNpv) > 1.0e-6);
            libor.addFixing(liborFixing, 0.04, true);
            assertTrue(Math.abs(before.NPV() - historicalNpv) > 1.0e-4);
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static double weightedAverage(AverageBMACoupon coupon, BMAIndex index) {
        var dates = coupon.fixingDates();
        Date start = coupon.accrualStartDate();
        Date end = coupon.accrualEndDate();
        double average = 0.0;
        Date d1 = start;
        for (int i = 0; i < dates.size() - 1; i++) {
            Date value = index.valueDate(dates.get(i));
            Date next = index.valueDate(dates.get(i + 1));
            if (!TimeUtils.less(dates.get(i), end) || !TimeUtils.less(value, end))
                break;
            if (TimeUtils.less(dates.get(i + 1), start) || !TimeUtils.greater(next, start))
                continue;
            Date d2 = TimeUtils.min(next, end);
            average += index.fixing(dates.get(i), false) * TimeUtils.substract(d2, d1);
            d1 = d2;
        }
        return average / TimeUtils.substract(end, start);
    }

    private static AverageBMACoupon coupon(Date start, Date end, BMAIndex index) {
        return new AverageBMACoupon(end, 100.0, start, end, index, 1.0, 0.0,
                new Date(), new Date(), new ActualActual(ActualActual.Convention.ISDA));
    }

    private static BMASwap swap(Date start, int years, double fraction, double spread,
                                IborIndex libor, BMAIndex bma, YieldTermStructure curve) {
        Date end = start.add(new Period(years, TimeUnit.YEARS));
        Schedule liborSchedule = new Schedule(start, end, new Period(3, TimeUnit.MONTHS),
                libor.fixingCalendar(), BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING, DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Schedule bmaSchedule = new MakeSchedule().from(start).to(end)
                .withFrequency(Frequency.QUARTERLY)
                .withCalendar(bma.fixingCalendar())
                .withConvention(BusinessDayConvention.FOLLOWING)
                .backwards()
                .toSchedule();
        BMASwap swap = new BMASwap(Swap.Type.Payer, 100.0,
                liborSchedule, fraction, spread, libor, libor.dayCounter(),
                bmaSchedule, bma, new ActualActual(ActualActual.Convention.ISDA));
        swap.setPricingEngine(new DiscountingMultiLegSwapEngine(curve));
        return swap;
    }

    private static YieldTermStructure flat(Date reference) {
        return new FlatForward(reference, 0.04, new Actual360(false),
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static IborIndex libor(YieldTermStructure curve) {
        return new IborIndex("USDLibor", new Period(3, TimeUnit.MONTHS), 2, usd(),
                new UnitedStates(UnitedStates.Market.Settlement),
                BusinessDayConvention.MODIFIED_FOLLOWING, true,
                new Actual360(false), new Handle<>(curve, false));
    }

    private static Currency usd() {
        return new Currency("U.S. dollar", "USD", 840, "$", "\u00A2", 100,
                new Rounding(), new Currency(), Set.of());
    }
}
