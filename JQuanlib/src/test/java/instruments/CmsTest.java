package instruments;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.AnalyticHaganPricer;
import jquant.cashflows.CappedFlooredCmsCoupon;
import jquant.cashflows.CmsCouponPricer;
import jquant.cashflows.GFunctionFactory;
import jquant.cashflows.LinearTsrPricer;
import jquant.cashflows.NumericHaganPricer;
import jquant.indexes.Euribor6M;
import jquant.indexes.EuriborSwapIsdaFixA;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.SwapIndex;
import jquant.instruments.MakeCms;
import jquant.instruments.Swap;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.optimization.Simplex;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.swaption.CmsMarket;
import jquant.termstructures.volatility.swaption.CmsMarketCalibration;
import jquant.termstructures.volatility.swaption.InterpolatedSwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.SabrSwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityMatrix;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.cashflows.GFunctionFactory.YieldCurveModel.ExactYield;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.NonParallelShifts;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.ParallelShifts;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.Standard;
import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official QuantLib {@code test-suite/cms.cpp} port (ATM lognormal slice).
 *
 * <p>Ported from QuantLib at SHA {@code cf74c9f}: {@code testFairRate},
 * {@code testCmsSwap}, {@code testParity},
 * {@code testCmsMarketCalibration} and
 * {@code testSabrCubeRecalibrationInitializesLazily} in full (ATM surface plus
 * the official interpolated and SABR cubes).
 *
 * <p>Dates, fixings, parameter branches, comparisons and tolerances are those
 * of the official test; no cached expected prices are used. The evaluation
 * date is {@code TARGET.adjust(Date.todaysDate())}, as official.
 */
public class CmsTest {

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

    /** Official {@code cms.cpp} {@code struct CommonVars}. */
    private static class CommonVars {

        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        IborIndex iborIndex;

        Handle<SwaptionVolatilityStructure> atmVol;
        Handle<SwaptionVolatilityStructure> SabrVolCube1;
        Handle<SwaptionVolatilityStructure> SabrVolCube2;

        List<GFunctionFactory.YieldCurveModel> yieldCurveModels;
        List<CmsCouponPricer> numericalPricers;
        List<CmsCouponPricer> analyticPricers;

        CommonVars() {
            Calendar calendar = new Target();

            Date referenceDate = calendar.adjust(Date.todaysDate());
            Settings.instance.evaluationDate().equal(referenceDate);

            termStructure.linkTo(new FlatForward(referenceDate, 0.05,
                    new Actual365Fixed()), true);

            // ATM volatility structure
            List<Period> atmOptionTenors = List.of(
                    new Period(1, TimeUnit.MONTHS),
                    new Period(6, TimeUnit.MONTHS),
                    new Period(1, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));

            List<Period> atmSwapTenors = List.of(
                    new Period(1, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));

            Matrix m = new Matrix(new double[][]{
                    {0.1300, 0.1560, 0.1390, 0.1220},
                    {0.1440, 0.1580, 0.1460, 0.1260},
                    {0.1600, 0.1590, 0.1470, 0.1290},
                    {0.1640, 0.1470, 0.1370, 0.1220},
                    {0.1400, 0.1300, 0.1250, 0.1100},
                    {0.1130, 0.1090, 0.1070, 0.0930}
            });

            atmVol = new Handle<>(new SwaptionVolatilityMatrix(calendar,
                    BusinessDayConvention.FOLLOWING,
                    atmOptionTenors,
                    atmSwapTenors,
                    m,
                    new Actual365Fixed()), true);

            iborIndex = new Euribor6M(termStructure);

            // Swaption volatility cubes, as official cms.cpp CommonVars.
            List<Period> optionTenors = List.of(
                    new Period(1, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));
            List<Period> swapTenors = List.of(
                    new Period(2, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));
            double[] strikeSpreads = {-0.020, -0.005, 0.000, 0.005, 0.020};

            int nRows = optionTenors.size() * swapTenors.size();
            int nCols = strikeSpreads.length;
            double[][] volSpreadsMatrix = {
                    {0.0599, 0.0049, 0.0000, -0.0001, 0.0127},
                    {0.0729, 0.0086, 0.0000, -0.0024, 0.0098},
                    {0.0738, 0.0102, 0.0000, -0.0039, 0.0065},
                    {0.0465, 0.0063, 0.0000, -0.0032, -0.0010},
                    {0.0558, 0.0084, 0.0000, -0.0050, -0.0057},
                    {0.0576, 0.0083, 0.0000, -0.0043, -0.0014},
                    {0.0437, 0.0059, 0.0000, -0.0030, -0.0006},
                    {0.0533, 0.0078, 0.0000, -0.0045, -0.0046},
                    {0.0545, 0.0079, 0.0000, -0.0042, -0.0020}
            };

            List<List<Handle<Quote>>> volSpreads = new ArrayList<>(nRows);
            for (int i = 0; i < nRows; ++i) {
                List<Handle<Quote>> row = new ArrayList<>(nCols);
                for (int j = 0; j < nCols; ++j)
                    row.add(new Handle<>(
                            new SimpleQuote(volSpreadsMatrix[i][j]), true));
                volSpreads.add(row);
            }

            SwapIndex swapIndexBase = new EuriborSwapIsdaFixA(
                    new Period(10, TimeUnit.YEARS), termStructure);
            SwapIndex shortSwapIndexBase = new EuriborSwapIsdaFixA(
                    new Period(2, TimeUnit.YEARS), termStructure);

            boolean vegaWeightedSmileFit = false;

            SabrVolCube2 = new Handle<>(
                    new InterpolatedSwaptionVolatilityCube(atmVol,
                            optionTenors, swapTenors, strikeSpreads, volSpreads,
                            swapIndexBase, shortSwapIndexBase,
                            vegaWeightedSmileFit), true);
            SabrVolCube2.currentLink().enableExtrapolation();

            List<List<Handle<Quote>>> guess = new ArrayList<>(nRows);
            for (int i = 0; i < nRows; ++i) {
                List<Handle<Quote>> row = new ArrayList<>(4);
                row.add(new Handle<>(new SimpleQuote(0.2), true));
                row.add(new Handle<>(new SimpleQuote(0.5), true));
                row.add(new Handle<>(new SimpleQuote(0.4), true));
                row.add(new Handle<>(new SimpleQuote(0.0), true));
                guess.add(row);
            }
            boolean[] isParameterFixed = {false, true, false, false};
            boolean isAtmCalibrated = false;

            SabrVolCube1 = new Handle<>(
                    new SabrSwaptionVolatilityCube(atmVol,
                            optionTenors, swapTenors, strikeSpreads, volSpreads,
                            swapIndexBase, shortSwapIndexBase,
                            vegaWeightedSmileFit, guess, isParameterFixed,
                            isAtmCalibrated), true);
            SabrVolCube1.currentLink().enableExtrapolation();

            yieldCurveModels = Arrays.asList(
                    Standard,
                    ExactYield,
                    ParallelShifts,
                    NonParallelShifts,
                    NonParallelShifts);

            Handle<Quote> zeroMeanRev =
                    new Handle<>(new SimpleQuote(0.0), true);

            numericalPricers = new ArrayList<>();
            analyticPricers = new ArrayList<>();
            for (int j = 0; j < yieldCurveModels.size(); ++j) {
                if (j < yieldCurveModels.size() - 1)
                    numericalPricers.add(new NumericHaganPricer(
                            atmVol, yieldCurveModels.get(j), zeroMeanRev));
                else
                    numericalPricers.add(new LinearTsrPricer(
                            atmVol, zeroMeanRev));

                analyticPricers.add(new AnalyticHaganPricer(
                        atmVol, yieldCurveModels.get(j), zeroMeanRev));
            }
        }
    }

    /**
     * Official {@code testFairRate}: flat-vol equivalence between the
     * numerical and analytic Hagan pricers (lognormal case).
     */
    @Test
    public void testFairRate() {
        CommonVars vars = new CommonVars();

        SwapIndex swapIndex = new SwapIndex("EuriborSwapIsdaFixA",
                new Period(10, TimeUnit.YEARS),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.UNADJUSTED,
                vars.iborIndex.dayCounter(),
                vars.iborIndex);

        Date startDate = vars.termStructure.currentLink().referenceDate()
                .add(new Period(20, TimeUnit.YEARS));
        Date paymentDate = startDate.add(new Period(1, TimeUnit.YEARS));
        Date endDate = paymentDate;
        double nominal = 1.0;
        double infiniteCap = Double.NaN;
        double infiniteFloor = Double.NaN;
        double gearing = 1.0;
        double spread = 0.0;
        CappedFlooredCmsCoupon coupon = new CappedFlooredCmsCoupon(
                paymentDate, nominal,
                startDate, endDate,
                swapIndex.fixingDays(), swapIndex,
                gearing, spread,
                infiniteCap, infiniteFloor,
                startDate, endDate,
                vars.iborIndex.dayCounter());

        for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
            vars.numericalPricers.get(j).setSwaptionVolatility(vars.atmVol);
            coupon.setPricer(vars.numericalPricers.get(j));
            double rate0 = coupon.rate();

            vars.analyticPricers.get(j).setSwaptionVolatility(vars.atmVol);
            coupon.setPricer(vars.analyticPricers.get(j));
            double rate1 = coupon.rate();

            double difference = Math.abs(rate1 - rate0);
            double tol = 2.0e-4;
            boolean linearTsr = j == vars.yieldCurveModels.size() - 1;

            String message = "\nCoupon payment date: " + paymentDate
                    + "\nCoupon start date:   " + startDate
                    + "\nCoupon floor:        " + infiniteFloor
                    + "\nCoupon gearing:      " + gearing
                    + "\nCoupon swap index:   " + swapIndex.name()
                    + "\nCoupon spread:       " + spread
                    + "\nCoupon cap:          " + infiniteCap
                    + "\nCoupon DayCounter:   " + vars.iborIndex.dayCounter()
                    + "\nYieldCurve Model:    " + vars.yieldCurveModels.get(j)
                    + "\nNumerical Pricer:    " + rate0
                    + (linearTsr ? " (Linear TSR Model)" : "")
                    + "\nAnalytic Pricer:     " + rate1
                    + "\ndifference:          " + difference
                    + "\ntolerance:           " + tol;

            assertTrue(difference <= tol, message);
        }
    }

    /**
     * Official {@code testCmsSwap}: flat-vol equivalence for CMS swaps
     * (lognormal case).
     */
    @Test
    public void testCmsSwap() {
        CommonVars vars = new CommonVars();

        SwapIndex swapIndex = new SwapIndex("EuriborSwapIsdaFixA",
                new Period(10, TimeUnit.YEARS),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.UNADJUSTED,
                vars.iborIndex.dayCounter(),
                vars.iborIndex);

        double spread = 0.0;
        int[] swapLengths = {1, 5, 6, 10};
        int n = swapLengths.length;
        List<Swap> cms = new ArrayList<>(n);
        for (int i = 0; i < n; ++i)
            // no cap, floor
            // no gearing, spread
            cms.add(new MakeCms(new Period(swapLengths[i], TimeUnit.YEARS),
                    swapIndex,
                    vars.iborIndex)
                    .withIborSpread(spread)
                    .withForwardStart(new Period(10, TimeUnit.DAYS))
                    .makeSwap());

        for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
            vars.numericalPricers.get(j).setSwaptionVolatility(vars.atmVol);
            vars.analyticPricers.get(j).setSwaptionVolatility(vars.atmVol);
            for (int sl = 0; sl < n; ++sl) {
                setCouponPricer(cms.get(sl).leg(0),
                        vars.numericalPricers.get(j));
                double priceNum = cms.get(sl).NPV();
                setCouponPricer(cms.get(sl).leg(0),
                        vars.analyticPricers.get(j));
                double priceAn = cms.get(sl).NPV();

                double difference = Math.abs(priceNum - priceAn);
                double tol = 2.0e-4;
                boolean linearTsr = j == vars.yieldCurveModels.size() - 1;

                String message = "\nLength in Years:  " + swapLengths[sl]
                        + "\nswap index:       " + swapIndex.name()
                        + "\nibor index:       " + vars.iborIndex.name()
                        + "\nspread:           " + spread
                        + "\nYieldCurve Model: " + vars.yieldCurveModels.get(j)
                        + "\nNumerical Pricer: " + priceNum
                        + (linearTsr ? " (Linear TSR Model)" : "")
                        + "\nAnalytic Pricer:  " + priceAn
                        + "\ndifference:       " + difference
                        + "\ntolerance:        " + tol;

                assertTrue(difference <= tol, message);
            }
        }
    }

    /**
     * Official {@code testParity}: put-call parity for capped-floored CMS
     * coupons (lognormal case) over the ATM surface and both official cubes.
     */
    @Test
    public void testParity() {
        CommonVars vars = new CommonVars();

        SwapIndex swapIndex = new EuriborSwapIsdaFixA(
                new Period(10, TimeUnit.YEARS),
                vars.iborIndex.forwardingTermStructure());

        Date startDate = vars.termStructure.currentLink().referenceDate()
                .add(new Period(20, TimeUnit.YEARS));
        Date paymentDate = startDate.add(new Period(1, TimeUnit.YEARS));
        Date endDate = paymentDate;
        double nominal = 1.0;
        double infiniteCap = Double.NaN;
        double infiniteFloor = Double.NaN;
        double gearing = 1.0;
        double spread = 0.0;
        double discount = vars.termStructure.currentLink()
                .discount(paymentDate);
        CappedFlooredCmsCoupon swaplet = new CappedFlooredCmsCoupon(
                paymentDate, nominal,
                startDate, endDate,
                swapIndex.fixingDays(),
                swapIndex,
                gearing, spread,
                infiniteCap, infiniteFloor,
                startDate, endDate,
                vars.iborIndex.dayCounter());

        for (double strike = 0.02; strike < 0.12; strike += 0.05) {
            CappedFlooredCmsCoupon caplet = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(),
                    swapIndex,
                    gearing, spread,
                    strike, infiniteFloor,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());
            CappedFlooredCmsCoupon floorlet = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(),
                    swapIndex,
                    gearing, spread,
                    infiniteCap, strike,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());

            for (Handle<SwaptionVolatilityStructure> swaptionVol
                    : List.of(vars.atmVol, vars.SabrVolCube1,
                            vars.SabrVolCube2)) {
            for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
                vars.numericalPricers.get(j).setSwaptionVolatility(swaptionVol);
                vars.analyticPricers.get(j).setSwaptionVolatility(swaptionVol);
                List<CmsCouponPricer> pricers = new ArrayList<>(2);
                pricers.add(vars.numericalPricers.get(j));
                pricers.add(vars.analyticPricers.get(j));
                for (int k = 0; k < pricers.size(); ++k) {
                    swaplet.setPricer(pricers.get(k));
                    caplet.setPricer(pricers.get(k));
                    floorlet.setPricer(pricers.get(k));
                    double swapletPrice = swaplet.price(vars.termStructure)
                            + nominal * swaplet.accrualPeriod() * strike
                            * discount;
                    double capletPrice = caplet.price(vars.termStructure);
                    double floorletPrice = floorlet.price(vars.termStructure);
                    double difference = Math.abs(capletPrice + floorletPrice
                            - swapletPrice);
                    double tol = 2.0e-5;
                    boolean linearTsr = k == 0
                            && j == vars.yieldCurveModels.size() - 1;
                    if (linearTsr)
                        tol = 1.0e-7;

                    String message = "\nCoupon payment date: " + paymentDate
                            + "\nCoupon start date:   " + startDate
                            + "\nCoupon gearing:      " + gearing
                            + "\nCoupon swap index:   " + swapIndex.name()
                            + "\nCoupon spread:       " + spread
                            + "\nstrike:              " + strike
                            + "\nCoupon DayCounter:   "
                            + vars.iborIndex.dayCounter()
                            + "\nYieldCurve Model:    "
                            + vars.yieldCurveModels.get(j)
                            + (k == 0 ? "\nNumerical Pricer"
                            : "\nAnalytic Pricer")
                            + (linearTsr ? " (Linear TSR Model)" : "")
                            + "\nSwaplet price:       " + swapletPrice
                            + "\nCaplet price:        " + capletPrice
                            + "\nFloorlet price:      " + floorletPrice
                            + "\ndifference:          " + difference
                            + "\ntolerance:           " + tol;

                    assertTrue(difference <= tol, message);
                }
            }
            }
        }
    }

    /**
     * Official {@code testCmsMarketCalibration}: calibration of the SABR cube
     * to the CMS market over the three calibration types, with fixed and free
     * mean reversion, plus the official array/matrix/parametric result shapes
     * and exception cases.
     */
    @Test
    public void testCmsMarketCalibration() {
        CommonVars vars = new CommonVars();

        List<Period> swapLengths = List.of(
                new Period(1, TimeUnit.YEARS),
                new Period(5, TimeUnit.YEARS),
                new Period(10, TimeUnit.YEARS),
                new Period(20, TimeUnit.YEARS));
        SabrSwaptionVolatilityCube sabrCube =
                (SabrSwaptionVolatilityCube) vars.SabrVolCube1.currentLink();
        Period calibrationTenor = sabrCube.swapTenors().get(0);

        List<SwapIndex> swapIndexes = new ArrayList<>(1);
        swapIndexes.add(new EuriborSwapIsdaFixA(calibrationTenor, vars.termStructure));

        List<List<Handle<Quote>>> bidAskSpreads = new ArrayList<>(4);
        double[][] bidAsk = {
                {0.01, 0.011},
                {0.012, 0.013},
                {0.014, 0.015},
                {0.016, 0.017}
        };
        for (double[] row : bidAsk) {
            List<Handle<Quote>> quotes = new ArrayList<>(2);
            quotes.add(new Handle<>(new SimpleQuote(row[0]), true));
            quotes.add(new Handle<>(new SimpleQuote(row[1]), true));
            bidAskSpreads.add(quotes);
        }

        List<CmsCouponPricer> pricers = List.of(vars.analyticPricers.get(0));
        CmsMarket cmsMarket = new CmsMarket(
                swapLengths, swapIndexes, vars.iborIndex, bidAskSpreads,
                pricers, vars.termStructure);
        Matrix weights = new Matrix(4, 1, 1.0);
        EndCriteria endCriteria =
                new EndCriteria(20, 10, 1e-6, 1e-6, 1e-6);
        OptimizationMethod method =
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        OptimizationMethod simplex = new Simplex(0.1);

        checkClose(CmsMarketCalibration.betaTransformDirect(
                        CmsMarketCalibration.betaTransformInverse(0.5)),
                0.5, 1e-12, "beta transform round trip");
        assertEquals(0.000001, CmsMarketCalibration.betaTransformDirect(20.0));
        assertEquals(0.000001, CmsMarketCalibration.betaTransformDirect(-20.0));
        checkClose(CmsMarketCalibration.reversionTransformDirect(
                        CmsMarketCalibration.reversionTransformInverse(0.01)),
                0.01, 1e-12, "reversion transform round trip");

        for (CmsMarketCalibration.CalibrationType calibrationType : List.of(
                CmsMarketCalibration.CalibrationType.OnSpread,
                CmsMarketCalibration.CalibrationType.OnPrice,
                CmsMarketCalibration.CalibrationType.OnForwardCmsPrice)) {
            CmsMarketCalibration calibration = new CmsMarketCalibration(
                    vars.SabrVolCube1, cmsMarket, weights, calibrationType);
            Array guess = new Array(2);
            guess.set(0, 0.5);
            guess.set(1, 0.01);
            Array result = calibration.compute(endCriteria, method, guess, false);
            assertEquals(2, result.size());
            assertTrue(result.get(0) > 0.0 && result.get(0) < 1.0,
                    "beta out of range: " + result.get(0));
            assertTrue(result.get(1) >= 0.0,
                    "reversion out of range: " + result.get(1));
            assertTrue(Double.isFinite(calibration.error()),
                    "non-finite error: " + calibration.error());
            assertTrue(calibration.error() < 1.0,
                    "error too large: " + calibration.error());
        }

        CmsMarketCalibration fixedCalibration = new CmsMarketCalibration(
                vars.SabrVolCube1, cmsMarket, weights,
                CmsMarketCalibration.CalibrationType.OnSpread);
        Array fixedGuess = new Array(2);
        fixedGuess.set(0, 0.5);
        fixedGuess.set(1, 0.01);
        Array fixedResult = fixedCalibration.compute(
                endCriteria, method, fixedGuess, true);
        assertEquals(2, fixedResult.size());
        assertEquals(fixedGuess.get(1), fixedResult.get(1));

        Matrix matrixGuess = new Matrix(4, 1, 0.5);
        Matrix matrixResult = fixedCalibration.compute(
                endCriteria, method, matrixGuess, true, 0.01);
        assertEquals(4, matrixResult.rows());
        assertEquals(2, matrixResult.cols());
        assertEquals(0.01, matrixResult.get(0, 1));

        Matrix freeMatrixResult = fixedCalibration.compute(
                endCriteria, simplex, matrixGuess, false, 0.01);
        assertEquals(4, freeMatrixResult.rows());
        assertEquals(2, freeMatrixResult.cols());

        Matrix parametricGuess = new Matrix(3, 1, 0.5);
        parametricGuess.set(2, 0, 0.1);
        Matrix parametricResult = fixedCalibration.computeParametric(
                endCriteria, method, parametricGuess, true, 0.01);
        assertEquals(3, parametricResult.rows());
        assertEquals(2, parametricResult.cols());
        assertEquals(0.01, parametricResult.get(0, 1));

        Matrix freeParametricResult = fixedCalibration.computeParametric(
                endCriteria, method, parametricGuess, false, 0.01);
        assertEquals(3, freeParametricResult.rows());
        assertEquals(2, freeParametricResult.cols());
        assertTrue(freeParametricResult.get(0, 1) >= 0.0,
                "reversion out of range: " + freeParametricResult.get(0, 1));

        assertThrows(IllegalArgumentException.class, () ->
                new CmsMarketCalibration(vars.SabrVolCube1, cmsMarket,
                        new Matrix(2, 1, 1.0),
                        CmsMarketCalibration.CalibrationType.OnSpread));
        assertThrows(IllegalArgumentException.class, () ->
                new CmsMarketCalibration(vars.SabrVolCube1, cmsMarket,
                        new Matrix(1, 2, 1.0),
                        CmsMarketCalibration.CalibrationType.OnSpread));
        assertThrows(IllegalArgumentException.class, () ->
                fixedCalibration.compute(endCriteria, method,
                        new Array(1, 0.5), false));
        assertThrows(IllegalArgumentException.class, () ->
                fixedCalibration.compute(endCriteria, method,
                        new Matrix(5, 1, 0.5), true, 0.01));
        assertThrows(IllegalArgumentException.class, () ->
                fixedCalibration.computeParametric(endCriteria, method,
                        new Matrix(2, 1, 0.5), true, 0.01));
    }

    /**
     * Official {@code testSabrCubeRecalibrationInitializesLazily}: the SABR
     * cube must initialize itself on demand inside {@code recalibration}.
     */
    @Test
    public void testSabrCubeRecalibrationInitializesLazily() {
        CommonVars vars = new CommonVars();

        SabrSwaptionVolatilityCube sabrCube =
                (SabrSwaptionVolatilityCube) vars.SabrVolCube1.currentLink();
        double[] beta = new double[sabrCube.optionTenors().size()];
        Arrays.fill(beta, 0.5);

        assertDoesNotThrow(() ->
                sabrCube.recalibration(beta, sabrCube.swapTenors().get(0)));
    }

    // BOOST_CHECK_CLOSE / QL_CHECK_CLOSE: the tolerance is a percentage.
    private static void checkClose(double calculated, double expected,
                                   double percentTolerance, String message) {
        double relativeError = Math.abs(calculated - expected) / Math.abs(expected);
        assertFalse(relativeError > percentTolerance / 100.0, message);
    }
}
