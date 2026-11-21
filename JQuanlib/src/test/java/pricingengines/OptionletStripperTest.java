package pricingengines;

import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.CapFloor;
import jquant.instruments.MakeCapFloor;
import jquant.math.Matrix;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.capfloor.CapFloorTermVolSurface;
import jquant.termstructures.volatility.optionlet.OptionletStripper1;
import jquant.termstructures.volatility.optionlet.StrippedOptionletAdapter;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
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
 * <p>Phase 2 ports only the first two official cases:
 * {@code testFlatTermVolatilityStripping1} and
 * {@code testTermVolatilityStripping1}. The remaining 17 official cases are
 * out of scope.
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

        double[] strikes;
        List<Period> optionTenors;
        double[][] termV;

        CapFloorTermVolSurface flatTermVolSurface;
        CapFloorTermVolSurface capFloorVolSurface;

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
}
