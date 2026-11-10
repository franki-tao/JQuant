package indexes;

import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.Weekday;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code test-suite/indexes.cpp}: {@code testFixingObservability}
 * and {@code testFixingHasHistoricalFixing}. A stored fixing also reprices a
 * vanilla swap whose floating period has already fixed.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/indexes.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/interestrateindex.cpp
 */
public class IndexFixingTest {
    @Test
    public void testFixingHasHistoricalFixing() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            YieldTermStructure curve = flat(today);
            IborIndex threeMonth = ibor(new Period(3, TimeUnit.MONTHS), curve);
            IborIndex sixMonth = ibor(new Period(6, TimeUnit.MONTHS), curve);
            IborIndex sixMonthAgain = ibor(new Period(6, TimeUnit.MONTHS), curve);

            sixMonth.addFixing(today, 0.01, false);

            assertFalse(threeMonth.hasHistoricalFixing(today));
            assertFalse(IndexManager.Instance.hasHistoricalFixing(threeMonth.name(), today));
            assertFalse(IndexManager.Instance.hasHistoricalFixing(threeMonth.name().toUpperCase(), today));
            assertFalse(IndexManager.Instance.hasHistoricalFixing(threeMonth.name().toLowerCase(), today));

            assertTrue(sixMonth.hasHistoricalFixing(today));
            assertTrue(sixMonthAgain.hasHistoricalFixing(today));
            assertTrue(IndexManager.Instance.hasHistoricalFixing(sixMonth.name(), today));
            assertTrue(IndexManager.Instance.hasHistoricalFixing(sixMonth.name().toUpperCase(), today));
            assertTrue(IndexManager.Instance.hasHistoricalFixing(sixMonth.name().toLowerCase(), today));
            assertEquals(0.01, sixMonthAgain.fixing(today, false), 0.0);

            sixMonth.addFixing(today, 0.01, false);
            IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                    () -> sixMonth.addFixing(today, 0.02, false));
            assertTrue(duplicate.getMessage().contains(today.toString()));
            assertEquals(0.01, sixMonth.fixing(today, false), 0.0);

            Date saturday = new Date(18, Month.JANUARY, 2025);
            assertEquals(Weekday.SATURDAY, saturday.weekday());
            IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                    () -> sixMonth.addFixing(saturday, 0.02, false));
            assertTrue(invalid.getMessage().contains("invalid fixing"));
            assertFalse(sixMonth.hasHistoricalFixing(saturday));

            IndexManager.Instance.clearHistories();
            assertFalse(sixMonth.hasHistoricalFixing(today));
            assertFalse(sixMonthAgain.hasHistoricalFixing(today));
            assertFalse(IndexManager.Instance.hasHistory(sixMonth.name()));
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testHistoricalFixingRepricesSwap() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            // Accrual starts Friday 17 Jan 2025. The fixing date is two
            // business days earlier, Wednesday 15 Jan. Evaluation is after that.
            Date accrualStart = new Date(17, Month.JANUARY, 2025);
            Calendar target = new Target();
            YieldTermStructure curve = flat(new Date(20, Month.JANUARY, 2025));
            IborIndex index = ibor(new Period(6, TimeUnit.MONTHS), curve);
            Date fixingDate = index.fixingDate(accrualStart);
            Date accrualEnd = index.maturityDate(accrualStart);
            assertTrue(target.isBusinessDay(fixingDate));
            Settings.instance.evaluationDate().equal(new Date(20, Month.JANUARY, 2025));

            VanillaSwap swap = onePeriodSwap(accrualStart, accrualEnd, index, curve);
            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, swap::NPV);
            assertTrue(missing.getMessage().contains(index.name()));
            assertTrue(missing.getMessage().contains(fixingDate.toString()));

            index.addFixing(fixingDate, 0.02, false);
            double accrual = index.dayCounter().yearFraction(accrualStart, accrualEnd, new Date(), new Date());
            double amount = 100.0 * 0.02 * accrual;
            double discount = curve.discount(target.adjust(accrualEnd, BusinessDayConvention.MODIFIED_FOLLOWING), false)
                    / curve.discount(curve.referenceDate(), false);
            assertEquals(amount * discount, swap.NPV(), 1.0e-12);
            assertEquals(0.02, index.fixing(fixingDate, false), 0.0);

            index.addFixing(fixingDate, 0.03, true);
            double overwritten = 100.0 * 0.03 * accrual * discount;
            assertEquals(overwritten, swap.NPV(), 1.0e-12);
            assertTrue(Math.abs(swap.NPV() - amount * discount) > 1.0e-4);
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static YieldTermStructure flat(Date reference) {
        return new FlatForward(reference, 0.05, new Actual360(false),
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static IborIndex ibor(Period tenor, YieldTermStructure curve) {
        return new IborIndex("Euribor", tenor, 2, new jquant.Currency(),
                new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, false,
                new Actual360(false), new Handle<>(curve, false));
    }

    private static VanillaSwap onePeriodSwap(Date start, Date end, IborIndex index, YieldTermStructure curve) {
        Schedule fixed = new Schedule(start, end, new Period(Frequency.SEMIANNUAL), new NullCalendar(),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        Schedule floating = new Schedule(start, end, new Period(Frequency.SEMIANNUAL), index.fixingCalendar(),
                BusinessDayConvention.MODIFIED_FOLLOWING, BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, 100.0, fixed, 0.0,
                new Actual360(false), floating, index, 0.0, index.dayCounter());
        swap.setPricingEngine(new DiscountingSwapEngine(curve));
        return swap;
    }
}
