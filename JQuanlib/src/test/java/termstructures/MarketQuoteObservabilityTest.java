package termstructures;

import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.instruments.ForwardRateAgreement;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FraRateHelper;
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
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Thirty360;
import jquant.time.calendars.Target;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bumped deposit quote must rebuild the log-linear curve and reprice the
 * swap and the FRA. Matches the notification checked by QuantLib
 * {@code test-suite/swap.cpp} {@code testNotifications}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/swap.cpp
 */
public class MarketQuoteObservabilityTest {
    @Test
    public void testDepositBumpRepricesSwapAndFra() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter dc = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            SimpleQuote depositQuote = new SimpleQuote(0.04559);
            SimpleQuote fraQuote = new SimpleQuote(0.04490);
            RateHelper deposit = new DepositRateHelper(
                    new Handle<>(depositQuote, true),
                    new Period(1, TimeUnit.WEEKS),
                    2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, dc);
            FraRateHelper fraHelper = new FraRateHelper(
                    new Handle<>(fraQuote, true), 9, 12,
                    2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, dc,
                    FraRateHelper.Pillar.LastRelevantDate, false);
            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, List.of(deposit, fraHelper), dc);
            IborIndex swapIndex = euribor(new Period(6, TimeUnit.MONTHS), curve);
            VanillaSwap swap = swap(settlement, 1, 0.045, swapIndex, curve);
            IborIndex fraIndex = euribor(new Period(3, TimeUnit.MONTHS), curve);
            ForwardRateAgreement fra = new ForwardRateAgreement(
                    fraIndex, fraHelper.earliestDate(), fraHelper.maturityDate(),
                    ForwardRateAgreement.Position.Long, 0.04, 100.0, curve, false);

            double npv = swap.NPV();
            double fair = swap.fairRate();
            double fraNpv = fra.NPV();
            double longDiscount = curve.discount(fraHelper.maturityDate(), false);

            depositQuote.setValue(0.05559);

            PiecewiseYieldCurve rebuilt = rebuilt(settlement, 0.05559, 0.04490, target, dc);
            IborIndex rebuiltSwapIndex = euribor(new Period(6, TimeUnit.MONTHS), rebuilt);
            VanillaSwap rebuiltSwap = swap(settlement, 1, 0.045, rebuiltSwapIndex, rebuilt);
            IborIndex rebuiltFraIndex = euribor(new Period(3, TimeUnit.MONTHS), rebuilt);
            ForwardRateAgreement rebuiltFra = new ForwardRateAgreement(
                    rebuiltFraIndex, fraHelper.earliestDate(), fraHelper.maturityDate(),
                    ForwardRateAgreement.Position.Long, 0.04, 100.0, rebuilt, false);

            assertEquals(rebuilt.discount(fraHelper.maturityDate(), false),
                    curve.discount(fraHelper.maturityDate(), false), 1.0e-12);
            assertEquals(rebuiltSwap.fairRate(), swap.fairRate(), 1.0e-12);
            assertEquals(rebuiltSwap.NPV(), swap.NPV(), 1.0e-10);
            assertEquals(rebuiltFra.NPV(), fra.NPV(), 1.0e-10);
            assertTrue(Math.abs(curve.discount(fraHelper.maturityDate(), false) - longDiscount) > 1.0e-6);
            assertTrue(Math.abs(swap.fairRate() - fair) > 1.0e-6);
            assertTrue(Math.abs(swap.NPV() - npv) > 1.0e-4);
            assertTrue(Math.abs(fra.NPV() - fraNpv) > 1.0e-6);
            assertEquals(0.04490, fraQuote.value(), 0.0);
            assertEquals(0.05559, depositQuote.value(), 0.0);
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static PiecewiseYieldCurve rebuilt(Date settlement, double depositRate, double fraRate,
                                               Calendar calendar, DayCounter dc) {
        return new PiecewiseYieldCurve(settlement, List.of(
                new DepositRateHelper(new Handle<>(new SimpleQuote(depositRate), true),
                        new Period(1, TimeUnit.WEEKS), 2, calendar,
                        BusinessDayConvention.MODIFIED_FOLLOWING, false, dc),
                new FraRateHelper(new Handle<>(new SimpleQuote(fraRate), true), 9, 12,
                        2, calendar, BusinessDayConvention.MODIFIED_FOLLOWING, false, dc,
                        FraRateHelper.Pillar.LastRelevantDate, false)), dc);
    }

    private static IborIndex euribor(Period tenor, PiecewiseYieldCurve curve) {
        return new IborIndex("Euribor", tenor, 2, new jquant.Currency(),
                new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, false,
                new Actual360(false), new Handle<>(curve, false));
    }

    private static VanillaSwap swap(Date settlement, int years, double fixedRate,
                                    IborIndex index, PiecewiseYieldCurve curve) {
        Calendar calendar = index.fixingCalendar();
        Date maturity = calendar.advance(settlement, years, TimeUnit.YEARS,
                BusinessDayConvention.MODIFIED_FOLLOWING, false);
        Schedule fixed = new Schedule(settlement, maturity, new Period(Frequency.ANNUAL), calendar,
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        Schedule floating = new Schedule(settlement, maturity, index.tenor(), calendar,
                BusinessDayConvention.MODIFIED_FOLLOWING, BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.FORWARD, false, new Date(), new Date());
        VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, 100.0, fixed, fixedRate,
                new Thirty360(Thirty360.Convention.BondBasis), floating, index, 0.0, index.dayCounter());
        swap.setPricingEngine(new DiscountingSwapEngine(curve));
        return swap;
    }
}
