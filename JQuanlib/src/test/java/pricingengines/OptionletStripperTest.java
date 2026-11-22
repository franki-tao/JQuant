package pricingengines;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.CashflowUtil;
import jquant.cashflows.CompoundingOvernightIndexedCouponPricer;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.cashflows.OvernightLeg;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.OvernightIndex;
import jquant.indexes.Sofr;
import jquant.instruments.CapFloor;
import jquant.instruments.MakeCapFloor;
import jquant.math.Matrix;
import jquant.pricingengines.capfloor.BachelierCapFloorEngine;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.capfloor.CapFloorTermVolCurve;
import jquant.termstructures.volatility.capfloor.CapFloorTermVolSurface;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.volatility.optionlet.OptionletStripper1;
import jquant.termstructures.volatility.optionlet.OptionletStripper2;
import jquant.termstructures.volatility.optionlet.StrippedOptionletAdapter;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.DateGenerationRule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Optionlet stripping from QuantLib {@code test-suite/optionletstripper.cpp}.
 *
 * <p>Phase 2 ports the first two official cases:
 * {@code testFlatTermVolatilityStripping1} and
 * {@code testTermVolatilityStripping1}. Phase 3 adds
 * {@code testTermVolatilityStrippingNormalVol},
 * {@code testTermVolatilityStrippingShiftedLogNormalVol},
 * {@code testFlatTermVolatilityStripping2} and
 * {@code testTermVolatilityStripping2}. Cumulative official coverage is 6/19.
 */
public class OptionletStripperTest {

    private SavedSettings savedSettings_;

    @BeforeEach
    public void setUp() {
        savedSettings_ = new SavedSettings();
    }

    @AfterEach
    public void tearDown() {
        savedSettings_.restore();
        IndexManager.Instance.clearHistories();
    }

    private static class CommonVars {
        // global data
        Calendar calendar;
        DayCounter dayCounter;

        RelinkableHandle<YieldTermStructure> yieldTermStructure =
                new RelinkableHandle<>();
        RelinkableHandle<YieldTermStructure> discountingYTS =
                new RelinkableHandle<>();
        RelinkableHandle<YieldTermStructure> forwardingYTS =
                new RelinkableHandle<>();

        double[] strikes;
        List<Period> optionTenors;
        double[][] termV;

        double[] atmTermV;
        CapFloorTermVolCurve flatTermVolCurve;
        CapFloorTermVolCurve capFloorVolCurve;
        CapFloorTermVolSurface flatTermVolSurface;
        CapFloorTermVolSurface capFloorVolSurface;
        CapFloorTermVolSurface capFloorVolRealSurface;

        final double accuracy;
        final double tolerance;

        CommonVars() {
            accuracy = 1.0e-6;
            tolerance = 2.5e-8;
        }

        void setTermStructure() {
            calendar = new Target();
            dayCounter = new Actual365Fixed();

            double flatFwdRate = 0.04;
            yieldTermStructure.linkTo(
                    new FlatForward(0, calendar, flatFwdRate, dayCounter), true);
        }

        void setRealTermStructure() {
            calendar = new Target();
            dayCounter = new Actual365Fixed();

            int[] discountingDatesTmp = {
                    42124, 42129, 42143, 42221, 42254, 42282, 42313, 42345,
                    42374, 42405, 42465, 42495, 42587, 42681, 42772, 42860,
                    43227, 43956, 44321, 44686, 45051, 45418, 45782, 46147,
                    46512, 47609, 49436, 51263, 53087, 56739, 60392
            };

            List<Date> dates = new ArrayList<>();
            for (int serial : discountingDatesTmp)
                dates.add(new Date(serial));

            double[] rates = {
                    -0.00292, -0.00292, -0.001441, -0.00117, -0.001204,
                    -0.001212, -0.001223, -0.001236, -0.001221, -0.001238,
                    -0.001262, -0.00125, -0.001256, -0.001233, -0.00118,
                    -0.001108, -0.000619, 0.000833, 0.001617, 0.002414,
                    0.003183, 0.003883, 0.004514, 0.005074, 0.005606,
                    0.006856, 0.00813, 0.008709, 0.009136, 0.009601,
                    0.009384
            };

            discountingYTS.linkTo(new ZeroCurve(dates, rates, dayCounter), true);

            int[] forwardingDatesTmp = {
                    42124, 42313, 42436, 42556, 42618, 42800, 42830, 42860,
                    43227, 43591, 43956, 44321, 44686, 45051, 45418, 45782,
                    46147, 46512, 46878, 47245, 47609, 47973, 48339, 48704,
                    49069, 49436, 49800, 50165, 50530, 50895, 51263, 51627,
                    51991, 52356, 52722, 53087, 54913, 56739, 60392, 64045
            };

            dates = new ArrayList<>();
            for (int serial : forwardingDatesTmp)
                dates.add(new Date(serial));

            double[] forwardingRates = {
                    0.000649, 0.000649, 0.000684, 0.000717, 0.000745,
                    0.000872, 0.000905, 0.000954, 0.001532, 0.002319,
                    0.003147, 0.003949, 0.004743, 0.00551, 0.006198,
                    0.006798, 0.007339, 0.007832, 0.008242, 0.008614,
                    0.008935, 0.009205, 0.009443, 0.009651, 0.009818,
                    0.009952, 0.010054, 0.010146, 0.010206, 0.010266,
                    0.010315, 0.010365, 0.010416, 0.010468, 0.010519,
                    0.010571, 0.010757, 0.010806, 0.010423, 0.010217
            };

            forwardingYTS.linkTo(new ZeroCurve(dates, forwardingRates, dayCounter),
                    true);
        }

        void setFlatTermVolCurve() {
            setTermStructure();

            optionTenors = new ArrayList<>();
            for (int i = 0; i < 10; ++i)
                optionTenors.add(new Period(i + 1, TimeUnit.YEARS));

            double flatVol = .18;
            List<Handle<Quote>> curveVHandle = new ArrayList<>();
            for (int i = 0; i < optionTenors.size(); ++i)
                curveVHandle.add(new Handle<>(new SimpleQuote(flatVol), true));

            flatTermVolCurve = new CapFloorTermVolCurve(0, calendar,
                    BusinessDayConvention.FOLLOWING, optionTenors,
                    curveVHandle, dayCounter);
        }

        void setFlatTermVolSurface() {
            setTermStructure();

            optionTenors = new ArrayList<>();
            for (int i = 0; i < 10; ++i)
                optionTenors.add(new Period(i + 1, TimeUnit.YEARS));

            strikes = new double[10];
            for (int j = 0; j < strikes.length; ++j)
                strikes[j] = (j + 1) / 100.0;

            double flatVol = .18;
            termV = new double[optionTenors.size()][strikes.length];
            for (double[] row : termV)
                java.util.Arrays.fill(row, flatVol);

            flatTermVolSurface = new CapFloorTermVolSurface(0, calendar,
                    BusinessDayConvention.FOLLOWING, optionTenors, strikes,
                    new Matrix(termV), dayCounter);
        }

        void setCapFloorTermVolCurve() {
            setTermStructure();

            // atm cap volatility curve
            optionTenors = List.of(
                    new Period(1, TimeUnit.YEARS),
                    new Period(18, TimeUnit.MONTHS),
                    new Period(2, TimeUnit.YEARS),
                    new Period(3, TimeUnit.YEARS),
                    new Period(4, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(6, TimeUnit.YEARS),
                    new Period(7, TimeUnit.YEARS),
                    new Period(8, TimeUnit.YEARS),
                    new Period(9, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(12, TimeUnit.YEARS),
                    new Period(15, TimeUnit.YEARS),
                    new Period(20, TimeUnit.YEARS),
                    new Period(25, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));

            // atm capfloor vols from mkt vol matrix using flat yield curve
            atmTermV = new double[]{
                    0.090304, 0.12180, 0.13077, 0.14832, 0.15570, 0.15816,
                    0.15932, 0.16035, 0.15951, 0.15855, 0.15754, 0.15459,
                    0.15163, 0.14575, 0.14175, 0.13889
            };

            List<Handle<Quote>> atmTermVolHandle = new ArrayList<>();
            for (int i = 0; i < optionTenors.size(); ++i)
                atmTermVolHandle.add(new Handle<>(
                        new SimpleQuote(atmTermV[i]), true));

            capFloorVolCurve = new CapFloorTermVolCurve(0, calendar,
                    BusinessDayConvention.FOLLOWING, optionTenors,
                    atmTermVolHandle, dayCounter);
        }

        void setCapFloorTermVolSurface() {
            setTermStructure();

            // cap volatility smile matrix
            optionTenors = List.of(
                    new Period(1, TimeUnit.YEARS),
                    new Period(18, TimeUnit.MONTHS),
                    new Period(2, TimeUnit.YEARS),
                    new Period(3, TimeUnit.YEARS),
                    new Period(4, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(6, TimeUnit.YEARS),
                    new Period(7, TimeUnit.YEARS),
                    new Period(8, TimeUnit.YEARS),
                    new Period(9, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(12, TimeUnit.YEARS),
                    new Period(15, TimeUnit.YEARS),
                    new Period(20, TimeUnit.YEARS),
                    new Period(25, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));

            strikes = new double[]{
                    0.015, 0.0175, 0.02, 0.0225, 0.025, 0.03, 0.035,
                    0.04, 0.05, 0.06, 0.07, 0.08, 0.1
            };

            termV = new double[][]{
                    {0.287, 0.274, 0.256, 0.245, 0.227, 0.148, 0.096, 0.09, 0.11, 0.139, 0.166, 0.19, 0.214},
                    {0.303, 0.258, 0.22, 0.203, 0.19, 0.153, 0.126, 0.118, 0.147, 0.165, 0.18, 0.192, 0.212},
                    {0.303, 0.257, 0.216, 0.196, 0.182, 0.154, 0.134, 0.127, 0.149, 0.166, 0.18, 0.192, 0.212},
                    {0.305, 0.266, 0.226, 0.203, 0.19, 0.167, 0.151, 0.144, 0.16, 0.172, 0.183, 0.193, 0.209},
                    {0.294, 0.261, 0.216, 0.201, 0.19, 0.171, 0.158, 0.151, 0.163, 0.172, 0.181, 0.188, 0.201},
                    {0.276, 0.248, 0.212, 0.199, 0.189, 0.172, 0.16, 0.155, 0.162, 0.17, 0.177, 0.183, 0.195},
                    {0.26, 0.237, 0.21, 0.198, 0.188, 0.172, 0.161, 0.156, 0.161, 0.167, 0.173, 0.179, 0.19},
                    {0.25, 0.231, 0.208, 0.196, 0.187, 0.172, 0.162, 0.156, 0.16, 0.165, 0.17, 0.175, 0.185},
                    {0.244, 0.226, 0.206, 0.195, 0.186, 0.171, 0.161, 0.156, 0.158, 0.162, 0.166, 0.171, 0.18},
                    {0.239, 0.222, 0.204, 0.193, 0.185, 0.17, 0.16, 0.155, 0.156, 0.159, 0.163, 0.168, 0.177},
                    {0.235, 0.219, 0.202, 0.192, 0.183, 0.169, 0.159, 0.154, 0.154, 0.156, 0.16, 0.164, 0.173},
                    {0.227, 0.212, 0.197, 0.187, 0.179, 0.166, 0.156, 0.151, 0.149, 0.15, 0.153, 0.157, 0.165},
                    {0.22, 0.206, 0.192, 0.183, 0.175, 0.162, 0.153, 0.147, 0.144, 0.144, 0.147, 0.151, 0.158},
                    {0.211, 0.197, 0.185, 0.176, 0.168, 0.156, 0.147, 0.142, 0.138, 0.138, 0.14, 0.144, 0.151},
                    {0.204, 0.192, 0.18, 0.171, 0.164, 0.152, 0.143, 0.138, 0.134, 0.134, 0.137, 0.14, 0.148},
                    {0.2, 0.187, 0.176, 0.167, 0.16, 0.148, 0.14, 0.135, 0.131, 0.132, 0.135, 0.139, 0.146}
            };

            capFloorVolSurface = new CapFloorTermVolSurface(0, calendar,
                    BusinessDayConvention.FOLLOWING, optionTenors, strikes,
                    new Matrix(termV), dayCounter);
        }

        void setRealCapFloorTermVolSurface() {
            setRealTermStructure();

            // cap volatility smile matrix
            optionTenors = List.of(
                    new Period(1, TimeUnit.YEARS),
                    new Period(18, TimeUnit.MONTHS),
                    new Period(2, TimeUnit.YEARS),
                    new Period(3, TimeUnit.YEARS),
                    new Period(4, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(6, TimeUnit.YEARS),
                    new Period(7, TimeUnit.YEARS),
                    new Period(8, TimeUnit.YEARS),
                    new Period(9, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(12, TimeUnit.YEARS),
                    new Period(15, TimeUnit.YEARS),
                    new Period(20, TimeUnit.YEARS),
                    new Period(25, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS));

            strikes = new double[]{
                    -0.005, -0.0025, -0.00125, 0.0, 0.00125, 0.0025, 0.005,
                    0.01, 0.015, 0.02, 0.03, 0.05, 0.1
            };

            double[][] rawVols = {
                    {0.49, 0.39, 0.34, 0.31, 0.34, 0.37, 0.50, 0.75, 0.99, 1.21, 1.64, 2.44, 4.29},
                    {0.44, 0.36, 0.33, 0.31, 0.33, 0.35, 0.45, 0.65, 0.83, 1.00, 1.32, 1.93, 3.30},
                    {0.40, 0.35, 0.33, 0.31, 0.33, 0.34, 0.41, 0.55, 0.69, 0.82, 1.08, 1.56, 2.68},
                    {0.42, 0.39, 0.38, 0.37, 0.38, 0.39, 0.43, 0.54, 0.64, 0.74, 0.94, 1.31, 2.18},
                    {0.46, 0.43, 0.42, 0.41, 0.42, 0.43, 0.47, 0.56, 0.66, 0.75, 0.93, 1.28, 2.07},
                    {0.49, 0.47, 0.46, 0.45, 0.46, 0.47, 0.51, 0.59, 0.68, 0.76, 0.93, 1.25, 1.99},
                    {0.51, 0.49, 0.49, 0.48, 0.49, 0.50, 0.54, 0.62, 0.70, 0.78, 0.94, 1.24, 1.94},
                    {0.52, 0.51, 0.51, 0.51, 0.52, 0.53, 0.56, 0.63, 0.71, 0.79, 0.94, 1.23, 1.89},
                    {0.53, 0.52, 0.52, 0.52, 0.53, 0.54, 0.57, 0.65, 0.72, 0.79, 0.94, 1.21, 1.83},
                    {0.55, 0.54, 0.54, 0.54, 0.55, 0.56, 0.59, 0.66, 0.72, 0.79, 0.91, 1.15, 1.71},
                    {0.56, 0.56, 0.56, 0.56, 0.57, 0.58, 0.61, 0.67, 0.72, 0.78, 0.89, 1.09, 1.59},
                    {0.59, 0.58, 0.58, 0.59, 0.59, 0.60, 0.63, 0.68, 0.73, 0.78, 0.86, 1.03, 1.45},
                    {0.61, 0.61, 0.61, 0.61, 0.62, 0.62, 0.64, 0.69, 0.73, 0.77, 0.85, 1.02, 1.44},
                    {0.62, 0.62, 0.63, 0.63, 0.64, 0.64, 0.65, 0.69, 0.72, 0.76, 0.82, 0.96, 1.32},
                    {0.62, 0.63, 0.63, 0.63, 0.65, 0.66, 0.66, 0.68, 0.72, 0.74, 0.80, 0.93, 1.25},
                    {0.62, 0.62, 0.62, 0.62, 0.66, 0.67, 0.67, 0.67, 0.72, 0.72, 0.78, 0.90, 1.25}
            };

            termV = new double[rawVols.length][rawVols[0].length];
            for (int i = 0; i < rawVols.length; ++i)
                for (int j = 0; j < rawVols[i].length; ++j)
                    termV[i][j] = rawVols[i][j] / 100.0;

            capFloorVolRealSurface = new CapFloorTermVolSurface(0, calendar,
                    BusinessDayConvention.FOLLOWING, optionTenors, strikes,
                    new Matrix(termV), dayCounter);
        }
    }

    /** Official {@code CommonVarsON} from test-suite/optionletstripper.cpp. */
    private static class CommonVarsON {
        Date today;
        Date startDate, endDate;
        Period tenor;
        Calendar calendar;
        BusinessDayConvention convention;
        DayCounter dc;
        final RelinkableHandle<YieldTermStructure> sofrCurveHandle =
                new RelinkableHandle<>();
        double[] strikes;
        List<Period> expiries = new ArrayList<>();
        double[][] vols;
        CapFloorTermVolSurface capfloorVol;

        CommonVarsON() {
            today = new Date(15, Month.APRIL, 2025);
            startDate = new Date(17, Month.APRIL, 2025);
            endDate = new Date(17, Month.APRIL, 2030);
            tenor = new Period(3, TimeUnit.MONTHS);
            calendar = new UnitedStates(UnitedStates.Market.FederalReserve);
            convention = BusinessDayConvention.MODIFIED_FOLLOWING;
            dc = new Actual360();
        }

        void setSofrHandle() {
            List<Date> dates = List.of(
                    new Date(15, Month.APRIL, 2025),
                    new Date(16, Month.APRIL, 2025),
                    new Date(28, Month.APRIL, 2025),
                    new Date(21, Month.MAY, 2025),
                    new Date(21, Month.JULY, 2025),
                    new Date(21, Month.OCTOBER, 2025),
                    new Date(21, Month.APRIL, 2026),
                    new Date(21, Month.APRIL, 2027),
                    new Date(19, Month.APRIL, 2028),
                    new Date(22, Month.APRIL, 2030),
                    new Date(21, Month.APRIL, 2032),
                    new Date(19, Month.APRIL, 2035),
                    new Date(21, Month.APRIL, 2037),
                    new Date(19, Month.APRIL, 2040),
                    new Date(19, Month.APRIL, 2045),
                    new Date(20, Month.APRIL, 2050),
                    new Date(21, Month.APRIL, 2055));

            double[] zeroRates = {
                    3.039872 / 100.0, 3.082092 / 100.0, 3.67902 / 100.0,
                    3.791077 / 100.0, 4.147655 / 100.0, 4.498917 / 100.0,
                    4.688082 / 100.0, 4.486636 / 100.0, 4.228873 / 100.0,
                    3.949601 / 100.0, 3.814579 / 100.0, 3.731412 / 100.0,
                    3.718794 / 100.0, 3.704788 / 100.0, 3.599069 / 100.0,
                    3.401666 / 100.0, 3.221372 / 100.0};

            sofrCurveHandle.linkTo(
                    new ZeroCurve(dates, zeroRates, new Actual365Fixed(),
                            calendar), true);
        }

        void setFlatSofrCapFloorVolSurface() {
            strikes = new double[]{0.03, 0.035, 0.04};

            expiries = new ArrayList<>();
            for (int i = 1; i <= 10; ++i)
                expiries.add(new Period(i, TimeUnit.YEARS));

            vols = new double[expiries.size()][strikes.length];
            for (int i = 0; i < vols.length; ++i)
                for (int j = 0; j < vols[i].length; ++j)
                    vols[i][j] = 30.0 / 10000.0;

            capfloorVol = new CapFloorTermVolSurface(2, calendar, convention,
                    expiries, strikes, new Matrix(vols), dc);
        }

        void setNonFlatSofrCapFloorVolSurface() {
            strikes = new double[]{0.03, 0.035, 0.04};

            expiries = new ArrayList<>();
            for (int i = 1; i <= 10; ++i)
                expiries.add(new Period(i, TimeUnit.YEARS));

            double[] skewBps = {4.0, 2.0, 0.0};
            vols = new double[expiries.size()][strikes.length];
            for (int i = 0; i < vols.length; ++i)
                for (int j = 0; j < vols[i].length; ++j)
                    vols[i][j] = (25.0 + 1.5 * (i + 1) + skewBps[j]) / 10000.0;

            capfloorVol = new CapFloorTermVolSurface(2, calendar, convention,
                    expiries, strikes, new Matrix(vols), dc);
        }
    }

    private static double checkStripping(CommonVars vars,
                                         CapFloorTermVolSurface termVolSurface) {
        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);

        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                termVolSurface, iborIndex, null, vars.accuracy);

        StrippedOptionletAdapter strippedOptionletAdapter =
                new StrippedOptionletAdapter(optionletStripper1);

        Handle<OptionletVolatilityStructure> vol =
                new Handle<>(strippedOptionletAdapter, true);
        vol.currentLink().enableExtrapolation();

        BlackCapFloorEngine strippedVolEngine = BlackCapFloorEngine.withVolatility(
                vars.yieldTermStructure, vol, null);

        double maxError = 0.0;
        for (int tenorIndex = 0;
             tenorIndex < vars.optionTenors.size(); ++tenorIndex) {
            for (int strikeIndex = 0;
                 strikeIndex < vars.strikes.length; ++strikeIndex) {
                CapFloor cap = new MakeCapFloor(CapFloor.Type.Cap,
                        vars.optionTenors.get(tenorIndex), iborIndex)
                        .withStrike(vars.strikes[strikeIndex])
                        .withPricingEngine(strippedVolEngine)
                        .makeCapFloor();

                double priceFromStrippedVolatility = cap.NPV();

                BlackCapFloorEngine blackCapFloorEngineConstantVolatility =
                        new BlackCapFloorEngine(vars.yieldTermStructure,
                                vars.termV[tenorIndex][strikeIndex]);

                cap.setPricingEngine(blackCapFloorEngineConstantVolatility);
                double priceFromConstantVolatility = cap.NPV();

                double error = Math.abs(priceFromStrippedVolatility
                        - priceFromConstantVolatility);
                maxError = Math.max(maxError, error);
                if (error > vars.tolerance)
                    fail("\noption tenor:       "
                            + vars.optionTenors.get(tenorIndex)
                            + "\nstrike:             " + vars.strikes[strikeIndex]
                            + "\nstripped vol price: " + priceFromStrippedVolatility
                            + "\nconstant vol price: " + priceFromConstantVolatility
                            + "\nerror:              " + error
                            + "\ntolerance:          " + vars.tolerance);
            }
        }
        return maxError;
    }

    @Test
    public void testFlatTermVolatilityStripping1() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));

        vars.setFlatTermVolSurface();

        double maxError = checkStripping(vars, vars.flatTermVolSurface);
        System.out.println("testFlatTermVolatilityStripping1 max error: " + maxError);
    }

    @Test
    public void testTermVolatilityStripping1() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));

        vars.setCapFloorTermVolSurface();

        double maxError = checkStripping(vars, vars.capFloorVolSurface);
        System.out.println("testTermVolatilityStripping1 max error: " + maxError);
    }

    @Test
    public void testTermVolatilityStrippingNormalVol() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(30, Month.APRIL, 2015));

        vars.setRealCapFloorTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.forwardingYTS);

        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                vars.capFloorVolRealSurface, iborIndex, null, vars.accuracy,
                100, vars.discountingYTS, Sarb.VolatilityType.Normal,
                0.0, false, 0);

        StrippedOptionletAdapter strippedOptionletAdapter =
                new StrippedOptionletAdapter(optionletStripper1);

        Handle<OptionletVolatilityStructure> vol =
                new Handle<>(strippedOptionletAdapter, true);
        vol.currentLink().enableExtrapolation();

        BachelierCapFloorEngine strippedVolEngine =
                BachelierCapFloorEngine.withVolatility(vars.discountingYTS, vol);

        double maxError = 0.0;
        for (int tenorIndex = 0;
             tenorIndex < vars.optionTenors.size(); ++tenorIndex) {
            for (int strikeIndex = 0;
                 strikeIndex < vars.strikes.length; ++strikeIndex) {
                CapFloor cap = new MakeCapFloor(CapFloor.Type.Cap,
                        vars.optionTenors.get(tenorIndex), iborIndex)
                        .withStrike(vars.strikes[strikeIndex])
                        .withPricingEngine(strippedVolEngine)
                        .makeCapFloor();

                double priceFromStrippedVolatility = cap.NPV();

                BachelierCapFloorEngine bachelierCapFloorEngineConstantVolatility =
                        new BachelierCapFloorEngine(vars.discountingYTS,
                                vars.termV[tenorIndex][strikeIndex]);

                cap.setPricingEngine(bachelierCapFloorEngineConstantVolatility);
                double priceFromConstantVolatility = cap.NPV();

                double error = Math.abs(priceFromStrippedVolatility
                        - priceFromConstantVolatility);
                maxError = Math.max(maxError, error);
                if (error > vars.tolerance)
                    fail("\noption tenor:       "
                            + vars.optionTenors.get(tenorIndex)
                            + "\nstrike:             " + vars.strikes[strikeIndex]
                            + "\nstripped vol price: " + priceFromStrippedVolatility
                            + "\nconstant vol price: " + priceFromConstantVolatility
                            + "\nerror:              " + error
                            + "\ntolerance:          " + vars.tolerance);
            }
        }
        System.out.println("testTermVolatilityStrippingNormalVol max error: "
                + maxError);
    }

    @Test
    public void testTermVolatilityStrippingShiftedLogNormalVol() {
        CommonVars vars = new CommonVars();
        double shift = 0.03;
        Settings.instance.evaluationDate().equal(new Date(30, Month.APRIL, 2015));

        vars.setRealCapFloorTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.forwardingYTS);

        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                vars.capFloorVolRealSurface, iborIndex, null, vars.accuracy,
                100, vars.discountingYTS,
                Sarb.VolatilityType.ShiftedLognormal, shift, true, 0);

        StrippedOptionletAdapter strippedOptionletAdapter =
                new StrippedOptionletAdapter(optionletStripper1);

        Handle<OptionletVolatilityStructure> vol =
                new Handle<>(strippedOptionletAdapter, true);
        vol.currentLink().enableExtrapolation();

        BlackCapFloorEngine strippedVolEngine =
                BlackCapFloorEngine.withVolatility(vars.discountingYTS, vol, null);

        double maxError = 0.0;
        for (int strikeIndex = 0;
             strikeIndex < vars.strikes.length; ++strikeIndex) {
            for (int tenorIndex = 0;
                 tenorIndex < vars.optionTenors.size(); ++tenorIndex) {
                CapFloor cap = new MakeCapFloor(CapFloor.Type.Cap,
                        vars.optionTenors.get(tenorIndex), iborIndex)
                        .withStrike(vars.strikes[strikeIndex])
                        .withPricingEngine(strippedVolEngine)
                        .makeCapFloor();

                double priceFromStrippedVolatility = cap.NPV();

                BlackCapFloorEngine blackCapFloorEngineConstantVolatility =
                        new BlackCapFloorEngine(vars.discountingYTS,
                                vars.termV[tenorIndex][strikeIndex],
                                vars.capFloorVolRealSurface.dayCounter(), shift);

                cap.setPricingEngine(blackCapFloorEngineConstantVolatility);
                double priceFromConstantVolatility = cap.NPV();

                double error = Math.abs(priceFromStrippedVolatility
                        - priceFromConstantVolatility);
                maxError = Math.max(maxError, error);
                if (error > vars.tolerance)
                    fail("\noption tenor:       "
                            + vars.optionTenors.get(tenorIndex)
                            + "\nstrike:             " + vars.strikes[strikeIndex]
                            + "\nstripped vol price: " + priceFromStrippedVolatility
                            + "\nconstant vol price: " + priceFromConstantVolatility
                            + "\nerror:              " + error
                            + "\ntolerance:          " + vars.tolerance);
            }
        }
        System.out.println(
                "testTermVolatilityStrippingShiftedLogNormalVol max error: "
                        + maxError);
    }

    @Test
    public void testFlatTermVolatilityStripping2() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));

        vars.setFlatTermVolCurve();
        vars.setFlatTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);

        // optionletstripper1
        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                vars.flatTermVolSurface, iborIndex, null, vars.accuracy);

        StrippedOptionletAdapter strippedOptionletAdapter1 =
                new StrippedOptionletAdapter(optionletStripper1);

        Handle<OptionletVolatilityStructure> vol1 =
                new Handle<>(strippedOptionletAdapter1, true);
        vol1.currentLink().enableExtrapolation();

        // optionletstripper2
        OptionletStripper2 optionletStripper2 = new OptionletStripper2(
                optionletStripper1, vars.flatTermVolCurve);

        StrippedOptionletAdapter strippedOptionletAdapter2 =
                new StrippedOptionletAdapter(optionletStripper2);

        Handle<OptionletVolatilityStructure> vol2 =
                new Handle<>(strippedOptionletAdapter2, true);
        vol2.currentLink().enableExtrapolation();

        double maxError = 0.0;
        // consistency check: diff(stripped vol1-stripped vol2)
        for (int strikeIndex = 0;
             strikeIndex < vars.strikes.length; ++strikeIndex) {
            for (int tenorIndex = 0;
                 tenorIndex < vars.optionTenors.size(); ++tenorIndex) {
                double strippedVol1 = vol1.currentLink().volatility(
                        vars.optionTenors.get(tenorIndex),
                        vars.strikes[strikeIndex], true);
                double strippedVol2 = vol2.currentLink().volatility(
                        vars.optionTenors.get(tenorIndex),
                        vars.strikes[strikeIndex], true);

                // vol from flat vol surface (for comparison only)
                double flatVol = vars.flatTermVolSurface.volatility(
                        vars.optionTenors.get(tenorIndex),
                        vars.strikes[strikeIndex], true);

                double error = Math.abs(strippedVol1 - strippedVol2);
                maxError = Math.max(maxError, error);
                if (error > vars.tolerance)
                    fail("\noption tenor:  "
                            + vars.optionTenors.get(tenorIndex)
                            + "\nstrike:        " + vars.strikes[strikeIndex]
                            + "\nstripped vol1: " + strippedVol1
                            + "\nstripped vol2: " + strippedVol2
                            + "\nflat vol:      " + flatVol
                            + "\nerror:         " + error
                            + "\ntolerance:     " + vars.tolerance);
            }
        }
        System.out.println("testFlatTermVolatilityStripping2 max error: "
                + maxError);
    }

    @Test
    public void testTermVolatilityStripping2() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(30, Month.APRIL, 2015));

        vars.setCapFloorTermVolCurve();
        vars.setCapFloorTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);

        // optionletstripper1
        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                vars.capFloorVolSurface, iborIndex, null, vars.accuracy);

        StrippedOptionletAdapter strippedOptionletAdapter1 =
                new StrippedOptionletAdapter(optionletStripper1);

        Handle<OptionletVolatilityStructure> vol1 =
                new Handle<>(strippedOptionletAdapter1, true);
        vol1.currentLink().enableExtrapolation();

        // optionletstripper2
        OptionletStripper2 optionletStripper2 = new OptionletStripper2(
                optionletStripper1, vars.capFloorVolCurve);

        StrippedOptionletAdapter strippedOptionletAdapter2 =
                new StrippedOptionletAdapter(optionletStripper2);

        Handle<OptionletVolatilityStructure> vol2 =
                new Handle<>(strippedOptionletAdapter2, true);
        vol2.currentLink().enableExtrapolation();

        double maxError = 0.0;
        // consistency check: diff(stripped vol1-stripped vol2)
        for (int strikeIndex = 0;
             strikeIndex < vars.strikes.length; ++strikeIndex) {
            for (int tenorIndex = 0;
                 tenorIndex < vars.optionTenors.size(); ++tenorIndex) {
                double strippedVol1 = vol1.currentLink().volatility(
                        vars.optionTenors.get(tenorIndex),
                        vars.strikes[strikeIndex], true);
                double strippedVol2 = vol2.currentLink().volatility(
                        vars.optionTenors.get(tenorIndex),
                        vars.strikes[strikeIndex], true);

                // vol from flat vol surface (for comparison only)
                double flatVol = vars.capFloorVolSurface.volatility(
                        vars.optionTenors.get(tenorIndex),
                        vars.strikes[strikeIndex], true);

                double error = Math.abs(strippedVol1 - strippedVol2);
                maxError = Math.max(maxError, error);
                if (error > vars.tolerance)
                    fail("\noption tenor:  "
                            + vars.optionTenors.get(tenorIndex)
                            + "\nstrike:        " + vars.strikes[strikeIndex]
                            + "\nstripped vol1: " + strippedVol1
                            + "\nstripped vol2: " + strippedVol2
                            + "\nflat vol:      " + flatVol
                            + "\nerror:         " + error
                            + "\ntolerance:     " + vars.tolerance);
            }
        }
        System.out.println("testTermVolatilityStripping2 max error: " + maxError);
    }

    @Test
    public void testSwitchStrike() {
        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setCapFloorTermVolSurface();

        RelinkableHandle<YieldTermStructure> yieldTermStructure =
                new RelinkableHandle<>();
        yieldTermStructure.linkTo(new FlatForward(0, vars.calendar, 0.03,
                vars.dayCounter), true);

        IborIndex iborIndex = new Euribor6M(yieldTermStructure);

        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                vars.capFloorVolSurface, iborIndex, null, vars.accuracy);

        double expected = usingAtParCoupons ? 0.02981223 : 0.02981258;

        double error = Math.abs(optionletStripper1.switchStrike() - expected);
        double firstError = error;
        if (error > vars.tolerance)
            fail("\nSwitchstrike not correctly computed:  "
                    + "\nexpected switch strike: " + expected
                    + "\ncomputed switch strike: "
                    + optionletStripper1.switchStrike()
                    + "\nerror:         " + error
                    + "\ntolerance:     " + vars.tolerance);

        yieldTermStructure.linkTo(new FlatForward(0, vars.calendar, 0.05,
                vars.dayCounter), true);

        expected = usingAtParCoupons ? 0.0499371 : 0.0499381;

        error = Math.abs(optionletStripper1.switchStrike() - expected);
        if (error > vars.tolerance)
            fail("\nSwitchstrike not correctly computed:  "
                    + "\nexpected switch strike: " + expected
                    + "\ncomputed switch strike: "
                    + optionletStripper1.switchStrike()
                    + "\nerror:         " + error
                    + "\ntolerance:     " + vars.tolerance);

        System.out.println("testSwitchStrike max error: "
                + Math.max(firstError, error));
    }

    private static double checkIborOptionletStripping(
            CommonVars vars,
            CapFloorTermVolSurface surface,
            int paymentLag) {
        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);

        OptionletStripper1 optionletStripper1 = new OptionletStripper1(
                surface, iborIndex, null, vars.accuracy, 100, null,
                Sarb.VolatilityType.ShiftedLognormal, 0.0, false, paymentLag);

        StrippedOptionletAdapter strippedOptionletAdapter =
                new StrippedOptionletAdapter(optionletStripper1);

        Handle<OptionletVolatilityStructure> vol =
                new Handle<>(strippedOptionletAdapter, true);
        vol.currentLink().enableExtrapolation();

        BlackCapFloorEngine strippedVolEngine =
                BlackCapFloorEngine.withVolatility(vars.yieldTermStructure,
                        vol, null);

        Date startDate = iborIndex.valueDate(
                iborIndex.fixingCalendar().adjust(
                        Settings.instance.evaluationDate().Date(),
                        BusinessDayConvention.FOLLOWING));

        double maxError = 0.0;
        for (int tenorIndex = 0;
             tenorIndex < vars.optionTenors.size(); ++tenorIndex) {
            Date endDate = startDate.add(vars.optionTenors.get(tenorIndex));
            Schedule schedule = new Schedule(startDate, endDate,
                    iborIndex.tenor(), iborIndex.fixingCalendar(),
                    iborIndex.businessDayConvention(),
                    iborIndex.businessDayConvention(),
                    DateGenerationRule.BACKWARD, false,
                    new Date(), new Date());
            List<jquant.CashFlow> leg = new IborLeg(schedule, iborIndex)
                    .withNotionals(1.0)
                    .withPaymentDayCounter(iborIndex.dayCounter())
                    .withPaymentAdjustment(iborIndex.businessDayConvention())
                    .withPaymentLag(paymentLag)
                    .leg();

            for (int strikeIndex = 0;
                 strikeIndex < vars.strikes.length; ++strikeIndex) {
                List<Double> strikes =
                        List.of(vars.strikes[strikeIndex]);

                CapFloor strippedCap = new CapFloor(CapFloor.Type.Cap, leg,
                        strikes);
                strippedCap.setPricingEngine(strippedVolEngine);
                double priceFromStrippedVolatility = strippedCap.NPV();

                BlackCapFloorEngine constantVolEngine =
                        new BlackCapFloorEngine(vars.yieldTermStructure,
                                vars.termV[tenorIndex][strikeIndex]);
                CapFloor cap = new CapFloor(CapFloor.Type.Cap, leg, strikes);
                cap.setPricingEngine(constantVolEngine);
                double priceFromConstantVolatility = cap.NPV();

                double error = Math.abs(priceFromStrippedVolatility
                        - priceFromConstantVolatility);
                maxError = Math.max(maxError, error);
                if (error > vars.tolerance)
                    fail("\noption tenor:       "
                            + vars.optionTenors.get(tenorIndex)
                            + "\nstrike:             " + vars.strikes[strikeIndex]
                            + "\nstripped vol price: " + priceFromStrippedVolatility
                            + "\nconstant vol price: " + priceFromConstantVolatility
                            + "\nerror:              " + error
                            + "\ntolerance:          " + vars.tolerance);
            }
        }
        return maxError;
    }

    @Test
    public void testFlatTermVolatilityStripping1IborPaymentLag() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setFlatTermVolSurface();

        double maxError = checkIborOptionletStripping(
                vars, vars.flatTermVolSurface, 2);
        System.out.println(
                "testFlatTermVolatilityStripping1IborPaymentLag max error: "
                        + maxError);
    }

    @Test
    public void testTermVolatilityStripping1IborPaymentLag() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setCapFloorTermVolSurface();

        double maxError = checkIborOptionletStripping(
                vars, vars.capFloorVolSurface, 2);
        System.out.println(
                "testTermVolatilityStripping1IborPaymentLag max error: "
                        + maxError);
    }

    @Test
    public void testIborReferenceDateCompatibility() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setFlatTermVolSurface();

        CapFloorTermVolSurface surface = new CapFloorTermVolSurface(2,
                vars.calendar, BusinessDayConvention.FOLLOWING,
                vars.optionTenors, vars.strikes, new Matrix(vars.termV),
                vars.dayCounter);
        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);
        OptionletStripper1 stripper1 = new OptionletStripper1(
                surface, iborIndex, null, vars.accuracy);

        SimpleQuote volQuote = new SimpleQuote();
        Handle<Quote> volHandle = new Handle<>(volQuote, true);
        BlackCapFloorEngine legacyEngine = new BlackCapFloorEngine(
                vars.yieldTermStructure, volHandle, vars.dayCounter);
        double[][] capFloorPrices = stripper1.capFloorPrices();
        Period capFloorLength = new Period(
                2 * iborIndex.tenor().length(), iborIndex.tenor().units());
        double maxError = 0.0;
        for (int i = 0; i < capFloorPrices.length; ++i) {
            for (int j = 0; j < vars.strikes.length; ++j) {
                volQuote.setValue(surface.volatility(capFloorLength,
                        vars.strikes[j], true));
                CapFloor.Type type =
                        vars.strikes[j] < stripper1.switchStrike()
                                ? CapFloor.Type.Floor : CapFloor.Type.Cap;
                CapFloor legacyCap = new MakeCapFloor(type, capFloorLength,
                        iborIndex)
                        .withStrike(vars.strikes[j])
                        .withPricingEngine(legacyEngine)
                        .makeCapFloor();
                double error = Math.abs(
                        capFloorPrices[i][j] - legacyCap.NPV());
                maxError = Math.max(maxError, error);
                if (error > 1.0e-12)
                    fail("\ncapFloorPrices mismatch"
                            + "\noptionlet row: " + i
                            + "\nstrike:        " + vars.strikes[j]
                            + "\nstored price:  " + capFloorPrices[i][j]
                            + "\nlegacy price:  " + legacyCap.NPV()
                            + "\nerror:         " + error
                            + "\ntolerance:     1.0e-12");
            }
            capFloorLength = TimeUtils.add(capFloorLength, iborIndex.tenor());
        }

        List<Handle<Quote>> atmVols = new ArrayList<>();
        for (int i = 0; i < vars.optionTenors.size(); ++i)
            atmVols.add(new Handle<>(new SimpleQuote(0.18), true));
        CapFloorTermVolCurve atmCurve = new CapFloorTermVolCurve(2,
                vars.calendar, BusinessDayConvention.FOLLOWING,
                vars.optionTenors, atmVols, vars.dayCounter);
        OptionletStripper2 stripper2 = new OptionletStripper2(
                stripper1, atmCurve);
        double[] atmStrikes = stripper2.atmCapFloorStrikes();
        double[] atmPrices = stripper2.atmCapFloorPrices();
        volQuote.setValue(0.18);
        for (int i = 0; i < vars.optionTenors.size(); ++i) {
            CapFloor legacyCap = new MakeCapFloor(CapFloor.Type.Cap,
                    vars.optionTenors.get(i), iborIndex)
                    .withStrike(atmStrikes[i])
                    .withPricingEngine(legacyEngine)
                    .makeCapFloor();
            double error = Math.abs(atmPrices[i] - legacyCap.NPV());
            maxError = Math.max(maxError, error);
            if (error > 1.0e-12)
                fail("\nATM cap price mismatch"
                        + "\noption tenor:  " + vars.optionTenors.get(i)
                        + "\nATM strike:    " + atmStrikes[i]
                        + "\nstored price:  " + atmPrices[i]
                        + "\nlegacy price:  " + legacyCap.NPV()
                        + "\nerror:         " + error
                        + "\ntolerance:     1.0e-12");
        }
        System.out.println("testIborReferenceDateCompatibility max error: "
                + maxError);
    }

    @Test
    public void testTermVolatilityStripping2IborPaymentLag() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setFlatTermVolCurve();
        vars.setFlatTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);
        OptionletStripper1 stripper1 = new OptionletStripper1(
                vars.flatTermVolSurface, iborIndex, null, vars.accuracy, 100,
                null, Sarb.VolatilityType.ShiftedLognormal, 0.0, false, 2);
        OptionletStripper2 stripper2 = new OptionletStripper2(
                stripper1, vars.flatTermVolCurve);

        // The flat ATM curve and flat surface imply an exact zero adjustment;
        // the tight price tolerance below therefore isolates leg conventions.
        double maxSpread = 0.0;
        for (double spread : stripper2.spreadsVol()) {
            maxSpread = Math.max(maxSpread, Math.abs(spread));
            if (Math.abs(spread) > 1.0e-12)
                fail("\nspread is not zero"
                        + "\nspread:    " + spread
                        + "\ntolerance: 1.0e-12");
        }

        StrippedOptionletAdapter strippedOptionletAdapter =
                new StrippedOptionletAdapter(stripper2);
        Handle<OptionletVolatilityStructure> vol =
                new Handle<>(strippedOptionletAdapter, true);
        vol.currentLink().enableExtrapolation();
        BlackCapFloorEngine engine = BlackCapFloorEngine.withVolatility(
                vars.yieldTermStructure, vol, null);

        double[] strikes = stripper2.atmCapFloorStrikes();
        double[] targetPrices = stripper2.atmCapFloorPrices();
        Date startDate = iborIndex.valueDate(
                iborIndex.fixingCalendar().adjust(
                        Settings.instance.evaluationDate().Date(),
                        BusinessDayConvention.FOLLOWING));

        double maxError = 0.0;
        for (int i = 0; i < vars.optionTenors.size(); ++i) {
            Date endDate = startDate.add(vars.optionTenors.get(i));
            Schedule schedule = new Schedule(startDate, endDate,
                    iborIndex.tenor(), iborIndex.fixingCalendar(),
                    iborIndex.businessDayConvention(),
                    iborIndex.businessDayConvention(),
                    DateGenerationRule.BACKWARD, false,
                    new Date(), new Date());
            List<jquant.CashFlow> leg = new IborLeg(schedule, iborIndex)
                    .withNotionals(1.0)
                    .withPaymentDayCounter(iborIndex.dayCounter())
                    .withPaymentAdjustment(iborIndex.businessDayConvention())
                    .withPaymentLag(2)
                    .leg();
            leg.remove(0);

            CapFloor cap = new CapFloor(CapFloor.Type.Cap, leg,
                    List.of(strikes[i]));
            cap.setPricingEngine(engine);
            double error = Math.abs(cap.NPV() - targetPrices[i]);
            maxError = Math.max(maxError, error);
            if (error > vars.tolerance)
                fail("\noption tenor: " + vars.optionTenors.get(i)
                        + "\nATM strike:   " + strikes[i]
                        + "\ntarget price: " + targetPrices[i]
                        + "\nstripper price: " + cap.NPV()
                        + "\nerror:        " + error
                        + "\ntolerance:    " + vars.tolerance);
        }
        System.out.println(
                "testTermVolatilityStripping2IborPaymentLag max error: "
                        + maxError + ", max spread: " + maxSpread);
    }

    @Test
    public void testTermVolatilityStripping2IborFrontStub() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setFlatTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);
        OptionletStripper1 stripper1 = new OptionletStripper1(
                vars.flatTermVolSurface, iborIndex, null, vars.accuracy);

        List<Period> tenors = List.of(
                new Period(9, TimeUnit.MONTHS),
                new Period(1, TimeUnit.YEARS));
        List<Handle<Quote>> atmVols = List.of(
                new Handle<>(new SimpleQuote(0.19), true),
                new Handle<>(new SimpleQuote(0.19), true));
        CapFloorTermVolCurve atmCurve = new CapFloorTermVolCurve(0,
                vars.calendar, BusinessDayConvention.FOLLOWING, tenors,
                atmVols, vars.dayCounter);
        OptionletStripper2 stripper2 = new OptionletStripper2(
                stripper1, atmCurve);
        // Front-stub caps are not on the optionlet grid, but the historical
        // behaviour is to strip them anyway rather than throw.
        double[] targetPrices = stripper2.atmCapFloorPrices();
        for (double spread : stripper2.spreadsVol())
            if (!Double.isFinite(spread))
                fail("\nnon-finite spread: " + spread);

        StrippedOptionletAdapter strippedOptionletAdapter =
                new StrippedOptionletAdapter(stripper2);
        Handle<OptionletVolatilityStructure> vol =
                new Handle<>(strippedOptionletAdapter, true);
        vol.currentLink().enableExtrapolation();
        BlackCapFloorEngine engine = BlackCapFloorEngine.withVolatility(
                vars.yieldTermStructure, vol, null);

        double[] strikes = stripper2.atmCapFloorStrikes();
        CapFloor cap = new MakeCapFloor(CapFloor.Type.Cap, tenors.get(0),
                iborIndex)
                .withStrike(strikes[0])
                .withPricingEngine(engine)
                .makeCapFloor();
        double error = Math.abs(cap.NPV() - targetPrices[0]);
        if (error > vars.tolerance)
            fail("\nfront-stub cap price mismatch"
                    + "\noption tenor:   " + tenors.get(0)
                    + "\nATM strike:     " + strikes[0]
                    + "\ntarget price:   " + targetPrices[0]
                    + "\nstripper price: " + cap.NPV()
                    + "\nerror:          " + error
                    + "\ntolerance:      " + vars.tolerance);
        System.out.println(
                "testTermVolatilityStripping2IborFrontStub max error: "
                        + error);
    }

    @Test
    public void testOptionletStripper2RejectsEmptyIborCap() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setFlatTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);
        OptionletStripper1 stripper1 = new OptionletStripper1(
                vars.flatTermVolSurface, iborIndex, null, vars.accuracy);

        List<Period> tenors = List.of(
                new Period(6, TimeUnit.MONTHS),
                new Period(1, TimeUnit.YEARS));
        List<Handle<Quote>> atmVols = List.of(
                new Handle<>(new SimpleQuote(0.18), true),
                new Handle<>(new SimpleQuote(0.18), true));
        CapFloorTermVolCurve atmCurve = new CapFloorTermVolCurve(0,
                vars.calendar, BusinessDayConvention.FOLLOWING, tenors,
                atmVols, vars.dayCounter);
        OptionletStripper2 stripper2 = new OptionletStripper2(
                stripper1, atmCurve);
        try {
            stripper2.atmCapFloorPrices();
            fail("expected the empty 6M Ibor cap to be rejected");
        } catch (NullPointerException e) {
            fail("empty cap raised NullPointerException instead of the "
                    + "official QL_REQUIRE error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            // QuantLib rejects the 6M leg in makeCapFloorLeg before reaching
            // OptionletStripper2's own "empty cap/floor leg for option expiry"
            // check; both are official QL_REQUIRE errors.
            if (message == null
                    || !(message.contains(
                            "empty cap/floor leg for option expiry")
                    || message.contains("does not contain any optionlets")))
                fail("unexpected empty-cap error message: " + message);
        }
    }

    @Test
    public void testOptionletStripper2ToleratesMismatchedReferenceDate() {
        CommonVars vars = new CommonVars();
        Settings.instance.evaluationDate().equal(new Date(28, Month.OCTOBER, 2013));
        vars.setFlatTermVolSurface();

        IborIndex iborIndex = new Euribor6M(vars.yieldTermStructure);
        OptionletStripper1 stripper1 = new OptionletStripper1(
                vars.flatTermVolSurface, iborIndex, null, vars.accuracy);

        List<Handle<Quote>> atmVols = new ArrayList<>();
        for (int i = 0; i < vars.optionTenors.size(); ++i)
            atmVols.add(new Handle<>(new SimpleQuote(0.18), true));
        // different settlement days than the source surface
        CapFloorTermVolCurve atmCurve = new CapFloorTermVolCurve(2,
                vars.calendar, BusinessDayConvention.FOLLOWING,
                vars.optionTenors, atmVols, vars.dayCounter);
        OptionletStripper2 stripper2 = new OptionletStripper2(
                stripper1, atmCurve);
        try {
            double[] prices = stripper2.atmCapFloorPrices();
            if (prices.length != vars.optionTenors.size())
                fail("unexpected ATM price count: " + prices.length);
        } catch (RuntimeException e) {
            fail("mismatched reference date raised " + e.getClass().getName()
                    + ": " + e.getMessage());
        }
    }

    private static double checkSofrOptionletStripping(boolean flatVol,
                                                      int paymentLag) {
        CommonVarsON vars = new CommonVarsON();
        Settings.instance.evaluationDate().equal(vars.today);
        vars.setSofrHandle();
        if (flatVol)
            vars.setFlatSofrCapFloorVolSurface();
        else
            vars.setNonFlatSofrCapFloorVolSurface();

        OvernightIndex sofrIndex = new Sofr(vars.sofrCurveHandle);
        sofrIndex.addFixing(new Date(15, Month.APRIL, 2025), 3.04 / 100.0,
                false);

        double notional = 1.0;

        OptionletStripper1 optionletSurf = new OptionletStripper1(
                vars.capfloorVol, sofrIndex, null, 1e-6, 100,
                vars.sofrCurveHandle, Sarb.VolatilityType.Normal, 0.0, false,
                vars.tenor, paymentLag);

        Handle<OptionletVolatilityStructure> ovsHandle = new Handle<>(
                new StrippedOptionletAdapter(optionletSurf), true);

        BachelierCapFloorEngine strippedVolEngine =
                BachelierCapFloorEngine.withVolatility(
                        vars.sofrCurveHandle, ovsHandle);

        double tolerance = 2.5e-8;
        double maxError = 0.0;

        for (int tenorIndex = 0; tenorIndex < vars.expiries.size();
             ++tenorIndex) {
            Date endDate = vars.calendar.advance(vars.startDate,
                    vars.expiries.get(tenorIndex), vars.convention);
            Schedule schedule = new Schedule(vars.startDate, endDate,
                    vars.tenor, vars.calendar, vars.convention,
                    vars.convention, DateGenerationRule.FORWARD, false,
                    new Date(), new Date());
            List<jquant.CashFlow> leg = new OvernightLeg(schedule, sofrIndex)
                    .withNotionals(notional)
                    .withPaymentAdjustment(
                            BusinessDayConvention.MODIFIED_FOLLOWING)
                    .withPaymentLag(paymentLag)
                    .build();
            CashflowUtil.setCouponPricer(leg,
                    new CompoundingOvernightIndexedCouponPricer());

            for (int strikeIndex = 0; strikeIndex < vars.strikes.length;
                 ++strikeIndex) {
                List<Double> strikes = List.of(vars.strikes[strikeIndex]);

                CapFloor strippedCap = new CapFloor(CapFloor.Type.Cap, leg,
                        strikes);
                strippedCap.setPricingEngine(strippedVolEngine);
                double strippedCapPrice = strippedCap.NPV();

                SimpleQuote quotedVol = new SimpleQuote(
                        vars.vols[tenorIndex][strikeIndex]);
                Handle<Quote> quotedVolQuote = new Handle<>(quotedVol, true);
                Handle<OptionletVolatilityStructure> quotedVolHandle =
                        new Handle<>(new ConstantOptionletVolatility(
                                vars.capfloorVol.referenceDate(),
                                vars.calendar, vars.convention, quotedVolQuote,
                                vars.dc, Sarb.VolatilityType.Normal, 0.0),
                                true);
                BachelierCapFloorEngine quotedVolEngine =
                        BachelierCapFloorEngine.withVolatility(
                                vars.sofrCurveHandle, quotedVolHandle);
                CapFloor cap = new CapFloor(CapFloor.Type.Cap, leg, strikes);
                cap.setPricingEngine(quotedVolEngine);
                double capPrice = cap.NPV();

                double error = Math.abs(capPrice - strippedCapPrice);
                maxError = Math.max(maxError, error);
                if (error > tolerance)
                    fail("\noption tenor:       "
                            + vars.expiries.get(tenorIndex)
                            + "\nstrike:             " + vars.strikes[strikeIndex]
                            + "\nquoted vol price:   " + capPrice
                            + "\nstripped vol price: " + strippedCapPrice
                            + "\nerror:              " + error
                            + "\ntolerance:          " + tolerance);
            }
        }
        return maxError;
    }

    private static double checkSofrOptionletStripping2(boolean flatVol,
                                                       int paymentLag) {
        CommonVarsON vars = new CommonVarsON();
        Settings.instance.evaluationDate().equal(vars.today);
        vars.setSofrHandle();
        if (flatVol)
            vars.setFlatSofrCapFloorVolSurface();
        else
            vars.setNonFlatSofrCapFloorVolSurface();

        OvernightIndex sofrIndex = new Sofr(vars.sofrCurveHandle);
        sofrIndex.addFixing(new Date(15, Month.APRIL, 2025), 3.04 / 100.0,
                false);

        List<Handle<Quote>> atmVols = new ArrayList<>();
        for (int i = 0; i < vars.expiries.size(); ++i) {
            double atmVol = vars.vols[i][1];
            if (!flatVol)
                atmVol += 0.0001;
            atmVols.add(new Handle<>(new SimpleQuote(atmVol), true));
        }
        CapFloorTermVolCurve atmCurve = new CapFloorTermVolCurve(2,
                vars.calendar, vars.convention, vars.expiries, atmVols,
                vars.dc);

        OptionletStripper1 stripper1 = new OptionletStripper1(
                vars.capfloorVol, sofrIndex, null, 1e-6, 100,
                vars.sofrCurveHandle, Sarb.VolatilityType.Normal, 0.0, false,
                vars.tenor, paymentLag);
        OptionletStripper2 stripper2 = new OptionletStripper2(stripper1,
                atmCurve);

        Handle<OptionletVolatilityStructure> vol = new Handle<>(
                new StrippedOptionletAdapter(stripper2), true);
        vol.currentLink().enableExtrapolation();
        BachelierCapFloorEngine engine = BachelierCapFloorEngine.withVolatility(
                vars.sofrCurveHandle, vol);

        double[] strikes = stripper2.atmCapFloorStrikes();
        double[] targetPrices = stripper2.atmCapFloorPrices();
        double tolerance = 1.0e-6;

        if (flatVol) {
            List<Double> firstUncoveredSmile = stripper2.optionletStrikes(4);
            for (double strike : firstUncoveredSmile)
                if (Math.abs(strike - strikes[0]) < 1.0e-12)
                    fail("the 1Y SOFR ATM adjustment was applied beyond its "
                            + "final optionlet");
        }

        double maxError = 0.0;
        for (int i = 0; i < vars.expiries.size(); ++i) {
            Date endDate = vars.calendar.advance(vars.startDate,
                    vars.expiries.get(i), vars.convention);
            Schedule schedule = new Schedule(vars.startDate, endDate,
                    vars.tenor, vars.calendar, vars.convention,
                    vars.convention, DateGenerationRule.FORWARD, false,
                    new Date(), new Date());
            List<jquant.CashFlow> leg = new OvernightLeg(schedule, sofrIndex)
                    .withNotionals(1.0)
                    .withPaymentCalendar(vars.calendar)
                    .withPaymentAdjustment(vars.convention)
                    .withPaymentLag(paymentLag)
                    .build();
            CashflowUtil.setCouponPricer(leg,
                    new CompoundingOvernightIndexedCouponPricer());

            CapFloor cap = new CapFloor(CapFloor.Type.Cap, leg,
                    List.of(strikes[i]));
            cap.setPricingEngine(engine);
            double error = Math.abs(cap.NPV() - targetPrices[i]);
            maxError = Math.max(maxError, error);
            if (error > tolerance)
                fail("\noption tenor: " + vars.expiries.get(i)
                        + "\nATM strike:   " + strikes[i]
                        + "\ntarget price: " + targetPrices[i]
                        + "\nstripper price: " + cap.NPV()
                        + "\nerror:        " + error
                        + "\ntolerance:    " + tolerance);
        }
        return maxError;
    }

    @Test
    public void testFlatTermVolatilityStripping1SofrPaymentLag() {
        double maxError = checkSofrOptionletStripping(true, 2);
        System.out.println(
                "testFlatTermVolatilityStripping1SofrPaymentLag max error: "
                        + maxError);
    }

    @Test
    public void testTermVolatilityStripping1SofrPaymentLag() {
        double maxError = checkSofrOptionletStripping(false, 2);
        System.out.println(
                "testTermVolatilityStripping1SofrPaymentLag max error: "
                        + maxError);
    }

    @Test
    public void testFlatTermVolatilityStripping2SofrPaymentLag() {
        double maxError = checkSofrOptionletStripping2(true, 2);
        System.out.println(
                "testFlatTermVolatilityStripping2SofrPaymentLag max error: "
                        + maxError);
    }

    @Test
    public void testTermVolatilityStripping2SofrPaymentLag() {
        double maxError = checkSofrOptionletStripping2(false, 2);
        System.out.println(
                "testTermVolatilityStripping2SofrPaymentLag max error: "
                        + maxError);
    }

    @Test
    public void testOptionletStripper2AdjustsZeroBaseVolatility() {
        CommonVarsON vars = new CommonVarsON();
        Settings.instance.evaluationDate().equal(vars.today);
        vars.setSofrHandle();
        vars.setFlatSofrCapFloorVolSurface();
        vars.vols = new double[vars.expiries.size()][vars.strikes.length];
        vars.capfloorVol = new CapFloorTermVolSurface(2, vars.calendar,
                vars.convention, vars.expiries, vars.strikes,
                new Matrix(vars.vols), vars.dc);

        OvernightIndex sofrIndex = new Sofr(vars.sofrCurveHandle);
        sofrIndex.addFixing(new Date(15, Month.APRIL, 2025), 3.04 / 100.0,
                false);
        OptionletStripper1 stripper1 = new OptionletStripper1(
                vars.capfloorVol, sofrIndex, null, 1e-6, 100,
                vars.sofrCurveHandle, Sarb.VolatilityType.Normal, 0.0, true,
                vars.tenor, 2);

        List<Handle<Quote>> atmVols = new ArrayList<>();
        for (int i = 0; i < vars.expiries.size(); ++i)
            atmVols.add(new Handle<>(new SimpleQuote(0.05), true));
        CapFloorTermVolCurve atmCurve = new CapFloorTermVolCurve(2,
                vars.calendar, vars.convention, vars.expiries, atmVols,
                vars.dc);
        OptionletStripper2 stripper2 = new OptionletStripper2(stripper1,
                atmCurve);

        double[] spreads = stripper2.spreadsVol();
        double minSpread = Double.POSITIVE_INFINITY;
        for (double spread : spreads) {
            minSpread = Math.min(minSpread, spread);
            if (!(spread > 0.0))
                fail("expected a positive volatility spread, got " + spread);
        }
        System.out.println(
                "testOptionletStripper2AdjustsZeroBaseVolatility min spread: "
                        + minSpread);
    }
}
