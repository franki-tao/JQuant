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
import jquant.cashflows.NumericHaganPricer;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.SwapIndex;
import jquant.instruments.MakeCms;
import jquant.instruments.Swap;
import jquant.math.Matrix;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
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
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.ExactYield;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.NonParallelShifts;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.ParallelShifts;
import static jquant.cashflows.GFunctionFactory.YieldCurveModel.Standard;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Official QuantLib {@code test-suite/cms_normal.cpp} port (normal case).
 *
 * <p>Ported from QuantLib at SHA {@code cf74c9f}: {@code testFairRate},
 * {@code testCmsSwap} and {@code testParity} in full, using the official
 * normal ATM volatility matrix, the official interpolated and SABR cubes and
 * the official generic swap indexes. The evaluation date is
 * {@code TARGET.adjust(Date.todaysDate())}, as official.
 *
 * <p>No cached expected prices are used. The comparison operators and
 * tolerances are those of the official test, including the official
 * {@code std::round(10.0*(difference-tol))/10.0 > 0.0} checks in
 * {@code testFairRate} and {@code testCmsSwap} and the plain
 * {@code difference > tol} check in {@code testParity}.
 */
public class CmsNormalTest {

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

    /** Official {@code cms_normal.cpp} {@code struct CommonVars}. */
    private static class CommonVars {

        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        final IborIndex iborIndex;

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

            termStructure.linkTo(new FlatForward(referenceDate, 0.02,
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
                    {0.0085, 0.0120, 0.0102, 0.0095},
                    {0.0106, 0.0104, 0.0095, 0.0092},
                    {0.0104, 0.0099, 0.0092, 0.0088},
                    {0.0091, 0.0086, 0.0080, 0.0070},
                    {0.0077, 0.0073, 0.0068, 0.0060},
                    {0.0057, 0.0055, 0.0050, 0.0039}
            });

            atmVol = new Handle<>(new SwaptionVolatilityMatrix(calendar,
                    BusinessDayConvention.FOLLOWING,
                    atmOptionTenors,
                    atmSwapTenors,
                    m,
                    new Actual365Fixed(),
                    false,
                    Sarb.VolatilityType.Normal,
                    null), true);

            // Vol cubes
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
                    {-0.0016, -0.0008, 0.0000, 0.0009, 0.0038},
                    {0.0009, -0.0003, 0.0000, 0.0007, 0.0035},
                    {0.0025, 0.0002, 0.0000, 0.0002, 0.0024},
                    {-0.0009, -0.0003, 0.0000, 0.0003, 0.0013},
                    {-0.0001, -0.0001, 0.0000, 0.0001, 0.0007},
                    {0.0003, 0.0000, 0.0000, 0.0001, 0.0005},
                    {-0.0004, -0.0001, 0.0000, 0.0001, 0.0006},
                    {-0.0001, 0.0000, 0.0000, 0.0000, 0.0002},
                    {-0.0002, -0.0001, 0.0000, 0.0001, 0.0002}
            };

            List<List<Handle<Quote>>> volSpreads = new ArrayList<>(nRows);
            for (int i = 0; i < nRows; ++i) {
                List<Handle<Quote>> row = new ArrayList<>(nCols);
                for (int j = 0; j < nCols; ++j)
                    row.add(new Handle<>(
                            new SimpleQuote(volSpreadsMatrix[i][j]), true));
                volSpreads.add(row);
            }

            iborIndex = new Euribor6M(termStructure);
            /*
              ext::shared_ptr<SwapIndex> swapIndexBase(new
                    EuriborSwapIsdaFixA(10*Years, termStructure,termStructure));
              ext::shared_ptr<SwapIndex> shortSwapIndexBase(new
                    EuriborSwapIsdaFixA(2*Years, termStructure,termStructure));
            */

            SwapIndex swapIndexBase = new SwapIndex("swapIndexBase",
                    new Period(2, TimeUnit.YEARS),
                    iborIndex.fixingDays(),
                    iborIndex.currency(),
                    iborIndex.fixingCalendar(),
                    new Period(1, TimeUnit.YEARS),
                    BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.EurobondBasis), //EUR
                    iborIndex,
                    termStructure);
            SwapIndex shortSwapIndexBase = new SwapIndex("shortSwapIndexBase",
                    new Period(1, TimeUnit.YEARS),
                    iborIndex.fixingDays(),
                    iborIndex.currency(),
                    iborIndex.fixingCalendar(),
                    new Period(1, TimeUnit.YEARS),
                    BusinessDayConvention.MODIFIED_FOLLOWING,
                    new Thirty360(Thirty360.Convention.EurobondBasis), //EUR
                    iborIndex,
                    termStructure);

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
                row.add(new Handle<>(new SimpleQuote(0.01), true));
                row.add(new Handle<>(new SimpleQuote(0.0), true));
                row.add(new Handle<>(new SimpleQuote(0.3), true));
                row.add(new Handle<>(new SimpleQuote(0.5), true));
                guess.add(row);
            }
            boolean[] isParameterFixed = {false, true, false, false};

            // FIXME
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
                    NonParallelShifts);

            Handle<Quote> zeroMeanRev =
                    new Handle<>(new SimpleQuote(0.0), true);

            numericalPricers = new ArrayList<>();
            analyticPricers = new ArrayList<>();
            for (GFunctionFactory.YieldCurveModel yieldCurveModel
                    : yieldCurveModels) {
                numericalPricers.add(new NumericHaganPricer(
                        atmVol, yieldCurveModel, zeroMeanRev));
                analyticPricers.add(new AnalyticHaganPricer(
                        atmVol, yieldCurveModel, zeroMeanRev));
            }
        }
    }

    /**
     * Official {@code testFairRate}: flat-vol equivalence between the
     * numerical and analytic Hagan pricers (normal case).
     */
    @Test
    public void testFairRate() {
        CommonVars vars = new CommonVars();

        SwapIndex swapIndex = new SwapIndex("CMS10Y",
                new Period(10, TimeUnit.YEARS),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                new Thirty360(Thirty360.Convention.EurobondBasis), //EUR
                vars.iborIndex,
                vars.termStructure);
        // FIXME
        //ext::shared_ptr<SwapIndex> swapIndex(new
        //    EuriborSwapIsdaFixA(10*Years, vars.iborIndex->termStructure()));

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
            // The tolerance used before was 2bp. Semms very low for a
            // coupon with pmt in 20 Years
            double tol = 2.0e-4;

            String message = "\nCoupon payment date: " + paymentDate
                    + "\nCoupon start date:   " + startDate
                    + "\nCoupon floor:        " + rate(infiniteFloor)
                    + "\nCoupon gearing:      " + rate(gearing)
                    + "\nCoupon swap index:   " + swapIndex.name()
                    + "\nCoupon spread:       " + rate(spread)
                    + "\nCoupon cap:          " + rate(infiniteCap)
                    + "\nCoupon DayCounter:   " + vars.iborIndex.dayCounter()
                    + "\nYieldCurve Model:    " + vars.yieldCurveModels.get(j)
                    + "\nNumerical Pricer:    " + rate(rate0)
                    + "\nAnalytic Pricer:     " + rate(rate1)
                    + "\ndifference:          " + rate(difference)
                    + "\ntolerance:           " + rate(tol);

            // seems a more appropriate comparison to me instead of
            // difference > tol
            assertFalse(roundedToleranceExceeded(difference, tol), message);
        }
    }

    /**
     * Official {@code testCmsSwap}: flat-vol equivalence for CMS swaps
     * (normal case).
     */
    @Test
    public void testCmsSwap() {
        CommonVars vars = new CommonVars();

        SwapIndex swapIndex = new SwapIndex("CMS10Y",
                new Period(10, TimeUnit.YEARS),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                new Thirty360(Thirty360.Convention.EurobondBasis), //EUR
                vars.iborIndex,
                vars.termStructure);
        // FIXME
        //ext::shared_ptr<SwapIndex> swapIndex(new
        //    EuriborSwapIsdaFixA(10*Years, vars.iborIndex->termStructure()));

        double spread = 0.0;
        //?is an off-gridpoint a good test?
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

                String message = "\nLength in Years:  " + swapLengths[sl]
                        + "\nswap index:       " + swapIndex.name()
                        + "\nibor index:       " + vars.iborIndex.name()
                        + "\nspread:           " + rate(spread)
                        + "\nYieldCurve Model: " + vars.yieldCurveModels.get(j)
                        + "\nNumerical Pricer: " + rate(priceNum)
                        + "\nAnalytic Pricer:  " + rate(priceAn)
                        + "\ndifference:       " + rate(difference)
                        + "\ntolerance:        " + rate(tol);

                // seems a more appropriate comparison to me instead of
                // difference > tol
                assertFalse(roundedToleranceExceeded(difference, tol),
                        message);
            }
        }
    }

    /**
     * Official {@code testParity}: put-call parity for capped-floored CMS
     * coupons (normal case) over the ATM surface and both official cubes.
     */
    @Test
    public void testParity() {
        CommonVars vars = new CommonVars();

        List<Handle<SwaptionVolatilityStructure>> swaptionVols = List.of(
                vars.atmVol, vars.SabrVolCube1, vars.SabrVolCube2);

        SwapIndex swapIndex = new SwapIndex("CMS10Y",
                new Period(10, TimeUnit.YEARS),
                vars.iborIndex.fixingDays(),
                vars.iborIndex.currency(),
                vars.iborIndex.fixingCalendar(),
                new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                new Thirty360(Thirty360.Convention.EurobondBasis), //EUR
                vars.iborIndex,
                vars.termStructure);

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
        CappedFlooredCmsCoupon cpnPlain = new CappedFlooredCmsCoupon(
                paymentDate, nominal,
                startDate, endDate,
                swapIndex.fixingDays(),
                swapIndex,
                gearing, spread,
                infiniteCap, infiniteFloor,
                startDate, endDate,
                vars.iborIndex.dayCounter());

        for (double strike = -0.005; strike <= 0.035; strike += 0.01) {
            CappedFlooredCmsCoupon cpnCapped = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(),
                    swapIndex,
                    gearing, spread,
                    strike, infiniteFloor,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());
            CappedFlooredCmsCoupon cpnFloored = new CappedFlooredCmsCoupon(
                    paymentDate, nominal,
                    startDate, endDate,
                    swapIndex.fixingDays(),
                    swapIndex,
                    gearing, spread,
                    infiniteCap, strike,
                    startDate, endDate,
                    vars.iborIndex.dayCounter());

            for (Handle<SwaptionVolatilityStructure> swaptionVol
                    : swaptionVols) {
                for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
                    vars.numericalPricers.get(j)
                            .setSwaptionVolatility(swaptionVol);
                    vars.analyticPricers.get(j)
                            .setSwaptionVolatility(swaptionVol);
                    List<CmsCouponPricer> pricers = new ArrayList<>(2);
                    pricers.add(vars.numericalPricers.get(j));
                    pricers.add(vars.analyticPricers.get(j));
                    for (int k = 0; k < pricers.size(); ++k) {
                        cpnPlain.setPricer(pricers.get(k));
                        cpnCapped.setPricer(pricers.get(k));
                        cpnFloored.setPricer(pricers.get(k));
                        double cpnPlainPrice =
                                cpnPlain.price(vars.termStructure);
                        double cpnCappedPrice =
                                cpnCapped.price(vars.termStructure);
                        double cpnFlooredPrice =
                                cpnFloored.price(vars.termStructure);
                        double difference = Math.abs(
                                cpnCappedPrice + cpnFlooredPrice
                                        - cpnPlainPrice
                                        - nominal * strike
                                        * cpnPlain.accrualPeriod()
                                        * discount);
                        double tol = 4.0e-5;

                        String message = "\nDiscount Factor:     " + discount
                                + "\nCoupon payment date: " + paymentDate
                                + "\nCoupon start date:   " + startDate
                                + "\nCoupon gearing:      " + rate(gearing)
                                + "\nCoupon swap index:   " + swapIndex.name()
                                + "\nCoupon spread:       " + rate(spread)
                                + "\nstrike:              " + rate(strike)
                                + "\nCoupon DayCounter:   "
                                + vars.iborIndex.dayCounter()
                                + "\nYieldCurve Model:    "
                                + vars.yieldCurveModels.get(j)
                                + "\nPricerType:          "
                                + (k == 0 ? "Numerical Pricer"
                                : "Analytic Pricer")
                                + "\nPlain Coupon with rate=strike:       "
                                + nominal * strike
                                * cpnPlain.accrualPeriod() * discount
                                + "\nPlain Coupon price:       "
                                + rate(cpnPlainPrice)
                                + "\nCapped Coupon price:        "
                                + rate(cpnCappedPrice)
                                + "\nFloored Coupon price:      "
                                + rate(cpnFlooredPrice)
                                + "\ndifference:          " + difference
                                + "\ntolerance:           " + rate(tol);

                        assertFalse(difference > tol, message);
                    }
                }
            }
        }
    }

    /**
     * Official {@code std::round(10.0*(difference-tol))/10.0 > 0.0}
     * comparison used by {@code testFairRate} and {@code testCmsSwap}.
     */
    private static boolean roundedToleranceExceeded(double difference,
                                                    double tolerance) {
        return Math.round(10.0 * (difference - tolerance)) / 10.0 > 0.0;
    }

    /**
     * Official {@code io::rate} rendering: a percentage, {@code null} for the
     * official {@code Null<Real>()} values.
     */
    private static String rate(double value) {
        if (Double.isNaN(value))
            return "null";
        return String.format(Locale.ROOT, "%.2f %%", value * 100.0);
    }
}
