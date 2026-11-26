package instruments;

import jquant.Handle;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.CappedFlooredCmsSpreadCoupon;
import jquant.cashflows.CmsCoupon;
import jquant.cashflows.CmsCouponPricer;
import jquant.cashflows.CmsSpreadCoupon;
import jquant.cashflows.CmsSpreadCouponPricer;
import jquant.cashflows.LinearTsrPricer;
import jquant.cashflows.LognormalCmsSpreadPricer;
import jquant.indexes.Currencies;
import jquant.indexes.EuriborSwapIsdaFixA;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.SwapIndex;
import jquant.indexes.SwapSpreadIndex;
import jquant.math.Array;
import jquant.math.MathUtils;
import jquant.math.Matrix;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.math.randomnumbers.SobolRsg;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.swaption.ConstantSwaptionVolatility;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityMatrix;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official QuantLib {@code test-suite/cmsspread.cpp} port.
 *
 * <p>Ported from QuantLib at SHA {@code cf74c9f}: the full
 * {@code CmsSpreadTests} suite ({@code testFixings}, {@code testCouponPricing}
 * and {@code testShiftedLognormalDifferentShifts}) with the official data,
 * tolerances, 1,000,000 Sobol samples (seed 42) and cached cap values.
 */
public class CmsSpreadTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    /** Official {@code cmsspread.cpp} {@code struct TestData}. */
    private static class TestData {

        final Date refDate;
        final Handle<YieldTermStructure> yts2;
        final Handle<SwaptionVolatilityStructure> swLn, swSln, swN;
        final Handle<Quote> reversion, correlation;
        final CmsCouponPricer cmsPricerLn, cmsPricerSln, cmsPricerN;
        final CmsSpreadCouponPricer cmsspPricerLn, cmsspPricerSln, cmsspPricerN;

        TestData() {
            refDate = new Date(23, Month.FEBRUARY, 2018);
            Settings.instance.evaluationDate().equal(refDate);

            yts2 = new Handle<>(new FlatForward(refDate, 0.02,
                    new Actual365Fixed()), true);

            swLn = new Handle<>(new ConstantSwaptionVolatility(
                    refDate, new Target(), BusinessDayConvention.FOLLOWING,
                    0.20, new Actual365Fixed(),
                    Sarb.VolatilityType.ShiftedLognormal, 0.0), true);
            swSln = new Handle<>(new ConstantSwaptionVolatility(
                    refDate, new Target(), BusinessDayConvention.FOLLOWING,
                    0.10, new Actual365Fixed(),
                    Sarb.VolatilityType.ShiftedLognormal, 0.01), true);
            swN = new Handle<>(new ConstantSwaptionVolatility(
                    refDate, new Target(), BusinessDayConvention.FOLLOWING,
                    0.0075, new Actual365Fixed(), Sarb.VolatilityType.Normal,
                    0.01), true);

            reversion = new Handle<>(new SimpleQuote(0.01), true);
            cmsPricerLn = new LinearTsrPricer(swLn, reversion, yts2);
            cmsPricerSln = new LinearTsrPricer(swSln, reversion, yts2);
            cmsPricerN = new LinearTsrPricer(swN, reversion, yts2);

            correlation = new Handle<>(new SimpleQuote(0.6), true);
            cmsspPricerLn = new LognormalCmsSpreadPricer(
                    cmsPricerLn, correlation, yts2, 32);
            cmsspPricerSln = new LognormalCmsSpreadPricer(
                    cmsPricerSln, correlation, yts2, 32);
            cmsspPricerN = new LognormalCmsSpreadPricer(
                    cmsPricerN, correlation, yts2, 32);
        }
    }

    @Test
    public void testFixings() {
        // "Testing fixings of cms spread indices..."
        TestData d = new TestData();

        SwapIndex cms10y = new EuriborSwapIsdaFixA(
                new Period(10, TimeUnit.YEARS), d.yts2, d.yts2);
        SwapIndex cms2y = new EuriborSwapIsdaFixA(
                new Period(2, TimeUnit.YEARS), d.yts2, d.yts2);
        SwapSpreadIndex cms10y2y = new SwapSpreadIndex("cms10y2y", cms10y, cms2y);

        Settings.instance.setEnforcesTodaysHistoricFixings(false);

        assertThrows(IllegalArgumentException.class,
                () -> cms10y2y.fixing(d.refDate.substract(1), false));
        assertDoesNotThrow(() -> cms10y2y.fixing(d.refDate, false));
        assertEquals(cms10y2y.fixing(d.refDate, false),
                cms10y.fixing(d.refDate, false) - cms2y.fixing(d.refDate, false));
        cms10y.addFixing(d.refDate, 0.05, false);
        assertEquals(cms10y2y.fixing(d.refDate, false),
                cms10y.fixing(d.refDate, false) - cms2y.fixing(d.refDate, false));
        cms2y.addFixing(d.refDate, 0.04, false);
        assertEquals(cms10y2y.fixing(d.refDate, false),
                cms10y.fixing(d.refDate, false) - cms2y.fixing(d.refDate, false));
        Date futureFixingDate = new Target().adjust(
                d.refDate.add(new Period(1, TimeUnit.YEARS)));
        assertEquals(cms10y2y.fixing(futureFixingDate, false),
                cms10y.fixing(futureFixingDate, false) -
                        cms2y.fixing(futureFixingDate, false));
        IndexManager.Instance.clearHistories();

        Settings.instance.setEnforcesTodaysHistoricFixings(true);
        assertThrows(IllegalArgumentException.class,
                () -> cms10y2y.fixing(d.refDate, false));
        cms10y.addFixing(d.refDate, 0.05, false);
        assertThrows(IllegalArgumentException.class,
                () -> cms10y2y.fixing(d.refDate, false));
        cms2y.addFixing(d.refDate, 0.04, false);
        assertEquals(cms10y2y.fixing(d.refDate, false),
                cms10y.fixing(d.refDate, false) - cms2y.fixing(d.refDate, false));
    }

    /** Official {@code mcReferenceValue} with 1,000,000 Sobol samples. */
    private static double mcReferenceValue(final CmsCoupon cpn1,
                                           final CmsCoupon cpn2,
                                           final double cap,
                                           final double floor,
                                           final Handle<SwaptionVolatilityStructure> vol,
                                           final double correlation) {
        int samples = 1000000;
        double sum = 0.0;
        Matrix Cov = new Matrix(2, 2, 0.0);
        Cov.set(0, 0, vol.currentLink().blackVariance(cpn1.fixingDate(),
                cpn1.index().tenor(), cpn1.indexFixing()));
        Cov.set(1, 1, vol.currentLink().blackVariance(cpn2.fixingDate(),
                cpn2.index().tenor(), cpn2.indexFixing()));
        double offDiagonal = Math.sqrt(Cov.get(0, 0) * Cov.get(1, 1)) * correlation;
        Cov.set(0, 1, offDiagonal);
        Cov.set(1, 0, offDiagonal);
        Matrix C = MatrixUtil.pseudoSqrt(Cov, SalvagingAlgorithm.Type.None);

        Array atmRate = new Array(2), adjRate = new Array(2), avg = new Array(2),
                volShift = new Array(2);
        atmRate.set(0, cpn1.indexFixing());
        atmRate.set(1, cpn2.indexFixing());
        adjRate.set(0, cpn1.adjustedFixing());
        adjRate.set(1, cpn2.adjustedFixing());
        if (vol.currentLink().volatilityType() ==
                Sarb.VolatilityType.ShiftedLognormal) {
            volShift.set(0, vol.currentLink().shift(cpn1.fixingDate(),
                    cpn1.index().tenor()));
            volShift.set(1, vol.currentLink().shift(cpn2.fixingDate(),
                    cpn2.index().tenor()));
            avg.set(0, Math.log((adjRate.get(0) + volShift.get(0)) /
                    (atmRate.get(0) + volShift.get(0))) - 0.5 * Cov.get(0, 0));
            avg.set(1, Math.log((adjRate.get(1) + volShift.get(1)) /
                    (atmRate.get(1) + volShift.get(1))) - 0.5 * Cov.get(1, 1));
        } else {
            avg.set(0, adjRate.get(0));
            avg.set(1, adjRate.get(1));
        }

        InverseCumulativeNormal icn = new InverseCumulativeNormal();
        SobolRsg sb = new SobolRsg(2, 42,
                SobolRsg.DirectionIntegers.Jaeckel, true);
        Array w = new Array(2);
        Array z;
        for (int i = 0; i < samples; ++i) {
            List<Double> seq = sb.nextSequence().value;
            for (int j = 0; j < 2; ++j)
                w.set(j, icn.value(seq.get(j)));
            z = C.mutiply(w);
            for (int j = 0; j < 2; ++j)
                z.set(j, z.get(j) + avg.get(j));
            for (int j = 0; j < 2; ++j) {
                if (vol.currentLink().volatilityType() ==
                        Sarb.VolatilityType.ShiftedLognormal) {
                    z.set(j, (atmRate.get(j) + volShift.get(j))
                            * Math.exp(z.get(j)) - volShift.get(j));
                }
            }
            sum += Math.min(Math.max(z.get(0) - z.get(1), floor), cap);
        }
        return sum / samples;
    }

    @Test
    public void testCouponPricing() {
        // "Testing pricing of cms spread coupons..."
        TestData d = new TestData();
        double tol = 1E-6; // abs tolerance coupon rate

        SwapIndex cms10y = new EuriborSwapIsdaFixA(
                new Period(10, TimeUnit.YEARS), d.yts2, d.yts2);
        SwapIndex cms2y = new EuriborSwapIsdaFixA(
                new Period(2, TimeUnit.YEARS), d.yts2, d.yts2);
        SwapSpreadIndex cms10y2y = new SwapSpreadIndex("cms10y2y", cms10y, cms2y);

        Date valueDate = cms10y2y.valueDate(d.refDate);
        Date payDate = valueDate.add(new Period(1, TimeUnit.YEARS));
        CmsCoupon cpn1a = new CmsCoupon(
                payDate, 10000.0, valueDate, payDate, cms10y.fixingDays(), cms10y,
                1.0, 0.0, new Date(), new Date(), new Actual360(), false);
        CmsCoupon cpn1b = new CmsCoupon(
                payDate, 10000.0, valueDate, payDate, cms2y.fixingDays(),
                cms2y, 1.0, 0.0, new Date(), new Date(), new Actual360(), false);
        CmsSpreadCoupon cpn1 = new CmsSpreadCoupon(
                payDate, 10000.0, valueDate, payDate, cms10y2y.fixingDays(),
                cms10y2y, 1.0, 0.0, new Date(), new Date(), new Actual360(),
                false);
        assertTrue(TimeUtils.equals(cpn1.fixingDate(), d.refDate));
        cpn1a.setPricer(d.cmsPricerLn);
        cpn1b.setPricer(d.cmsPricerLn);
        cpn1.setPricer(d.cmsspPricerLn);

        double eqTol = 100 * MathUtils.QL_EPSILON;
        checkClose(cpn1.rate(), cpn1a.rate() - cpn1b.rate(), eqTol);
        cms10y.addFixing(d.refDate, 0.05, false);
        checkClose(cpn1.rate(), cpn1a.rate() - cpn1b.rate(), eqTol);
        cms2y.addFixing(d.refDate, 0.03, false);
        checkClose(cpn1.rate(), cpn1a.rate() - cpn1b.rate(), eqTol);
        IndexManager.Instance.clearHistories();

        CmsCoupon cpn2a = new CmsCoupon(
                new Date(23, Month.FEBRUARY, 2029), 10000.0,
                new Date(23, Month.FEBRUARY, 2028),
                new Date(23, Month.FEBRUARY, 2029), 2,
                cms10y, 1.0, 0.0, new Date(), new Date(), new Actual360(), false);
        CmsCoupon cpn2b = new CmsCoupon(
                new Date(23, Month.FEBRUARY, 2029), 10000.0,
                new Date(23, Month.FEBRUARY, 2028),
                new Date(23, Month.FEBRUARY, 2029), 2,
                cms2y, 1.0, 0.0, new Date(), new Date(), new Actual360(), false);

        CappedFlooredCmsSpreadCoupon plainCpn =
                new CappedFlooredCmsSpreadCoupon(
                        new Date(23, Month.FEBRUARY, 2029), 10000.0,
                        new Date(23, Month.FEBRUARY, 2028),
                        new Date(23, Month.FEBRUARY, 2029), 2, cms10y2y,
                        1.0, 0.0, Double.NaN,
                        Double.NaN, new Date(), new Date(), new Actual360(),
                        false);
        CappedFlooredCmsSpreadCoupon cappedCpn =
                new CappedFlooredCmsSpreadCoupon(
                        new Date(23, Month.FEBRUARY, 2029), 10000.0,
                        new Date(23, Month.FEBRUARY, 2028),
                        new Date(23, Month.FEBRUARY, 2029), 2, cms10y2y,
                        1.0, 0.0, 0.03,
                        Double.NaN, new Date(), new Date(), new Actual360(),
                        false);
        CappedFlooredCmsSpreadCoupon flooredCpn =
                new CappedFlooredCmsSpreadCoupon(
                        new Date(23, Month.FEBRUARY, 2029), 10000.0,
                        new Date(23, Month.FEBRUARY, 2028),
                        new Date(23, Month.FEBRUARY, 2029), 2, cms10y2y,
                        1.0, 0.0, Double.NaN,
                        0.01, new Date(), new Date(), new Actual360(), false);
        CappedFlooredCmsSpreadCoupon collaredCpn =
                new CappedFlooredCmsSpreadCoupon(
                        new Date(23, Month.FEBRUARY, 2029), 10000.0,
                        new Date(23, Month.FEBRUARY, 2028),
                        new Date(23, Month.FEBRUARY, 2029), 2, cms10y2y,
                        1.0, 0.0, 0.03, 0.01, new Date(), new Date(),
                        new Actual360(), false);

        cpn2a.setPricer(d.cmsPricerLn);
        cpn2b.setPricer(d.cmsPricerLn);
        plainCpn.setPricer(d.cmsspPricerLn);
        cappedCpn.setPricer(d.cmsspPricerLn);
        flooredCpn.setPricer(d.cmsspPricerLn);
        collaredCpn.setPricer(d.cmsspPricerLn);

        checkSmall(Math.abs(plainCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        MathUtils.QL_MAX_REAL, -MathUtils.QL_MAX_REAL, d.swLn,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(cappedCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        0.03, -MathUtils.QL_MAX_REAL, d.swLn,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(flooredCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        MathUtils.QL_MAX_REAL, 0.01, d.swLn,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(collaredCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        0.03, 0.01, d.swLn,
                        d.correlation.currentLink().value())), tol);

        cpn2a.setPricer(d.cmsPricerSln);
        cpn2b.setPricer(d.cmsPricerSln);
        plainCpn.setPricer(d.cmsspPricerSln);
        cappedCpn.setPricer(d.cmsspPricerSln);
        flooredCpn.setPricer(d.cmsspPricerSln);
        collaredCpn.setPricer(d.cmsspPricerSln);

        checkSmall(Math.abs(plainCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        MathUtils.QL_MAX_REAL, -MathUtils.QL_MAX_REAL, d.swSln,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(cappedCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        0.03, -MathUtils.QL_MAX_REAL, d.swSln,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(flooredCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        MathUtils.QL_MAX_REAL, 0.01, d.swSln,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(collaredCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        0.03, 0.01, d.swSln,
                        d.correlation.currentLink().value())), tol);

        cpn2a.setPricer(d.cmsPricerN);
        cpn2b.setPricer(d.cmsPricerN);
        plainCpn.setPricer(d.cmsspPricerN);
        cappedCpn.setPricer(d.cmsspPricerN);
        flooredCpn.setPricer(d.cmsspPricerN);
        collaredCpn.setPricer(d.cmsspPricerN);

        checkSmall(Math.abs(plainCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        MathUtils.QL_MAX_REAL, -MathUtils.QL_MAX_REAL, d.swN,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(cappedCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        0.03, -MathUtils.QL_MAX_REAL, d.swN,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(flooredCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        MathUtils.QL_MAX_REAL, 0.01, d.swN,
                        d.correlation.currentLink().value())), tol);
        checkSmall(Math.abs(collaredCpn.rate() - mcReferenceValue(cpn2a, cpn2b,
                        0.03, 0.01, d.swN,
                        d.correlation.currentLink().value())), tol);
    }

    @Test
    public void testShiftedLognormalDifferentShifts() {
        // "Testing shifted-lognormal CMS spread pricing with different shifts..."

        Date refDate = new Date(21, Month.SEPTEMBER, 2026);
        Settings.instance.evaluationDate().equal(refDate);
        DayCounter curveDayCounter = new Actual365Fixed();
        Calendar calendar = new NullCalendar();
        Handle<YieldTermStructure> curve = new Handle<>(
                new FlatForward(refDate, 0.03, curveDayCounter), true);
        IborIndex ibor = new IborIndex(
                "I6M", new Period(6, TimeUnit.MONTHS), 0, Currencies.usd(),
                calendar, BusinessDayConvention.UNADJUSTED, false,
                new Actual360(), curve);
        SwapIndex swap10y = swapIndex(10, calendar, ibor);
        SwapIndex swap2y = swapIndex(2, calendar, ibor);
        SwapSpreadIndex spreadIndex = new SwapSpreadIndex(
                "SP", swap10y, swap2y, 1.0, -1.0);
        Date fixingDate = refDate.add(new Period(5, TimeUnit.YEARS));
        Date paymentDate = fixingDate.add(new Period(1, TimeUnit.YEARS));
        double[] shifts1 = {0.01, 0.0, 0.01};
        double[] shifts2 = {0.01, 0.01, 0.0};

        for (int i = 0; i < shifts1.length; ++i) {
            double shift1 = shifts1[i];
            double shift2 = shifts2[i];
            Matrix vols = new Matrix(2, 2, 0.20);
            Matrix shifts = new Matrix(2, 2, 0.0);
            shifts.set(0, 0, shift2);
            shifts.set(1, 0, shift2);
            shifts.set(0, 1, shift1);
            shifts.set(1, 1, shift1);
            SwaptionVolatilityMatrix vol = new SwaptionVolatilityMatrix(
                    calendar, BusinessDayConvention.FOLLOWING,
                    List.of(new Period(1, TimeUnit.YEARS),
                            new Period(10, TimeUnit.YEARS)),
                    List.of(new Period(2, TimeUnit.YEARS),
                            new Period(10, TimeUnit.YEARS)),
                    vols, curveDayCounter, false,
                    Sarb.VolatilityType.ShiftedLognormal, shifts);
            vol.enableExtrapolation();
            Handle<SwaptionVolatilityStructure> volHandle =
                    new Handle<>(vol, true);
            CmsCouponPricer cmsPricer = new LinearTsrPricer(
                    volHandle, new Handle<>(new SimpleQuote(0.0), true));
            Handle<Quote> correlation = new Handle<>(new SimpleQuote(0.5), true);
            LognormalCmsSpreadPricer spreadPricer =
                    new LognormalCmsSpreadPricer(cmsPricer, correlation);
            CmsSpreadCoupon coupon = new CmsSpreadCoupon(
                    paymentDate, 1e6, fixingDate, paymentDate, 0, spreadIndex,
                    1.0, 0.0, new Date(), new Date(), new Actual360(), false);
            coupon.setPricer(spreadPricer);
            double swaplet = coupon.rate();

            double displacedStrikeBoundary = shift2 - shift1;
            List<Double> strikes = new ArrayList<>(List.of(
                    displacedStrikeBoundary - 0.002,
                    displacedStrikeBoundary,
                    displacedStrikeBoundary + 0.002));
            if (shift1 == 0.0 && shift2 == 0.01) {
                strikes.add(-0.002);
                strikes.add(0.002);
            }
            for (double strike : strikes) {
                double cap = spreadPricer.capletRate(strike);
                double floor = spreadPricer.floorletRate(strike);
                assertTrue(Double.isFinite(cap));
                assertTrue(Double.isFinite(floor));
                checkSmall(Math.abs(cap - floor - (swaplet - strike)), 1e-12);

                if (shift1 == 0.0 && shift2 == 0.01) {
                    if (Math.abs(strike + 0.002) < 1e-12)
                        checkSmall(Math.abs(cap - 0.0080778180), 1e-8);
                    if (Math.abs(strike - 0.002) < 1e-12)
                        checkSmall(Math.abs(cap - 0.0058457681), 1e-8);
                }
            }
        }
    }

    /** Official {@code swapIndex} lambda: generic USD swap index. */
    private static SwapIndex swapIndex(int years, Calendar calendar,
                                       IborIndex ibor) {
        return new SwapIndex(
                "S", new Period(years, TimeUnit.YEARS), 0, Currencies.usd(),
                calendar, new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.UNADJUSTED,
                new Thirty360(Thirty360.Convention.European), ibor);
    }

    /** QL_CHECK_CLOSE: the tolerance is a percentage. */
    private static void checkClose(double calculated, double expected,
                                   double percentTolerance) {
        double relativeError = Math.abs(calculated - expected) / Math.abs(expected);
        assertFalse(relativeError > percentTolerance / 100.0,
                "calculated: " + calculated + ", expected: " + expected
                        + ", relative error: " + relativeError);
    }

    /** QL_CHECK_SMALL: absolute tolerance. */
    private static void checkSmall(double difference, double tolerance) {
        assertFalse(Math.abs(difference) > tolerance,
                "difference: " + difference + ", tolerance: " + tolerance);
    }
}
