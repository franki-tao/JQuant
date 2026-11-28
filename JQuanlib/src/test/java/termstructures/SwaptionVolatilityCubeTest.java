package termstructures;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.EuriborSwapIsdaFixA;
import jquant.indexes.IndexManager;
import jquant.indexes.SwapIndex;
import jquant.math.Matrix;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.SabrSmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.ZabrEvaluation;
import jquant.termstructures.volatility.ZabrSmileSection;
import jquant.termstructures.volatility.swaption.InterpolatedSwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.SabrSwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.SpreadedSwaptionVolatility;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.ZabrSwaptionVolatilityCube;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityMatrix;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.close_enough;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/swaptionvolatilitycube.cpp}
 * (suite {@code SwaptionVolatilityCubeTests}) and the shared market data
 * from {@code test-suite/swaptionvolstructuresutilities.hpp}.
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the case;
 * {@code BOOST_FAIL} aborts immediately. {@code BOOST_TEST_MESSAGE} is
 * printed to {@link System#out}. {@code QL_CHECK_SMALL} is an absolute
 * tolerance and is not loosened here.
 */
public class SwaptionVolatilityCubeTest {

    private SavedSettings savedSettings;
    private Set<Observer> evalObserversAtStart;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        evalObserversAtStart = snapshotEvalDateObservers();
    }

    @AfterEach
    void restoreSettings() {
        final Set<Observer> added = snapshotEvalDateObservers();
        if (evalObserversAtStart != null) {
            added.removeAll(evalObserversAtStart);
        }
        for (Observer o : added) {
            o.unregisterWithAll();
        }
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    @SuppressWarnings("unchecked")
    private static Set<Observer> snapshotEvalDateObservers() {
        final Object observable = Settings.instance.evaluationDate();
        Class<?> c = observable.getClass();
        while (c != null) {
            try {
                final Field f = c.getDeclaredField("observers");
                f.setAccessible(true);
                final Object v = f.get(observable);
                if (v instanceof Set) {
                    return new HashSet<>((Set<Observer>) v);
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            c = c.getSuperclass();
        }
        return Set.of();
    }

    // ---------------------------------------------------------------------
    // swaptionvolstructuresutilities.hpp
    // ---------------------------------------------------------------------

    private static final class SwaptionTenors {
        final List<Period> options = new ArrayList<>();
        final List<Period> swaps = new ArrayList<>();
    }

    private static final class SwaptionMarketConventions {
        Calendar calendar;
        BusinessDayConvention optionBdc;
        DayCounter dayCounter;

        void setConventions() {
            calendar = new Target();
            optionBdc = BusinessDayConvention.MODIFIED_FOLLOWING;
            dayCounter = new Actual365Fixed();
        }
    }

    private static final class AtmVolatility {
        final SwaptionTenors tenors = new SwaptionTenors();
        Matrix vols;
        List<List<Handle<Quote>>> volsHandle;

        void setMarketData() {
            tenors.options.addAll(Arrays.asList(
                    new Period(1, TimeUnit.MONTHS),
                    new Period(6, TimeUnit.MONTHS),
                    new Period(1, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS)));
            tenors.swaps.addAll(Arrays.asList(
                    new Period(1, TimeUnit.YEARS),
                    new Period(5, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS)));
            vols = new Matrix(tenors.options.size(), tenors.swaps.size(), 0.0);
            vols.set(0, 0, 0.1300);
            vols.set(0, 1, 0.1560);
            vols.set(0, 2, 0.1390);
            vols.set(0, 3, 0.1220);
            vols.set(1, 0, 0.1440);
            vols.set(1, 1, 0.1580);
            vols.set(1, 2, 0.1460);
            vols.set(1, 3, 0.1260);
            vols.set(2, 0, 0.1600);
            vols.set(2, 1, 0.1590);
            vols.set(2, 2, 0.1470);
            vols.set(2, 3, 0.1290);
            vols.set(3, 0, 0.1640);
            vols.set(3, 1, 0.1470);
            vols.set(3, 2, 0.1370);
            vols.set(3, 3, 0.1220);
            vols.set(4, 0, 0.1400);
            vols.set(4, 1, 0.1300);
            vols.set(4, 2, 0.1250);
            vols.set(4, 3, 0.1100);
            vols.set(5, 0, 0.1130);
            vols.set(5, 1, 0.1090);
            vols.set(5, 2, 0.1070);
            vols.set(5, 3, 0.0930);

            volsHandle = new ArrayList<>(tenors.options.size());
            for (int i = 0; i < tenors.options.size(); ++i) {
                List<Handle<Quote>> row = new ArrayList<>(tenors.swaps.size());
                for (int j = 0; j < tenors.swaps.size(); ++j)
                    // every handle must be reassigned: default-created links
                    // would all point to the same quote.
                    row.add(new Handle<>(new SimpleQuote(vols.get(i, j)), true));
                volsHandle.add(row);
            }
        }
    }

    private static final class VolatilityCube {
        final SwaptionTenors tenors = new SwaptionTenors();
        Matrix volSpreads;
        List<List<Handle<Quote>>> volSpreadsHandle;
        double[] strikeSpreads;

        void setMarketData() {
            tenors.options.addAll(Arrays.asList(
                    new Period(1, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS)));
            tenors.swaps.addAll(Arrays.asList(
                    new Period(2, TimeUnit.YEARS),
                    new Period(10, TimeUnit.YEARS),
                    new Period(30, TimeUnit.YEARS)));
            strikeSpreads = new double[]{-0.020, -0.005, 0.000, 0.005, 0.020};
            volSpreads = new Matrix(
                    tenors.options.size() * tenors.swaps.size(),
                    strikeSpreads.length, 0.0);
            volSpreads.set(0, 0, 0.0599);
            volSpreads.set(0, 1, 0.0049);
            volSpreads.set(0, 2, 0.0000);
            volSpreads.set(0, 3, -0.0001);
            volSpreads.set(0, 4, 0.0127);
            volSpreads.set(1, 0, 0.0729);
            volSpreads.set(1, 1, 0.0086);
            volSpreads.set(1, 2, 0.0000);
            volSpreads.set(1, 3, -0.0024);
            volSpreads.set(1, 4, 0.0098);
            volSpreads.set(2, 0, 0.0738);
            volSpreads.set(2, 1, 0.0102);
            volSpreads.set(2, 2, 0.0000);
            volSpreads.set(2, 3, -0.0039);
            volSpreads.set(2, 4, 0.0065);
            volSpreads.set(3, 0, 0.0465);
            volSpreads.set(3, 1, 0.0063);
            volSpreads.set(3, 2, 0.0000);
            volSpreads.set(3, 3, -0.0032);
            volSpreads.set(3, 4, -0.0010);
            volSpreads.set(4, 0, 0.0558);
            volSpreads.set(4, 1, 0.0084);
            volSpreads.set(4, 2, 0.0000);
            volSpreads.set(4, 3, -0.0050);
            volSpreads.set(4, 4, -0.0057);
            volSpreads.set(5, 0, 0.0576);
            volSpreads.set(5, 1, 0.0083);
            volSpreads.set(5, 2, 0.0000);
            volSpreads.set(5, 3, -0.0043);
            volSpreads.set(5, 4, -0.0014);
            volSpreads.set(6, 0, 0.0437);
            volSpreads.set(6, 1, 0.0059);
            volSpreads.set(6, 2, 0.0000);
            volSpreads.set(6, 3, -0.0030);
            volSpreads.set(6, 4, -0.0006);
            volSpreads.set(7, 0, 0.0533);
            volSpreads.set(7, 1, 0.0078);
            volSpreads.set(7, 2, 0.0000);
            volSpreads.set(7, 3, -0.0045);
            volSpreads.set(7, 4, -0.0046);
            volSpreads.set(8, 0, 0.0545);
            volSpreads.set(8, 1, 0.0079);
            volSpreads.set(8, 2, 0.0000);
            volSpreads.set(8, 3, -0.0042);
            volSpreads.set(8, 4, -0.0020);

            final int sections = tenors.options.size() * tenors.swaps.size();
            volSpreadsHandle = new ArrayList<>(sections);
            for (int i = 0; i < sections; ++i) {
                List<Handle<Quote>> row =
                        new ArrayList<>(strikeSpreads.length);
                for (int j = 0; j < strikeSpreads.length; ++j)
                    row.add(new Handle<>(
                            new SimpleQuote(volSpreads.get(i, j)), true));
                volSpreadsHandle.add(row);
            }
        }
    }

    private static final class CommonVars {
        final SwaptionMarketConventions conventions =
                new SwaptionMarketConventions();
        final AtmVolatility atm = new AtmVolatility();
        final RelinkableHandle<SwaptionVolatilityStructure> atmVolMatrix =
                new RelinkableHandle<>();
        final RelinkableHandle<SwaptionVolatilityStructure> normalVolMatrix =
                new RelinkableHandle<>();
        final VolatilityCube cube = new VolatilityCube();
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();
        SwapIndex swapIndexBase;
        SwapIndex shortSwapIndexBase;
        boolean vegaWeighedSmileFit;

        final List<String> boostErrors = new ArrayList<>();

        CommonVars() {
            conventions.setConventions();

            // ATM swaption vol matrix
            atm.setMarketData();
            atmVolMatrix.linkTo(new SwaptionVolatilityMatrix(
                    conventions.calendar, conventions.optionBdc,
                    atm.tenors.options, atm.tenors.swaps,
                    atm.volsHandle, conventions.dayCounter), true);
            normalVolMatrix.linkTo(new SwaptionVolatilityMatrix(
                    conventions.calendar, conventions.optionBdc,
                    atm.tenors.options, atm.tenors.swaps,
                    atm.volsHandle, conventions.dayCounter, false,
                    VolatilityType.Normal, null), true);

            // Swaption vol cube
            cube.setMarketData();

            termStructure.linkTo(new FlatForward(0, new NullCalendar(),
                    0.05, new Actual365Fixed()), true);
            swapIndexBase = new EuriborSwapIsdaFixA(
                    new Period(2, TimeUnit.YEARS), termStructure);
            shortSwapIndexBase = new EuriborSwapIsdaFixA(
                    new Period(1, TimeUnit.YEARS), termStructure);

            vegaWeighedSmileFit = false;
        }

        List<List<Handle<Quote>>> makeZabrParametersGuess(double alpha,
                                                         double beta,
                                                         double nu,
                                                         double rho,
                                                         double gamma) {
            final int n = cube.tenors.options.size()
                    * cube.tenors.swaps.size();
            List<List<Handle<Quote>>> guess = new ArrayList<>(n);
            for (int i = 0; i < n; ++i) {
                guess.add(Arrays.asList(
                        new Handle<Quote>(new SimpleQuote(alpha), true),
                        new Handle<Quote>(new SimpleQuote(beta), true),
                        new Handle<Quote>(new SimpleQuote(nu), true),
                        new Handle<Quote>(new SimpleQuote(rho), true),
                        new Handle<Quote>(new SimpleQuote(gamma), true)));
            }
            return guess;
        }

        void makeAtmVolTest(SwaptionVolatilityCube volCube, double tolerance) {
            for (Period option : atm.tenors.options) {
                for (Period swap : atm.tenors.swaps) {
                    final double strike = volCube.atmStrike(option, swap);
                    final Date optionDate =
                            atmVolMatrix.currentLink()
                                    .optionDateFromTenor(option);
                    final double expVol = atmVolMatrix.currentLink()
                            .volatility(optionDate, swap, strike, true);
                    final double actVol = volCube
                            .volatility(optionDate, swap, strike, true);
                    final double error = Math.abs(expVol - actVol);
                    if (error > tolerance) {
                        boostErrors.add("\nrecovery of atm vols failed:"
                                + "\nexpiry time = " + option
                                + "\nswap length = " + swap
                                + "\n atm strike = " + strike
                                + "\n   exp. vol = " + expVol
                                + "\n actual vol = " + actVol
                                + "\n      error = " + error
                                + "\n  tolerance = " + tolerance);
                    }
                }
            }
        }

        void makeVolSpreadsTest(SwaptionVolatilityCube volCube,
                                double tolerance) {
            for (int i = 0; i < cube.tenors.options.size(); ++i) {
                for (int j = 0; j < cube.tenors.swaps.size(); ++j) {
                    for (int k = 0; k < cube.strikeSpreads.length; ++k) {
                        final Period option = cube.tenors.options.get(i);
                        final Period swap = cube.tenors.swaps.get(j);
                        final double atmStrike =
                                volCube.atmStrike(option, swap);
                        final Date optionDate =
                                atmVolMatrix.currentLink()
                                        .optionDateFromTenor(option);
                        final double atmVol = atmVolMatrix.currentLink()
                                .volatility(optionDate, swap, atmStrike, true);
                        final double vol = volCube.volatility(optionDate,
                                swap, atmStrike + cube.strikeSpreads[k], true);
                        final double spread = vol - atmVol;
                        final double expVolSpread =
                                cube.volSpreads.get(
                                        i * cube.tenors.swaps.size() + j, k);
                        final double error = Math.abs(expVolSpread - spread);
                        if (error > tolerance) {
                            // BOOST_FAIL aborts immediately.
                            fail("\nrecovery of smile vol spreads failed:"
                                    + "\n    option tenor = " + option
                                    + "\n      swap tenor = " + swap
                                    + "\n      atm strike = " + atmStrike
                                    + "\n   strike spread = "
                                    + cube.strikeSpreads[k]
                                    + "\n         atm vol = " + atmVol
                                    + "\n      smiled vol = " + vol
                                    + "\n      vol spread = " + spread
                                    + "\n exp. vol spread = " + expVolSpread
                                    + "\n           error = " + error
                                    + "\n       tolerance = " + tolerance);
                        }
                    }
                }
            }
        }

        void checkBoostErrors() {
            if (!boostErrors.isEmpty()) {
                fail(String.join(System.lineSeparator(), boostErrors));
            }
        }
    }

    private static List<List<Handle<Quote>>> sabrParametersGuess(
            CommonVars vars, double alpha, double beta, double nu,
            double rho) {
        final int n = vars.cube.tenors.options.size()
                * vars.cube.tenors.swaps.size();
        List<List<Handle<Quote>>> parametersGuess = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            parametersGuess.add(Arrays.asList(
                    new Handle<Quote>(new SimpleQuote(alpha), true),
                    new Handle<Quote>(new SimpleQuote(beta), true),
                    new Handle<Quote>(new SimpleQuote(nu), true),
                    new Handle<Quote>(new SimpleQuote(rho), true)));
        }
        return parametersGuess;
    }

    // ---------------------------------------------------------------------
    // Official cases
    // ---------------------------------------------------------------------

    @Test
    public void testAtmVols() {
        System.out.println("Testing swaption volatility cube (atm vols)...");

        CommonVars vars = new CommonVars();

        InterpolatedSwaptionVolatilityCube volCube =
                new InterpolatedSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit);

        double tolerance = 1.0e-16;
        vars.makeAtmVolTest(volCube, tolerance);
        vars.checkBoostErrors();
    }

    @Test
    public void testSmile() {
        System.out.println("Testing swaption volatility cube (smile)...");

        CommonVars vars = new CommonVars();

        InterpolatedSwaptionVolatilityCube volCube =
                new InterpolatedSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit);

        double tolerance = 1.0e-16;
        vars.makeVolSpreadsTest(volCube, tolerance);
    }

    @Test
    public void testSabrVols() {
        System.out.println(
                "Testing swaption volatility cube (sabr interpolation)...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                sabrParametersGuess(vars, 0.2, 0.5, 0.4, 0.0);
        boolean[] isParameterFixed = new boolean[]{false, false, false, false};

        SabrSwaptionVolatilityCube volCube = new SabrSwaptionVolatilityCube(
                vars.atmVolMatrix,
                vars.cube.tenors.options,
                vars.cube.tenors.swaps,
                vars.cube.strikeSpreads,
                vars.cube.volSpreadsHandle,
                vars.swapIndexBase,
                vars.shortSwapIndexBase,
                vars.vegaWeighedSmileFit,
                parametersGuess,
                isParameterFixed,
                true);
        double tolerance = 3.0e-4;
        vars.makeAtmVolTest(volCube, tolerance);

        tolerance = 12.0e-4;
        vars.makeVolSpreadsTest(volCube, tolerance);
        vars.checkBoostErrors();
    }

    @Test
    public void testSabrNormalVolatility() {
        System.out.println("Testing sabr normal volatility...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                sabrParametersGuess(vars, 0.2, 0.5, 0.4, 0.0);
        boolean[] isParameterFixed = new boolean[]{false, false, false, false};

        SabrSwaptionVolatilityCube volCube = new SabrSwaptionVolatilityCube(
                vars.normalVolMatrix,
                vars.cube.tenors.options,
                vars.cube.tenors.swaps,
                vars.cube.strikeSpreads,
                vars.cube.volSpreadsHandle,
                vars.swapIndexBase,
                vars.shortSwapIndexBase,
                vars.vegaWeighedSmileFit,
                parametersGuess,
                isParameterFixed,
                true);
        double tolerance = 7.0e-4;
        vars.makeAtmVolTest(volCube, tolerance);
        vars.checkBoostErrors();
    }

    private static int findRow(Matrix matrix, double optionTime,
                               double swapLength) {
        for (int row = 0; row < matrix.rows(); ++row) {
            if (close_enough(matrix.get(row, 0), swapLength)
                    && close_enough(matrix.get(row, 1), optionTime))
                return row;
        }
        fail("parameter row not found");
        return -1;
    }

    private static double interpolatedSpread(
            Matrix sparseParameters,
            double[] optionNodes,
            double[] swapNodes,
            SwaptionVolatilityStructure normalAtm,
            double targetTime,
            double targetLength,
            double targetForward,
            double targetStrikeSpread,
            double targetStrike,
            boolean ratioMoneyness) {
        double[][] cornerSpreads = new double[2][2];
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                final int row = findRow(sparseParameters,
                        optionNodes[i], swapNodes[j]);
                final double sourceForward = sparseParameters.get(row, 6);
                final double[] parameters = {
                        sparseParameters.get(row, 2),
                        sparseParameters.get(row, 3),
                        sparseParameters.get(row, 4),
                        sparseParameters.get(row, 5)};
                SabrSmileSection smile = new SabrSmileSection(
                        optionNodes[i], sourceForward, parameters,
                        0.0, VolatilityType.Normal);
                final double sourceStrike = ratioMoneyness
                        ? sourceForward * targetStrike / targetForward
                        : sourceForward + targetStrikeSpread;
                final double sourceAtm = normalAtm.volatility(
                        optionNodes[i], swapNodes[j], sourceForward, true);
                cornerSpreads[i][j] =
                        smile.volatility(sourceStrike) - sourceAtm;
            }
        }
        final double optionWeight =
                (targetTime - optionNodes[0]) / (optionNodes[1] - optionNodes[0]);
        final double swapWeight =
                (targetLength - swapNodes[0]) / (swapNodes[1] - swapNodes[0]);
        return (1.0 - optionWeight)
                * ((1.0 - swapWeight) * cornerSpreads[0][0]
                + swapWeight * cornerSpreads[0][1])
                + optionWeight
                * ((1.0 - swapWeight) * cornerSpreads[1][0]
                + swapWeight * cornerSpreads[1][1]);
    }

    @Test
    public void testSabrNormalTwoPassUsesStrikeSpreadMoneyness() {
        System.out.println("Testing K-F interpolation in a two-pass normal "
                + "SABR cube...");

        CommonVars vars = new CommonVars();
        final Date today = Settings.instance.evaluationDate().Date();
        final List<Date> dates = Arrays.asList(
                today,
                vars.conventions.calendar.advance(today,
                        new Period(1, TimeUnit.YEARS)),
                vars.conventions.calendar.advance(today,
                        new Period(5, TimeUnit.YEARS)),
                vars.conventions.calendar.advance(today,
                        new Period(15, TimeUnit.YEARS)),
                vars.conventions.calendar.advance(today,
                        new Period(70, TimeUnit.YEARS)));
        final double[] zeroRates = {0.01, 0.015, 0.025, 0.035, 0.045};
        RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();
        termStructure.linkTo(new ZeroCurve(dates, zeroRates,
                new Actual365Fixed(), vars.conventions.calendar), true);

        final SwapIndex swapIndex = new EuriborSwapIsdaFixA(
                new Period(2, TimeUnit.YEARS), termStructure);
        final SwapIndex shortSwapIndex = new EuriborSwapIsdaFixA(
                new Period(1, TimeUnit.YEARS), termStructure);
        final int sections = vars.cube.tenors.options.size()
                * vars.cube.tenors.swaps.size();
        Matrix normalVols = vars.atm.vols.multiply(0.05);
        Handle<SwaptionVolatilityStructure> normalAtm =
                new Handle<>(new SwaptionVolatilityMatrix(
                        vars.conventions.calendar,
                        vars.conventions.optionBdc,
                        vars.atm.tenors.options, vars.atm.tenors.swaps,
                        normalVols, vars.conventions.dayCounter, false,
                        VolatilityType.Normal, null), true);
        List<List<Handle<Quote>>> volSpreads = new ArrayList<>(sections);
        for (int section = 0; section < sections; ++section) {
            List<Handle<Quote>> row = new ArrayList<>(
                    vars.cube.strikeSpreads.length);
            for (int strike = 0;
                 strike < vars.cube.strikeSpreads.length; ++strike) {
                row.add(new Handle<>(new SimpleQuote(
                        0.03 * vars.cube.volSpreads.get(section, strike)),
                        true));
            }
            volSpreads.add(row);
        }
        List<List<Handle<Quote>>> parametersGuess =
                new ArrayList<>(sections);
        for (int section = 0; section < sections; ++section) {
            parametersGuess.add(Arrays.asList(
                    new Handle<Quote>(new SimpleQuote(0.05), true),
                    new Handle<Quote>(new SimpleQuote(0.5), true),
                    new Handle<Quote>(new SimpleQuote(0.4), true),
                    new Handle<Quote>(new SimpleQuote(0.0), true)));
        }

        SabrSwaptionVolatilityCube volCube = new SabrSwaptionVolatilityCube(
                normalAtm, vars.cube.tenors.options,
                vars.cube.tenors.swaps,
                vars.cube.strikeSpreads, volSpreads, swapIndex,
                shortSwapIndex, false, parametersGuess,
                new boolean[]{false, true, false, false}, true);

        final Period targetOption = new Period(5, TimeUnit.YEARS);
        final Period targetSwap = new Period(5, TimeUnit.YEARS);
        final double targetTime = normalAtm.currentLink().timeFromReference(
                normalAtm.currentLink().optionDateFromTenor(targetOption));
        final double targetLength = normalAtm.currentLink()
                .swapLength(targetSwap);
        final double targetForward =
                volCube.atmStrike(targetOption, targetSwap);
        final double targetStrikeSpread = 0.02;
        final double targetStrike = targetForward + targetStrikeSpread;

        final Matrix sparseParameters = volCube.sparseSabrParameters();
        final double[] optionNodes = {
                normalAtm.currentLink().timeFromReference(
                        normalAtm.currentLink().optionDateFromTenor(
                                new Period(1, TimeUnit.YEARS))),
                normalAtm.currentLink().timeFromReference(
                        normalAtm.currentLink().optionDateFromTenor(
                                new Period(10, TimeUnit.YEARS)))};
        final double[] swapNodes = {
                normalAtm.currentLink().swapLength(
                        new Period(2, TimeUnit.YEARS)),
                normalAtm.currentLink().swapLength(
                        new Period(10, TimeUnit.YEARS))};

        final Matrix denseMarketVols = volCube.volCubeAtmCalibrated();
        final int targetRow =
                findRow(denseMarketVols, targetTime, targetLength);
        final double targetAtm = normalAtm.currentLink().volatility(
                targetTime, targetLength, targetForward, true);
        final double actual = denseMarketVols.get(targetRow, 6);
        final double expected = targetAtm + interpolatedSpread(
                sparseParameters, optionNodes, swapNodes,
                normalAtm.currentLink(), targetTime, targetLength,
                targetForward, targetStrikeSpread, targetStrike, false);
        final double oldRatioResult = targetAtm + interpolatedSpread(
                sparseParameters, optionNodes, swapNodes,
                normalAtm.currentLink(), targetTime, targetLength,
                targetForward, targetStrikeSpread, targetStrike, true);

        // QL_CHECK_SMALL(actual - expected, 1.0e-12)
        assertEquals(0.0, actual - expected, 1.0e-12);
        assertTrue(Math.abs(actual - oldRatioResult) > 1.0e-8);
    }

    private static void checkAtmRecovery(CommonVars vars,
                                         Handle<SwaptionVolatilityStructure> atmVols,
                                         double[] smileSpreads,
                                         double alphaGuess) {
        final int sections = vars.cube.tenors.options.size()
                * vars.cube.tenors.swaps.size();
        List<List<Handle<Quote>>> volSpreads = new ArrayList<>(sections);
        for (int section = 0; section < sections; ++section) {
            List<Handle<Quote>> row =
                    new ArrayList<>(smileSpreads.length);
            for (double smileSpread : smileSpreads)
                row.add(new Handle<>(
                        new SimpleQuote(smileSpread), true));
            volSpreads.add(row);
        }

        List<List<Handle<Quote>>> parametersGuess =
                new ArrayList<>(sections);
        for (int section = 0; section < sections; ++section) {
            parametersGuess.add(Arrays.asList(
                    new Handle<Quote>(new SimpleQuote(alphaGuess), true),
                    new Handle<Quote>(new SimpleQuote(0.5), true),
                    new Handle<Quote>(new SimpleQuote(0.4), true),
                    new Handle<Quote>(new SimpleQuote(0.0), true)));
        }

        SabrSwaptionVolatilityCube volCube = new SabrSwaptionVolatilityCube(
                atmVols, vars.cube.tenors.options,
                vars.cube.tenors.swaps,
                vars.cube.strikeSpreads, volSpreads, vars.swapIndexBase,
                vars.shortSwapIndexBase, false, parametersGuess,
                new boolean[]{false, true, false, false}, true,
                null, NULL_REAL, null, NULL_REAL, false, 50, false,
                0.0001, true);

        for (Period option : vars.cube.tenors.options) {
            for (Period swap : vars.cube.tenors.swaps) {
                final double strike = volCube.atmStrike(option, swap);
                final Date optionDate = atmVols.currentLink()
                        .optionDateFromTenor(option);
                final double expected = atmVols.currentLink()
                        .volatility(optionDate, swap, strike, true);
                final double calculated = volCube
                        .volatility(optionDate, swap, strike, true);
                // QL_CHECK_SMALL(calculated - expected, 1.0e-10)
                assertEquals(0.0, calculated - expected, 1.0e-10);
                final SmileSection smileSection =
                        volCube.smileSection(optionDate, swap);
                assertNotNull(smileSection);
                assertTrue(smileSection instanceof SabrSmileSection);
                final SabrSmileSection smile =
                        (SabrSmileSection) smileSection;
                // QL_CHECK_SMALL(smile->beta() - 0.5, 1.0e-12)
                assertEquals(0.0, smile.beta() - 0.5, 1.0e-12);
            }
        }

        final Matrix parameters = volCube.denseSabrParameters();
        final Matrix marketVols = volCube.volCubeAtmCalibrated();
        boolean hasRatioMoneynessAdjustment = false;
        for (int row = 0; row < parameters.rows(); ++row) {
            final double optionTime = parameters.get(row, 1);
            final double swapLength = parameters.get(row, 0);
            final double forward = parameters.get(row, 6);
            final double[] sabrParameters = {
                    parameters.get(row, 2), parameters.get(row, 3),
                    parameters.get(row, 4), parameters.get(row, 5)};
            SabrSmileSection smile = new SabrSmileSection(
                    optionTime, forward, sabrParameters,
                    atmVols.currentLink().shift(optionTime, swapLength),
                    atmVols.currentLink().volatilityType());

            double squaredError = 0.0;
            double maxError = 0.0;
            for (int i = 0; i < vars.cube.strikeSpreads.length; ++i) {
                final double error =
                        smile.volatility(forward + vars.cube.strikeSpreads[i])
                                - marketVols.get(row, i + 2);
                squaredError += error * error;
                maxError = Math.max(maxError, Math.abs(error));
            }
            final int n = vars.cube.strikeSpreads.length;
            final double rmsError = Math.sqrt(squaredError / (n - 1));
            assertEquals(0.0, parameters.get(row, 7) - rmsError, 1.0e-12);
            assertEquals(0.0, parameters.get(row, 8) - maxError, 1.0e-12);

            if (atmVols.currentLink().volatilityType()
                    == VolatilityType.Normal) {
                final double atmVol = atmVols.currentLink().volatility(
                        optionTime, swapLength, forward, true);
                for (int i = 0; i < smileSpreads.length; ++i) {
                    assertEquals(0.0,
                            marketVols.get(row, i + 2) - atmVol
                                    - smileSpreads[i],
                            1.0e-12);
                }
            } else {
                final double atmVol = atmVols.currentLink().volatility(
                        optionTime, swapLength, forward, true);
                for (int i = 0; i < smileSpreads.length; ++i) {
                    hasRatioMoneynessAdjustment =
                            hasRatioMoneynessAdjustment
                                    || Math.abs(marketVols.get(row, i + 2)
                                    - atmVol - smileSpreads[i])
                                    > 1.0e-10;
                }
            }
        }
        if (atmVols.currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            assertTrue(hasRatioMoneynessAdjustment);
        }

        SabrSwaptionVolatilityCube twoPassCube =
                new SabrSwaptionVolatilityCube(
                        atmVols, vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads, volSpreads,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase, false, parametersGuess,
                        new boolean[]{false, true, false, false}, true);
        boolean hasUnanchoredAtmNode = false;
        for (Period option : vars.cube.tenors.options) {
            for (Period swap : vars.cube.tenors.swaps) {
                final double strike = twoPassCube.atmStrike(option, swap);
                final Date optionDate = atmVols.currentLink()
                        .optionDateFromTenor(option);
                final double expected = atmVols.currentLink()
                        .volatility(optionDate, swap, strike, true);
                final double calculated = twoPassCube
                        .volatility(optionDate, swap, strike, true);
                hasUnanchoredAtmNode = hasUnanchoredAtmNode
                        || Math.abs(calculated - expected) > 1.0e-8;
            }
        }
        assertTrue(hasUnanchoredAtmNode);

        SabrSwaptionVolatilityCube unanchoredSinglePassCube =
                new SabrSwaptionVolatilityCube(
                        atmVols, vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads, volSpreads,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase, false, parametersGuess,
                        new boolean[]{false, true, false, false}, false,
                        null, NULL_REAL, null, NULL_REAL, false, 50, false,
                        0.0001, true);
        boolean singlePassHasUnanchoredAtmNode = false;
        for (Period option : vars.cube.tenors.options) {
            for (Period swap : vars.cube.tenors.swaps) {
                final double strike =
                        unanchoredSinglePassCube.atmStrike(option, swap);
                final Date optionDate = atmVols.currentLink()
                        .optionDateFromTenor(option);
                final double expected = atmVols.currentLink()
                        .volatility(optionDate, swap, strike, true);
                final double calculated = unanchoredSinglePassCube
                        .volatility(optionDate, swap, strike, true);
                singlePassHasUnanchoredAtmNode =
                        singlePassHasUnanchoredAtmNode
                                || Math.abs(calculated - expected) > 1.0e-8;
            }
        }
        assertTrue(singlePassHasUnanchoredAtmNode);

        if (atmVols.currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            final Matrix calibrationParameters =
                    unanchoredSinglePassCube.denseSabrParameters();
            double largestCalibrationError = 0.0;
            double largestAnchoredError = 0.0;
            for (int row = 0; row < parameters.rows(); ++row) {
                largestCalibrationError = Math.max(
                        largestCalibrationError,
                        calibrationParameters.get(row, 7));
                largestAnchoredError = Math.max(
                        largestAnchoredError, parameters.get(row, 7));
            }
            assertTrue(largestAnchoredError > largestCalibrationError);
            final double tolerance =
                    0.5 * (largestCalibrationError + largestAnchoredError);
            assertDoesNotThrow(() -> {
                SabrSwaptionVolatilityCube toleranceCube =
                        new SabrSwaptionVolatilityCube(
                                atmVols, vars.cube.tenors.options,
                                vars.cube.tenors.swaps,
                                vars.cube.strikeSpreads, volSpreads,
                                vars.swapIndexBase,
                                vars.shortSwapIndexBase, false,
                                parametersGuess,
                                new boolean[]{false, true, false, false},
                                true, null, tolerance, null, 0.0020,
                                false, 50, false, 0.0001, true);
                toleranceCube.denseSabrParameters();
            });
        }
    }

    @Test
    public void testSabrAtmCalibrationWithAsymmetricSmile() {
        System.out.println("Testing exact SABR ATM calibration with "
                + "asymmetric smiles...");

        CommonVars vars = new CommonVars();

        Matrix shifts = new Matrix(
                vars.atm.tenors.options.size(),
                vars.atm.tenors.swaps.size(), 0.0);
        for (int i = 0; i < shifts.rows(); ++i) {
            for (int j = 0; j < shifts.cols(); ++j)
                shifts.set(i, j, 0.001 * (i + 2 * j));
        }
        Handle<SwaptionVolatilityStructure> shiftedLognormalAtm =
                new Handle<>(new SwaptionVolatilityMatrix(
                        vars.conventions.calendar,
                        vars.conventions.optionBdc,
                        vars.atm.tenors.options, vars.atm.tenors.swaps,
                        vars.atm.vols, vars.conventions.dayCounter, false,
                        VolatilityType.ShiftedLognormal, shifts), true);
        checkAtmRecovery(vars, shiftedLognormalAtm,
                new double[]{0.045, 0.015, 0.0, 0.010, 0.030}, 0.05);

        List<List<Handle<Quote>>> normalAtmVols =
                new ArrayList<>(vars.atm.tenors.options.size());
        for (int i = 0; i < vars.atm.tenors.options.size(); ++i) {
            List<Handle<Quote>> row =
                    new ArrayList<>(vars.atm.tenors.swaps.size());
            for (int j = 0; j < vars.atm.tenors.swaps.size(); ++j) {
                row.add(new Handle<>(new SimpleQuote(
                        0.05 * vars.atm.vols.get(i, j)), true));
            }
            normalAtmVols.add(row);
        }
        Handle<SwaptionVolatilityStructure> normalAtm =
                new Handle<>(new SwaptionVolatilityMatrix(
                        vars.conventions.calendar,
                        vars.conventions.optionBdc,
                        vars.atm.tenors.options, vars.atm.tenors.swaps,
                        normalAtmVols, vars.conventions.dayCounter, false,
                        VolatilityType.Normal, null), true);
        checkAtmRecovery(vars, normalAtm,
                new double[]{0.00135, 0.00045, 0.0, 0.00030, 0.00090},
                0.05);
    }

    @Test
    public void testObservability() {
        System.out.println("Testing volatility cube observability...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                sabrParametersGuess(vars, 0.2, 0.5, 0.4, 0.0);
        boolean[] isParameterFixed = new boolean[]{false, false, false, false};

        // VolCube created before change of reference date
        SabrSwaptionVolatilityCube volCube1_0 =
                new SabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        Date referenceDate = Settings.instance.evaluationDate().Date();
        Settings.instance.evaluationDate().equal(
                vars.conventions.calendar.advance(referenceDate,
                        new Period(1, TimeUnit.DAYS),
                        vars.conventions.optionBdc));

        // VolCube created after change of reference date
        SabrSwaptionVolatilityCube volCube1_1 =
                new SabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);
        double dummyStrike = 0.03;
        for (int i = 0; i < vars.cube.tenors.options.size(); ++i) {
            for (int j = 0; j < vars.cube.tenors.swaps.size(); ++j) {
                for (int k = 0; k < vars.cube.strikeSpreads.length; ++k) {
                    final Period option = vars.cube.tenors.options.get(i);
                    final Period swap = vars.cube.tenors.swaps.get(j);
                    final double strike =
                            dummyStrike + vars.cube.strikeSpreads[k];
                    final double v0 = volCube1_0.volatility(
                            volCube1_0.optionDateFromTenor(option), swap,
                            strike, false);
                    final double v1 = volCube1_1.volatility(
                            volCube1_1.optionDateFromTenor(option), swap,
                            strike, false);
                    if (Math.abs(v0 - v1) > 1e-14) {
                        vars.boostErrors.add("option tenor = " + option
                                + " swap tenor = " + swap
                                + " strike = " + strike
                                + "  v0 = " + v0
                                + "  v1 = " + v1
                                + "  error = " + Math.abs(v1 - v0));
                    }
                }
            }
        }

        Settings.instance.evaluationDate().equal(referenceDate);

        // VolCube created before change of reference date
        InterpolatedSwaptionVolatilityCube volCube2_0 =
                new InterpolatedSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit);
        Settings.instance.evaluationDate().equal(
                vars.conventions.calendar.advance(referenceDate,
                        new Period(1, TimeUnit.DAYS),
                        vars.conventions.optionBdc));

        // VolCube created after change of reference date
        InterpolatedSwaptionVolatilityCube volCube2_1 =
                new InterpolatedSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit);

        for (int i = 0; i < vars.cube.tenors.options.size(); ++i) {
            for (int j = 0; j < vars.cube.tenors.swaps.size(); ++j) {
                for (int k = 0; k < vars.cube.strikeSpreads.length; ++k) {
                    final Period option = vars.cube.tenors.options.get(i);
                    final Period swap = vars.cube.tenors.swaps.get(j);
                    final double strike =
                            dummyStrike + vars.cube.strikeSpreads[k];
                    final double v0 = volCube2_0.volatility(
                            volCube2_0.optionDateFromTenor(option), swap,
                            strike, false);
                    final double v1 = volCube2_1.volatility(
                            volCube2_1.optionDateFromTenor(option), swap,
                            strike, false);
                    if (Math.abs(v0 - v1) > 1e-14) {
                        vars.boostErrors.add("option tenor = " + option
                                + " swap tenor = " + swap
                                + " strike = " + strike
                                + "  v0 = " + v0
                                + "  v1 = " + v1
                                + "  error = " + Math.abs(v1 - v0));
                    }
                }
            }
        }

        Settings.instance.evaluationDate().equal(referenceDate);
        vars.checkBoostErrors();
    }

    private static SabrSmileSection asSabr(SmileSection section) {
        assertNotNull(section);
        assertTrue(section instanceof SabrSmileSection);
        return (SabrSmileSection) section;
    }

    @Test
    public void testSabrParameters() {
        System.out.println("Testing interpolation of SABR smile sections...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                sabrParametersGuess(vars, 0.2, 0.5, 0.4, 0.0);
        boolean[] isParameterFixed = new boolean[]{false, false, false, false};

        SabrSwaptionVolatilityCube volCube = new SabrSwaptionVolatilityCube(
                vars.atmVolMatrix,
                vars.cube.tenors.options,
                vars.cube.tenors.swaps,
                vars.cube.strikeSpreads,
                vars.cube.volSpreadsHandle,
                vars.swapIndexBase,
                vars.shortSwapIndexBase,
                vars.vegaWeighedSmileFit,
                parametersGuess,
                isParameterFixed,
                true);

        double tolerance = 1.0e-4;

        // Interpolating between two SmileSection objects

        // First section: maturity = 10Y, tenor = 2Y
        SabrSmileSection smileSection1 = asSabr(volCube.smileSection(
                volCube.optionDateFromTenor(new Period(10, TimeUnit.YEARS)),
                new Period(2, TimeUnit.YEARS)));

        // Second section: maturity = 10Y, tenor = 4Y
        SabrSmileSection smileSection2 = asSabr(volCube.smileSection(
                volCube.optionDateFromTenor(new Period(10, TimeUnit.YEARS)),
                new Period(4, TimeUnit.YEARS)));

        // Third section in the middle: maturity = 10Y, tenor = 3Y
        SabrSmileSection smileSection3 = asSabr(volCube.smileSection(
                volCube.optionDateFromTenor(new Period(10, TimeUnit.YEARS)),
                new Period(3, TimeUnit.YEARS)));

        // test alpha interpolation
        double alpha1 = smileSection1.alpha();
        double alpha2 = smileSection2.alpha();
        double alpha3 = smileSection3.alpha();
        double alpha12 = 0.5 * (alpha1 + alpha2);
        if (Math.abs(alpha3 - alpha12) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of alpha "
                    + "parameters:\nexpected = " + alpha12
                    + "\nobserved = " + alpha3);
        }

        // test beta interpolation
        double beta1 = smileSection1.beta();
        double beta2 = smileSection2.beta();
        double beta3 = smileSection3.beta();
        double beta12 = 0.5 * (beta1 + beta2);
        if (Math.abs(beta3 - beta12) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of beta "
                    + "parameters:\nexpected = " + beta12
                    + "\nobserved = " + beta3);
        }

        // test rho interpolation
        double rho1 = smileSection1.rho();
        double rho2 = smileSection2.rho();
        double rho3 = smileSection3.rho();
        double rho12 = 0.5 * (rho1 + rho2);
        if (Math.abs(rho3 - rho12) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of rho "
                    + "parameters:\nexpected = " + rho12
                    + "\nobserved = " + rho3);
        }

        // test nu interpolation
        double nu1 = smileSection1.nu();
        double nu2 = smileSection2.nu();
        double nu3 = smileSection3.nu();
        double nu12 = 0.5 * (nu1 + nu2);
        if (Math.abs(nu3 - nu12) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of nu "
                    + "parameters:\nexpected = " + nu12
                    + "\nobserved = " + nu3);
        }

        // test forward interpolation
        double forward1 = smileSection1.atmLevel();
        double forward2 = smileSection2.atmLevel();
        double forward3 = smileSection3.atmLevel();
        double forward12 = 0.5 * (forward1 + forward2);
        if (Math.abs(forward3 - forward12) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of forward "
                    + "parameters:\nexpected = " + forward12
                    + "\nobserved = " + forward3);
        }

        vars.checkBoostErrors();
    }

    @Test
    public void testSpreadedCube() {
        System.out.println("Testing spreaded swaption volatility cube...");

        CommonVars vars = new CommonVars();

        final int n = vars.cube.tenors.options.size()
                * vars.cube.tenors.swaps.size();
        List<List<Handle<Quote>>> parametersGuess = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            parametersGuess.add(Arrays.asList(
                    new Handle<Quote>(new SimpleQuote(0.2), true),
                    new Handle<Quote>(new SimpleQuote(0.5), true),
                    new Handle<Quote>(new SimpleQuote(0.4), true),
                    new Handle<Quote>(new SimpleQuote(0.0), true)));
        }
        boolean[] isParameterFixed = new boolean[]{false, false, false, false};

        Handle<SwaptionVolatilityStructure> volCube =
                new Handle<>(new SabrSwaptionVolatilityCube(
                        vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true), true);

        SimpleQuote spread = new SimpleQuote(0.0001);
        Handle<Quote> spreadHandle = new Handle<>(spread, true);
        SpreadedSwaptionVolatility spreadedVolCube =
                new SpreadedSwaptionVolatility(volCube, spreadHandle);

        List<Double> strikes = new ArrayList<>();
        for (int k = 1; k < 100; k++)
            strikes.add(k * .01);

        for (int i = 0; i < vars.cube.tenors.options.size(); i++) {
            for (int j = 0; j < vars.cube.tenors.swaps.size(); j++) {
                SmileSection smileSectionByCube =
                        volCube.currentLink().smileSection(
                                vars.cube.tenors.options.get(i),
                                vars.cube.tenors.swaps.get(j));
                SmileSection smileSectionBySpreadedCube =
                        spreadedVolCube.smileSection(
                                vars.cube.tenors.options.get(i),
                                vars.cube.tenors.swaps.get(j));
                for (double strike : strikes) {
                    double diff = spreadedVolCube.volatility(
                            vars.cube.tenors.options.get(i),
                            vars.cube.tenors.swaps.get(j), strike)
                            - volCube.currentLink().volatility(
                            vars.cube.tenors.options.get(i),
                            vars.cube.tenors.swaps.get(j), strike);
                    if (Math.abs(diff - spread.value()) > 1e-16) {
                        vars.boostErrors.add(
                                "\ndiff!=spread in volatility method:"
                                        + "\nexpiry time = "
                                        + vars.cube.tenors.options.get(i)
                                        + "\nswap length = "
                                        + vars.cube.tenors.swaps.get(j)
                                        + "\n atm strike = " + strike
                                        + "\ndiff = " + diff
                                        + "\nspread = " + spread.value());
                    }

                    diff = smileSectionBySpreadedCube.volatility(strike)
                            - smileSectionByCube.volatility(strike);
                    if (Math.abs(diff - spread.value()) > 1e-16) {
                        vars.boostErrors.add(
                                "\ndiff!=spread in smile section method:"
                                        + "\nexpiry time = "
                                        + vars.cube.tenors.options.get(i)
                                        + "\nswap length = "
                                        + vars.cube.tenors.swaps.get(j)
                                        + "\n atm strike = " + strike
                                        + "\ndiff = " + diff
                                        + "\nspread = " + spread.value());
                    }
                }
            }
        }

        // testing observability
        Flag f = new Flag();
        f.registerWith(spreadedVolCube);
        volCube.currentLink().update();
        if (!f.isUp()) {
            vars.boostErrors.add("SpreadedSwaptionVolatilityStructure "
                    + "does not propagate notifications");
        }
        f.lower();
        spread.setValue(.001);
        if (!f.isUp()) {
            vars.boostErrors.add("SpreadedSwaptionVolatilityStructure "
                    + "does not propagate notifications");
        }

        vars.checkBoostErrors();
    }

    // ---------------------------------------------------------------------
    // ZABR cases
    // ---------------------------------------------------------------------

    private static ZabrSmileSection asZabr(SmileSection section) {
        assertNotNull(section);
        assertTrue(section instanceof ZabrSmileSection);
        return (ZabrSmileSection) section;
    }

    @Test
    public void testZabrVols() {
        System.out.println(
                "Testing swaption volatility cube (ZABR interpolation)...");

        CommonVars vars = new CommonVars();

        // ZABR requires 5 parameters: alpha, beta, nu, rho, gamma
        // gamma=1.0 gives SABR-like behaviour
        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;  // Fix beta (standard practice, as in SABR)
        isParameterFixed[4] = true;  // Fix gamma for stability

        ZabrSwaptionVolatilityCube volCube =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        // ZABR should recover ATM vols with reasonable tolerance
        double tolerance = 5.0e-4;
        vars.makeAtmVolTest(volCube, tolerance);

        // ZABR should also recover vol spreads with reasonable tolerance
        tolerance = 15.0e-4;
        vars.makeVolSpreadsTest(volCube, tolerance);

        // Access via base class interface for smileSection
        SwaptionVolatilityStructure volStructure = volCube;

        // Test smile section retrieval
        SmileSection smileSection = volStructure.smileSection(
                new Period(10, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS));
        assertNotNull(smileSection);

        // Test volatility at ATM
        double atmStrike = volCube.atmStrike(new Period(10, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS));
        double vol = volCube.volatility(new Period(10, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS), atmStrike, true);
        assertTrue(vol > 0.0);

        vars.checkBoostErrors();
    }

    @Test
    public void testZabrSmileSection() {
        System.out.println(
                "Testing ZABR swaption volatility cube smile sections...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;  // Fix beta (standard practice)
        isParameterFixed[4] = true;

        ZabrSwaptionVolatilityCube volCube =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        // Access via base class interface
        SwaptionVolatilityStructure volStructure = volCube;

        // Get smile section and test its properties
        SmileSection smileSection = volStructure.smileSection(
                new Period(5, TimeUnit.YEARS),
                new Period(5, TimeUnit.YEARS));

        assertNotNull(smileSection);
        assertTrue(smileSection.atmLevel() > 0.0);

        // Test volatility at different strikes
        double atmStrike = smileSection.atmLevel();
        double[] strikes = {atmStrike * 0.8, atmStrike, atmStrike * 1.2};

        for (double strike : strikes) {
            double vol = smileSection.volatility(strike);
            if (!(vol > 0.0)) {
                vars.boostErrors.add("ZABR smile section volatility at strike "
                        + strike + " should be positive");
            }
        }

        vars.checkBoostErrors();
    }

    @Test
    public void testZabrParameters() {
        System.out.println(
                "Testing interpolation of ZABR smile section parameters...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;  // Fix beta (standard practice)
        isParameterFixed[4] = true;

        ZabrSwaptionVolatilityCube volCube =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        SwaptionVolatilityStructure volStructure = volCube;

        // Test parameter interpolation by comparing volatilities at
        // intermediate points. If parameters interpolate correctly,
        // volatilities at intermediate tenors should be smooth
        // interpolations of neighboring volatilities.

        // Section 1: maturity = 10Y, tenor = 2Y
        SmileSection smileSection1 = volStructure.smileSection(
                new Period(10, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS));

        // Section 2: maturity = 10Y, tenor = 4Y
        SmileSection smileSection2 = volStructure.smileSection(
                new Period(10, TimeUnit.YEARS),
                new Period(4, TimeUnit.YEARS));

        // Section 3: maturity = 10Y, tenor = 3Y (intermediate)
        SmileSection smileSection3 = volStructure.smileSection(
                new Period(10, TimeUnit.YEARS),
                new Period(3, TimeUnit.YEARS));

        // Test forward rate interpolation (which depends on parameter
        // interpolation)
        double forward1 = smileSection1.atmLevel();
        double forward2 = smileSection2.atmLevel();
        double forward3 = smileSection3.atmLevel();
        double forward12 = 0.5 * (forward1 + forward2);

        double tolerance = 1.0e-4;
        if (Math.abs(forward3 - forward12) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of ZABR forward "
                    + "parameters:"
                    + "\nforward at 2Y = " + forward1
                    + "\nforward at 4Y = " + forward2
                    + "\nexpected at 3Y = " + forward12
                    + "\nobserved at 3Y = " + forward3);
        }

        // Test ATM volatility smoothness
        // (indirect parameter interpolation test)
        double vol1 = smileSection1.volatility(forward1);
        double vol2 = smileSection2.volatility(forward2);
        double vol3 = smileSection3.volatility(forward3);

        // Verify that interpolated section produces reasonable volatility
        // (between the two boundary sections or close to their average)
        double vol12 = 0.5 * (vol1 + vol2);
        double volTolerance = 5.0e-3;  // 50 bps tolerance for vol interpolation

        if (Math.abs(vol3 - vol12) > volTolerance) {
            vars.boostErrors.add("\nChecking interpolation of ZABR ATM "
                    + "volatility:"
                    + "\nvol at 2Y = " + vol1
                    + "\nvol at 4Y = " + vol2
                    + "\nexpected at 3Y = " + vol12
                    + "\nobserved at 3Y = " + vol3);
        }

        // Downcast to access individual calibrated ZABR parameters,
        // mirroring testSabrParameters which checks alpha/beta/rho/nu
        // directly. This catches bugs such as parameters stored in wrong
        // order in the params vector.
        ZabrSmileSection zs1 = asZabr(smileSection1);
        ZabrSmileSection zs2 = asZabr(smileSection2);
        ZabrSmileSection zs3 = asZabr(smileSection3);

        // alpha interpolation
        double alpha1 = zs1.model().alpha();
        double alpha2 = zs2.model().alpha();
        double alpha3 = zs3.model().alpha();
        if (Math.abs(alpha3 - 0.5 * (alpha1 + alpha2)) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of ZABR alpha:"
                    + "\nalpha at 2Y = " + alpha1
                    + "\nalpha at 4Y = " + alpha2
                    + "\nexpected at 3Y = " + 0.5 * (alpha1 + alpha2)
                    + "\nobserved at 3Y = " + alpha3);
        }

        // nu interpolation
        double nu1 = zs1.model().nu();
        double nu2 = zs2.model().nu();
        double nu3 = zs3.model().nu();
        if (Math.abs(nu3 - 0.5 * (nu1 + nu2)) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of ZABR nu:"
                    + "\nnu at 2Y = " + nu1
                    + "\nnu at 4Y = " + nu2
                    + "\nexpected at 3Y = " + 0.5 * (nu1 + nu2)
                    + "\nobserved at 3Y = " + nu3);
        }

        // rho interpolation
        double rho1 = zs1.model().rho();
        double rho2 = zs2.model().rho();
        double rho3 = zs3.model().rho();
        if (Math.abs(rho3 - 0.5 * (rho1 + rho2)) > tolerance) {
            vars.boostErrors.add("\nChecking interpolation of ZABR rho:"
                    + "\nrho at 2Y = " + rho1
                    + "\nrho at 4Y = " + rho2
                    + "\nexpected at 3Y = " + 0.5 * (rho1 + rho2)
                    + "\nobserved at 3Y = " + rho3);
        }

        // Note: beta and gamma interpolation are not tested here because
        // both parameters are fixed (isParameterFixed[1]=true,
        // isParameterFixed[4]=true), so their layer values do not change
        // between sections.

        vars.checkBoostErrors();
    }

    @Test
    public void testZabrWithNonUnitGamma() {
        System.out.println(
                "Testing ZABR swaption volatility cube with gamma != 1.0...");

        CommonVars vars = new CommonVars();

        // ZABR with gamma = 0.75 (different from SABR-like gamma=1.0)
        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 0.75);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;  // Fix beta (standard practice)
        isParameterFixed[4] = true;  // Fix gamma to test non-unit value behavior

        ZabrSwaptionVolatilityCube volCube =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        // With gamma != 1.0, ZABR should still produce valid volatilities
        // Tolerance may be slightly higher due to different smile shape
        double tolerance = 8.0e-4;
        vars.makeAtmVolTest(volCube, tolerance);

        // Verify smile section works with non-unit gamma
        SwaptionVolatilityStructure volStructure = volCube;
        SmileSection smileSection = volStructure.smileSection(
                new Period(5, TimeUnit.YEARS),
                new Period(5, TimeUnit.YEARS));

        assertNotNull(smileSection);

        // Test volatility smile shape - with gamma < 1, expect different
        // curvature
        double atmStrike = smileSection.atmLevel();
        double atmVol = smileSection.volatility(atmStrike);
        double otmVol = smileSection.volatility(atmStrike * 1.2);
        double itmVol = smileSection.volatility(atmStrike * 0.8);

        assertTrue(atmVol > 0.0);
        assertTrue(otmVol > 0.0);
        assertTrue(itmVol > 0.0);

        vars.checkBoostErrors();
    }

    @Test
    public void testZabrWithFreeGamma() {
        System.out.println(
                "Testing ZABR cube with free gamma calibration...");

        CommonVars vars = new CommonVars();

        // gamma=1.0 is only the initial guess; optimizer will calibrate it
        // freely
        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;   // fix beta

        ZabrSwaptionVolatilityCube cube =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        // Get a smile section and verify gamma was actually calibrated
        Period optionTenor = vars.cube.tenors.options.get(0);
        Period swapTenor = vars.cube.tenors.swaps.get(0);
        SmileSection section = cube.smileSection(optionTenor, swapTenor);
        assertNotNull(section);

        // Downcast to ZabrSmileSection to access calibrated gamma
        ZabrSmileSection zabrSection = asZabr(section);

        double calibratedGamma = zabrSection.model().gamma();

        // Gamma should be within valid bounds: ZabrSpecs::direct() caps
        // gamma at 1.9
        assertTrue(calibratedGamma > 0.1);
        assertTrue(calibratedGamma < 1.9);

        // Verify gamma actually moved from the initial guess of 1.0
        assertTrue(Math.abs(calibratedGamma - 1.0) > 1e-6);

        // Verify ATM vol is positive
        double atmStrike = section.atmLevel();
        double atmVol = section.volatility(atmStrike);
        assertTrue(atmVol > 0.0);
    }

    @Test
    public void testZabrShiftedVolThrows() {
        System.out.println(
                "Testing that ZABR cube throws for non-zero ATM vol shift...");

        CommonVars vars = new CommonVars();

        // Build an ATM vol matrix with a uniform 2% shift.
        // ZABR does not support shifted lognormal vols (QL_REQUIRE in
        // createSmileSection).
        int nOpts = vars.atm.tenors.options.size();
        int nSwps = vars.atm.tenors.swaps.size();
        Matrix shifts = new Matrix(nOpts, nSwps, 0.02);

        Handle<SwaptionVolatilityStructure> shiftedAtmVol =
                new Handle<>(new SwaptionVolatilityMatrix(
                        vars.conventions.calendar,
                        vars.conventions.optionBdc,
                        vars.atm.tenors.options,
                        vars.atm.tenors.swaps,
                        vars.atm.volsHandle,
                        vars.conventions.dayCounter,
                        false,
                        VolatilityType.ShiftedLognormal,
                        shifts), true);

        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;
        isParameterFixed[4] = true;

        ZabrSwaptionVolatilityCube volCube =
                new ZabrSwaptionVolatilityCube(shiftedAtmVol,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        // Querying the cube triggers lazy calibration and must throw
        // because the ATM vol structure has non-zero shift
        double strike = volCube.atmStrike(vars.cube.tenors.options.get(0),
                vars.cube.tenors.swaps.get(0));
        assertThrows(IllegalArgumentException.class, () ->
                volCube.volatility(vars.cube.tenors.options.get(0),
                        vars.cube.tenors.swaps.get(0), strike, true));
    }

    @Test
    public void testZabrAlternativeKernel() {
        System.out.println(
                "Testing ZABR cube with ZabrShortMaturityNormal kernel...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;
        isParameterFixed[4] = true;

        // Instantiate with a non-default kernel - this exercises the kernel
        // template parameter added in zabrswaptionvolatilitycube.hpp.
        // Note: ZabrLocalVolatility and ZabrFullFd kernels are not covered
        // here due to the high cost of finite-difference evaluation.
        ZabrSwaptionVolatilityCube volCube =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true,
                        ZabrEvaluation.ZabrShortMaturityNormal);

        // Smoke test: cube constructs and returns positive vol at ATM
        SmileSection section = volCube.smileSection(
                new Period(5, TimeUnit.YEARS),
                new Period(5, TimeUnit.YEARS));
        assertNotNull(section);

        double atmStrike = section.atmLevel();
        assertTrue(atmStrike > 0.0);
        assertTrue(section.volatility(atmStrike) > 0.0);
    }

    @Test
    public void testZabrObservability() {
        System.out.println("Testing ZABR volatility cube observability...");

        CommonVars vars = new CommonVars();

        List<List<Handle<Quote>>> parametersGuess =
                vars.makeZabrParametersGuess(0.2, 0.5, 0.4, 0.0, 1.0);
        boolean[] isParameterFixed = new boolean[5];
        Arrays.fill(isParameterFixed, false);
        isParameterFixed[1] = true;
        isParameterFixed[4] = true;

        // VolCube created before reference date change
        ZabrSwaptionVolatilityCube volCube0 =
                new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                        vars.cube.tenors.options,
                        vars.cube.tenors.swaps,
                        vars.cube.strikeSpreads,
                        vars.cube.volSpreadsHandle,
                        vars.swapIndexBase,
                        vars.shortSwapIndexBase,
                        vars.vegaWeighedSmileFit,
                        parametersGuess,
                        isParameterFixed,
                        true);

        Date referenceDate = Settings.instance.evaluationDate().Date();
        try {
            Settings.instance.evaluationDate().equal(
                    vars.conventions.calendar.advance(referenceDate,
                            new Period(1, TimeUnit.DAYS),
                            vars.conventions.optionBdc));

            // VolCube created after reference date change
            ZabrSwaptionVolatilityCube volCube1 =
                    new ZabrSwaptionVolatilityCube(vars.atmVolMatrix,
                            vars.cube.tenors.options,
                            vars.cube.tenors.swaps,
                            vars.cube.strikeSpreads,
                            vars.cube.volSpreadsHandle,
                            vars.swapIndexBase,
                            vars.shortSwapIndexBase,
                            vars.vegaWeighedSmileFit,
                            parametersGuess,
                            isParameterFixed,
                            true);

            // Both cubes must give identical volatilities: the cube
            // defined relative to today re-calibrates when the reference
            // date advances
            double dummyStrike = 0.03;
            for (int i = 0; i < vars.cube.tenors.options.size(); ++i) {
                for (int j = 0; j < vars.cube.tenors.swaps.size(); ++j) {
                    for (int k = 0; k < vars.cube.strikeSpreads.length; ++k) {
                        Period option = vars.cube.tenors.options.get(i);
                        Period swap = vars.cube.tenors.swaps.get(j);
                        double strike = dummyStrike
                                + vars.cube.strikeSpreads[k];
                        double v0 = volCube0.volatility(option, swap, strike,
                                false);
                        double v1 = volCube1.volatility(option, swap, strike,
                                false);
                        if (Math.abs(v0 - v1) > 1e-14) {
                            vars.boostErrors.add(
                                    "ZABR cube observability failed:"
                                            + " option tenor = " + option
                                            + " swap tenor = " + swap
                                            + " strike = " + strike
                                            + "  v0 = " + v0
                                            + "  v1 = " + v1
                                            + "  error = "
                                            + Math.abs(v0 - v1));
                        }
                    }
                }
            }
        } finally {
            Settings.instance.evaluationDate().equal(referenceDate);
        }

        vars.checkBoostErrors();
    }

    private static final class Flag implements Observer {
        private boolean up_ = false;

        void raise() {
            up_ = true;
        }

        void lower() {
            up_ = false;
        }

        boolean isUp() {
            return up_;
        }

        @Override
        public void registerWith(Observable observable) {
            observable.registerObserver(this);
        }

        @Override
        public void unregisterWithAll() {
        }

        @Override
        public void unregisterWith(Observable observable) {
            observable.unregisterObserver(this);
        }

        @Override
        public void update() {
            raise();
        }
    }
}
