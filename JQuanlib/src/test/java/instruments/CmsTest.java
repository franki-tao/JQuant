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
import jquant.math.Matrix;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official QuantLib {@code test-suite/cms.cpp} port (ATM lognormal slice).
 *
 * <p>Ported from QuantLib at SHA {@code cf74c9f}: {@code testFairRate} and
 * {@code testCmsSwap} in full, and only the ATM branch of {@code testParity}.
 * The SABR cubes, CMS market calibration and the other tests of the official
 * file are deliberately not ported.
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
     * coupons (lognormal case), ATM volatility branch only.
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

            for (int j = 0; j < vars.yieldCurveModels.size(); ++j) {
                vars.numericalPricers.get(j).setSwaptionVolatility(vars.atmVol);
                vars.analyticPricers.get(j).setSwaptionVolatility(vars.atmVol);
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
