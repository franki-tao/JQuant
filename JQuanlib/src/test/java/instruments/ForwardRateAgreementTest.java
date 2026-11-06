package instruments;

import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.instruments.ForwardRateAgreement;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.FraRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRA payment and the deposit-plus-FRA bootstrap from
 * {@code test-suite/piecewiseyieldcurve.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/forwardrateagreement.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 */
public class ForwardRateAgreementTest {
    @Test
    public void testPaymentUsesForwardAndDiscountsAtValueDate() {
        // Flat 5% continuous Actual/360. Value date is after the curve
        // reference, so the invoice is the discounted payoff, not the raw amount.
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date reference = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(reference);
            DayCounter dc = new Actual360(false);
            YieldTermStructure curve = new FlatForward(reference, 0.05, dc,
                    Compounding.Continuous, Frequency.ANNUAL);
            IborIndex index = euribor3m(curve);
            Date value = new Date(17, Month.APRIL, 2025);
            Date maturity = new Date(17, Month.JULY, 2025);
            double tau = dc.yearFraction(value, maturity, new Date(), new Date());
            double time = dc.yearFraction(reference, value, new Date(), new Date());
            double forward = (Math.exp(0.05 * tau) - 1.0) / tau;
            double strike = 0.04;
            double notional = 100.0;
            double amount = notional * (forward - strike) * tau / (1.0 + forward * tau);
            double discount = Math.exp(-0.05 * time);

            ForwardRateAgreement fra = new ForwardRateAgreement(
                    index, value, maturity, ForwardRateAgreement.Position.Long,
                    strike, notional, curve, false);
            assertEquals(forward, fra.forwardRate(), 1.0e-12);
            assertEquals(amount, fra.amount(), 1.0e-12);
            assertEquals(amount * discount, fra.NPV(), 1.0e-12);
            assertTrue(Math.abs(fra.NPV() - fra.amount()) > 1.0e-4);
            assertTrue(fra.NPV() > 0.0);

            ForwardRateAgreement atTheForward = new ForwardRateAgreement(
                    index, value, maturity, ForwardRateAgreement.Position.Long,
                    forward, notional, curve, false);
            assertEquals(0.0, atTheForward.amount(), 1.0e-12);
            assertEquals(0.0, atTheForward.NPV(), 1.0e-12);

            ForwardRateAgreement shortFra = new ForwardRateAgreement(
                    index, value, maturity, ForwardRateAgreement.Position.Short,
                    strike, notional, curve, false);
            assertEquals(-fra.NPV(), shortFra.NPV(), 1.0e-12);
            assertTrue(shortFra.NPV() < 0.0);
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testDepositAndOfficialFraQuotes() {
        // piecewiseyieldcurve.cpp: 1W deposit 4.559, 3x6 FRA 4.557, 9x12 FRA 4.490.
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter dc = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            SimpleQuote depositQuote = new SimpleQuote(0.04559);
            SimpleQuote fra3x6Quote = new SimpleQuote(0.04557);
            SimpleQuote fra9x12Quote = new SimpleQuote(0.04490);
            DepositRateHelper deposit = new DepositRateHelper(
                    new Handle<>(depositQuote, true),
                    new Period(1, TimeUnit.WEEKS),
                    2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, dc);
            FraRateHelper fra3x6 = fra(fra3x6Quote, 3, target, dc);
            FraRateHelper fra9x12 = fra(fra9x12Quote, 9, target, dc);
            assertTrue(TimeUtils.equals(fra3x6.pillarDate(), fra3x6.maturityDate()));
            assertTrue(TimeUtils.equals(fra3x6.latestRelevantDate(), fra3x6.maturityDate()));

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    settlement, List.of(deposit, fra3x6, fra9x12), dc);
            assertEquals(0.04559, deposit.impliedQuote(), 1.0e-9);
            assertEquals(0.04557, fra3x6.impliedQuote(), 1.0e-9);
            assertEquals(0.04490, fra9x12.impliedQuote(), 1.0e-9);
            assertEquals(parForward(curve, dc, fra3x6.earliestDate(), fra3x6.maturityDate()),
                    0.04557, 1.0e-9);
            assertEquals(parForward(curve, dc, fra9x12.earliestDate(), fra9x12.maturityDate()),
                    0.04490, 1.0e-9);

            FraRateHelper indexed = new FraRateHelper(
                    new Handle<>(new SimpleQuote(0.04557), true), 3, 6,
                    2, target, BusinessDayConvention.MODIFIED_FOLLOWING, false, dc,
                    FraRateHelper.Pillar.LastRelevantDate, true);
            indexed.setTermStructure(curve);
            IborIndex index = euribor3m(curve);
            assertTrue(TimeUtils.equals(indexed.pillarDate(), index.maturityDate(indexed.earliestDate())));
            assertTrue(TimeUtils.equals(indexed.latestRelevantDate(), indexed.pillarDate()));
            assertEquals(parForward(curve, dc, indexed.earliestDate(), indexed.pillarDate()),
                    indexed.impliedQuote(), 1.0e-12);

            double fixing = index.forecastFixing(index.fixingDate(fra3x6.earliestDate()));
            Date start = fra3x6.earliestDate();
            Date indexEnd = index.maturityDate(start);
            double tau = dc.yearFraction(start, indexEnd, new Date(), new Date());
            double fromCurve = (curve.discount(start, false) / curve.discount(indexEnd, false) - 1.0) / tau;
            assertEquals(fromCurve, fixing, 1.0e-12);

            assertEquals(0.04559, depositQuote.value(), 0.0);
            assertEquals(0.04557, fra3x6Quote.value(), 0.0);
            assertEquals(0.04490, fra9x12Quote.value(), 0.0);
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static FraRateHelper fra(SimpleQuote quote, int monthsToStart, Calendar calendar, DayCounter dc) {
        return new FraRateHelper(new Handle<>(quote, true), monthsToStart, monthsToStart + 3,
                2, calendar, BusinessDayConvention.MODIFIED_FOLLOWING, false, dc,
                FraRateHelper.Pillar.LastRelevantDate, false);
    }

    private static double parForward(YieldTermStructure curve, DayCounter dc, Date start, Date end) {
        double tau = dc.yearFraction(start, end, new Date(), new Date());
        return (curve.discount(start, false) / curve.discount(end, false) - 1.0) / tau;
    }

    private static IborIndex euribor3m(YieldTermStructure curve) {
        return new IborIndex("Euribor", new Period(3, TimeUnit.MONTHS), 2, new jquant.Currency(),
                new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, false,
                new Actual360(false), new Handle<>(curve, false));
    }
}
