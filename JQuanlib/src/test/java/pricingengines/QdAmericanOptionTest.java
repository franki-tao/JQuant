package pricingengines;

import jquant.AmericanExercise;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Settings;
import jquant.math.distributions.NormalDistribution;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.math.integrals.Integrator;
import jquant.math.randomnumbers.InverseCumulativeRng;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;
import jquant.math.statistics.IncrementalStatistics;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.BaroneAdesiWhaleyApproximationEngine;
import jquant.pricingengines.vanilla.BjerksundStenslandApproximationEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.pricingengines.vanilla.JuQuadraticApproximationEngine;
import jquant.pricingengines.vanilla.QdFpAmericanEngine;
import jquant.pricingengines.vanilla.QdFpIterationScheme;
import jquant.pricingengines.vanilla.QdFpLegendreScheme;
import jquant.pricingengines.vanilla.QdFpLegendreTanhSinhScheme;
import jquant.pricingengines.vanilla.QdFpTanhSinhIterationScheme;
import jquant.pricingengines.vanilla.QdPlusAmericanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static jquant.Compounding.Continuous;
import static jquant.math.MathUtils.NULL_SIZE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * QD+ and QD fixed-point American engines from QuantLib
 * {@code test-suite/americanoption.cpp}.
 */
public class QdAmericanOptionTest {

    // ------------------------------------------------------------------
    // QD+
    // ------------------------------------------------------------------

    @Test
    public void testQdPlusBoundaryValues() {
        final double S = 100;
        final double K = 120;
        final double r = 0.1;
        final double q = 0.03;
        final double sigma = 0.25;
        final double maturity = 5.0;

        QdPlusAmericanEngine qrPlusEngine =
                new QdPlusAmericanEngine(null, 10);

        double[][] testCaseSpecs = {
                {4.9, 87.76960949965387},
                {4.0, 88.39053003614612},
                {2.5, 90.14327315762256},
                {1.0, 94.49793803095984},
                {0.1, 106.2588964442338}
        };

        for (double[] testCaseSpec : testCaseSpecs) {
            QdPlusAmericanEngine.BoundaryResult calculated =
                    qrPlusEngine.putExerciseBoundaryAtTau(
                            S, K, r, q, sigma, maturity, testCaseSpec[0]);

            double boundary = calculated.boundary();
            int nrEvaluations = calculated.evaluations();

            double expected = testCaseSpec[1];
            double diff = Math.abs(boundary - expected);
            double tol = 1e-12;

            if (diff > tol) {
                fail("failed to reproduce QR+ boundary approximation"
                        + "\n    calculated: " + boundary
                        + "\n    expected:   " + expected
                        + "\n    difference: " + diff
                        + "\n    tolerance : " + tol);
            }

            if (nrEvaluations > 10) {
                fail("failed to reproduce rate of convergence"
                        + "\n    evaluations: " + nrEvaluations
                        + "\n    max eval :   " + 10);
            }
        }
    }

    @Test
    public void testQdPlusBoundaryConvergence() {
        final double S = 100;
        final double sigma = 0.25;
        final double maturity = 10.0;

        record TestCaseSpec(double r, double q, double strike,
                            int maxEvaluations) {
        }

        TestCaseSpec[] testCases = {
                new TestCaseSpec(0.10, 0.03, 120, 2000),
                new TestCaseSpec(0.0001, 0.03, 120, 2000),
                new TestCaseSpec(0.0001, 0.000002, 120, 2000),
                new TestCaseSpec(0.01, 0.75, 120, 2000),
                new TestCaseSpec(0.03, 0.0, 30, 2000),
                new TestCaseSpec(0.03, 0.0, 1e7, 2500),
                new TestCaseSpec(0.075, 0.0, 1e-8, 2000)
        };

        QdPlusAmericanEngine.SolverType[] solverTypes = {
                QdPlusAmericanEngine.SolverType.Brent,
                QdPlusAmericanEngine.SolverType.Newton,
                QdPlusAmericanEngine.SolverType.Ridder,
                QdPlusAmericanEngine.SolverType.Halley,
                QdPlusAmericanEngine.SolverType.SuperHalley
        };

        for (TestCaseSpec testCase : testCases) {
            for (QdPlusAmericanEngine.SolverType solverType : solverTypes) {
                QdPlusAmericanEngine qrPlusEngine =
                        new QdPlusAmericanEngine(null, NULL_SIZE, solverType,
                                1e-8);

                int nrEvaluations = 0;

                for (double t = 0.0; t < maturity; t += 0.1) {
                    QdPlusAmericanEngine.BoundaryResult calculated =
                            qrPlusEngine.putExerciseBoundaryAtTau(
                                    S, testCase.strike(), testCase.r(),
                                    testCase.q(), sigma, maturity, t);
                    nrEvaluations += calculated.evaluations();
                }

                int maxEvaluations =
                        (solverType == QdPlusAmericanEngine.SolverType.Halley
                                || solverType
                                    == QdPlusAmericanEngine.SolverType.SuperHalley)
                                ? 750 : testCase.maxEvaluations();

                if (nrEvaluations > maxEvaluations) {
                    fail("QR+ boundary approximation failed to converge"
                            + "\n    evaluations: " + nrEvaluations
                            + "\n    max eval:    " + maxEvaluations
                            + "\n    Solver:      " + solverType
                            + "\n    r :          " + testCase.r()
                            + "\n    q :          " + testCase.q()
                            + "\n    K :          " + testCase.strike());
                }
            }
        }
    }

    @Test
    public void testQdAmericanEngines() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(1, Month.JUNE, 2022);
        Settings.instance.evaluationDate().equal(today);

        record OptionSpec(Option.Type optionType, double spot, double strike,
                          int maturityInDays, double volatility, double r,
                          double q, double expectedValue, double precision) {
        }

        // high precision edge cases
        OptionSpec[] edgeTestCases = {
                // standard put option
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 3650, 0.25, 0.10, 0.03, 22.97383256003585, 1e-8),
                // call-put parity on standard option
                new OptionSpec(Option.Type.Call, 120.0, 100.0, 3650, 0.25, 0.03, 0.10, 22.97383256003585, 1e-8),

                // zero strike put
                new OptionSpec(Option.Type.Put, 100.0, 0.0, 365, 0.25, 0.02, 0.02, 0.0, 1e-14),
                new OptionSpec(Option.Type.Put, 100.0, 1e-8, 365, 0.25, 0.02, 0.02, 0.0, 1e-14),

                // zero strike call
                new OptionSpec(Option.Type.Call, 100.0, 0.0, 365, 0.25, 0.05, 0.01, 100.0, 1e-11),
                new OptionSpec(Option.Type.Call, 100.0, 1e-7, 365, 0.25, 0.05, 0.01, 100.0 - 1e-7, 1e-9),

                // zero vol call
                new OptionSpec(Option.Type.Call, 100.0, 50.0, 365, 0.0, 0.05, 0.01, 51.4435121498811085, 1e-10),
                new OptionSpec(Option.Type.Call, 100.0, 50.0, 365, 1e-8, 0.05, 0.01, 51.4435121498811156, 1e-8),

                // zero vol put 1
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 4 * 3650, 1e-6, 0.01, 0.50, 108.980920365700442, 1e-4),
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 4 * 3650, 0.0, 0.01, 0.50, 108.980904561184602, 1e-10),

                // zero vol put 2
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 365, 1e-7, 0.05, 0.01, 20.0, 1e-9),
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 365, 0.0, 0.05, 0.01, 20.0, 1e-12),

                // zero vol put 3
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 365, 1e-7, 0.00, 0.05, 24.8770575499286082, 1e-8),
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 365, 0.0, 0.00, 0.05, 24.8770575499286082, 1e-10),

                // zero spot put
                new OptionSpec(Option.Type.Put, 1e-6, 120.0, 365, 0.25, -0.075, 0.05, 129.346097154926355, 1e-9),
                new OptionSpec(Option.Type.Put, 0.0, 120.0, 365, 0.25, -0.075, 0.05, 129.346098106155779, 1e-10),

                // zero spot call
                new OptionSpec(Option.Type.Call, 1e-6, 120.0, 365, 0.25, 0.075, 0.05, 0.0, 1e-14),
                new OptionSpec(Option.Type.Call, 0.0, 120.0, 365, 0.25, 0.075, 0.05, 0.0, 1e-14),

                // put option with one day left
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 1, 0.25, 0.05, 0.0, 20.0, 1e-10),

                // put option at maturity
                new OptionSpec(Option.Type.Put, 100.0, 120.0, 0, 0.25, 0.05, 0.0, 0.0, 1e-14),

                // zero everything
                new OptionSpec(Option.Type.Put, 0.0, 0.0, 365, 0.0, 0.0, 0.0, 0.0, 1e-14),

                // zero strike call with zero vol
                new OptionSpec(Option.Type.Call, 100.0, 1e-7, 365, 1e-8, 0.05, 0.025, 100.0 - 1e-7, 1e-8),
                new OptionSpec(Option.Type.Call, 100.0, 0.0, 365, 1e-8, 0.05, 0.025, 100.0, 1e-8),
                new OptionSpec(Option.Type.Call, 100.0, 1e-7, 365, 0.0, 0.05, 0.025, 100.0 - 1e-7, 1e-8),
                new OptionSpec(Option.Type.Call, 100.0, 0.0, 365, 0.0, 0.05, 0.025, 100.0, 1e-8),

                // zero spot call with zero vol
                new OptionSpec(Option.Type.Call, 1e-8, 100, 365, 1e-8, 0.05, 0.025, 0.0, 1e-10),
                new OptionSpec(Option.Type.Call, 0.0, 100, 365, 0.0, 0.05, 0.025, 0.0, 1e-14),

                // zero interest rate call
                new OptionSpec(Option.Type.Call, 100, 100, 365, 0.25, 0.0, 0.025, 8.871505915120776, 1e-8),

                // zero dividend rate call
                new OptionSpec(Option.Type.Call, 100, 100, 365, 0.25, 0.05, 0.0, 12.3359989303687243, 1e-8),

                // extreme spot call
                new OptionSpec(Option.Type.Call, 1e10, 100, 365, 0.25, 0.01, 0.05, 1e10 - 100.0, -1),

                // extreme strike call
                new OptionSpec(Option.Type.Call, 100, 1e10, 365, 0.25, 0.01, 0.05, 0.0, 1e-14),

                // extreme vol call
                new OptionSpec(Option.Type.Call, 100, 100, 365, 100.0, 0.01, 0.05, 99.9874942266127, 1e-8),

                // extreme dividend yield call
                new OptionSpec(Option.Type.Call, 100, 100, 365, 0.25, 0.10, 10.0, 0.1159627202107989, 1e-8),

                // extreme maturity call
                new OptionSpec(Option.Type.Call, 100, 100, 170 * 365, 0.25, 0.01, 0.002, 80.37468392429741, 1e-8)
        };

        double[] pdeValues = {
                581.46895, 113.78442, 581.44547, 1408.579, 49.19448, 1060.27367,
                834.83366, 176.48305, 120.38008, 307.11264, 602.7006, 233.80171,
                204.74596, 0.30987, 0, 0, 5.36215, 0.01711, 0, 84.51193, 0.67131,
                0.06414, 152.67188, 54.75257, 90.31861, 168.50289, 18.38926, 0,
                282.4995, 0, 0.08428, 12.30929, 42.26359, 139.87748, 0.28724, 0.00421,
                0, 0.00206, 0, 658.60427, 140.51139, 23.17387, 0.35612, 0, 909.14828,
                0, 0.11549, 5.46749, 144.25428, 2576.6754, 562.16484, 0, 122.725,
                383.48463, 278.7447, 3.52566, 82.34348, 81.06139, 0, 10.42824,
                4.95917, 25.28602, 31.38869, 3.53697, 0, 0.012, 0, 0.4263, 162.16184,
                0.4618, 97.714, 283.03442, 0.38176, 70.25367, 134.94142, 2.19293,
                226.4746, 76.74309, 46.03123, 15.76214, 0.01666, 1806.26208, 0,
                103.93726, 6.82956, 337.81301, 0.64236, 677.63248, 25.01763,
                443.79052, 1793.78327, 118.6293, 185.79849, 11.59313, 679.01736,
                17.99005, 403.57554, 1.67418, 0, 0.03795, 3326.09089, 71.1996,
                0, 485.10353, 0, 1681.25166, 0, 43.15432, 0.75825, 0.05895, 34.71493,
                0.00015, 5.58671, 115.98793, 37.7713, 399.24494, 0.00766, 445.42207,
                152.65397, 0, 47.05874, 0.96921, 14.21875, 257.84754, 109.62533,
                2553.99295, 138.46663, 192.33614, 81.41877, 18.21403, 113.926,
                27.28409, 174.77093, 42.70527, 0.90326, 0, 967.9901, 616.0143,
                253.56442, 0.00397, 2493.82098, 9.29406, 11.00023, 0, 0, 234.12481,
                0, 72.46356, 0, 9.00932, 48.67934, 29.42756, 13.4271, 0, 0, 0, 0, 20.71417,
                48.57474, 2.26452, 0, 109.0243, 0, 21.26801, 1.21164, 0, 86.25232,
                36.00437, 4.53844, 7.40503, 313.53602, 379.76105, 165.84347, 77.19665,
                9.02466, 0.10634, 214.84982, 6.13387, 133.44645, 303.25953, 0,
                134.26724, 246.89804, 0, 123.32975, 32.83429, 9.56819, 7.42582, 0,
                73.82832, 196.84831, 0.00001, 72.70391, 2173.8649, 123.00513,
                153.83539, 21.63003, 209.84752, 30.12425, 0, 197.6502, 0, 164.02863,
                7.65143, 56.57631, 2392.70018, 0, 0, 34.23457, 171.08459, 0.49387,
                31.13395, 237.68801, 0.01262, 0, 0, 0, 0, 41.56635, 0, 8.41535, 55.01775,
                310.50094, 0, 14.85456, 174.34018, 7.19772, 0.00001, 0, 91.70874,
                0.00001, 17.51724, 0.00587, 0, 532.24902, 2.05553, 36.80843, 0,
                33.39288, 0.00006, 0.04439, 1.3434, 0, 0.41816, 926.37642, 0, 247.61559,
                151.98965, 0.35243, 4.33198, 23294.47744, 0.00791, 12.51996, 53.47727,
                167.95572, 0.0062, 6.8482, 0, 347.83408, 852.85742, 558.21422, 0,
                53.89293, 78.61011, 187.3978, 9.18927, 0.00553, 113.48101, 1467.30556,
                74.82251, 94.84476, 0, 101.3649, 59.27007, 0, 773.81251, 0, 542.7889, 0,
                68.96209, 96.0435, 0.00004, 0.10738, 0.00187, 324.97758, 245.68455,
                30.52818, 129.84472, 0, 46.86288, 368.41675, 139.29763, 4.4393, 16.29594,
                25.7554, 64.02621, 89.41363, 0.62751, 219.65237, 0.26039, 0, 12.02172,
                101.97733, 69.37456, 45.81122, 1263.33603, 164.31607, 15.88788, 0, 48.77797,
                0.13233, 147.16808, 10.31217, 7.50634, 7.48611, 177.95409, 225.77562, 3.56947,
                0.02531, 4.88869, 8.76632, 0, 0, 0.02214, 305.08468, 44.52185, 182.17332,
                538.31458, 0, 46.97229, 0, 31.94202, 410.43038, 0, 70.35432, 15.58346, 74.14177,
                953.67663, 11.79128, 59.83061, 0, 37.86557, 1184.22731, 2411.37823, 0, 0, 0, 0,
                49.3179, 236.38654, 21.36225, 218.048, 517.57006, 0, 0, 12.52933, 256.71967,
                0.00025, 1.47981, 158.19166, 0, 1923.70709, 4.94441, 1199.81196, 45.92353,
                85.73255, 14.91338, 88.81459, 21.42459, 3456.9466, 31.97838, 233.26863,
                49.34801, 2684.07758, 0, 0, 32.24149, 0, 111.79552, 0.00506, 8.77602, 0,
                406.54213, 0.32974, 365.53998, 1.49714, 19.65603, 37.33877, 205.06928,
                0.01805, 589.23478, 9.58273, 0.02946, 286.48706, 463.34512, 528.21392, 0,
                47.71294, 21.0864, 114.81771, 80.489, 21.30905, 41.95873, 19.03598, 156.09295,
                0, 73.6509, 0, 0, 168.17576, 0, 32.71243, 36.75044, 177.64583, 0.05618,
                156.38616, 1370.4754, 24.5976, 59.83173, 0, 354.93074, 34.96889, 0.00532,
                16.95287, 1259.72993, 241.05777, 18.9778, 0.57635, 43.98093, 25.2678,
                369.39896, 0.31549, 0, 31.95512, 101.60559, 11.22079, 970.16273, 0, 0,
                1.55445, 0, 18.6067, 0, 1124.20117, 52.67762, 10.38273, 0, 10.22588, 251.27813,
                0, 431.82244, 0, 1.31252, 0, 84.72154, 100.98411, 160.95557, 129.51372,
                0.00026, 103.81663, 421.64767, 0.00031, 0, 104.48529, 162.59225, 0,
                1504.0869, 88.11253, 4.14052, 0.07195, 203.78754, 0.00002, 42.5395, 0,
                17.05087, 26.89157, 64.64923, 0, 390.87453, 124.55406, 0.01018, 94.23963
        };

        List<OptionSpec> testCaseSpecs = new ArrayList<>();
        InverseCumulativeRng rng = new InverseCumulativeRng(
                new MersenneTwisterUniformRng(12345L),
                new jquant.math.distributions.InverseCumulativeNormal());

        for (double pdeValue : pdeValues) {
            Option.Type optionType = rng.next().value > 0
                    ? Option.Type.Call : Option.Type.Put;
            double spot = 100 * Math.exp(1.5 * rng.next().value);
            double strike = 100 * Math.exp(1.5 * rng.next().value);
            int maturityInDays = (int) (1 + 365 * Math.exp(2 * rng.next().value));
            double vol = 0.5 * Math.exp(rng.next().value);
            double r = 0.10 * Math.exp(rng.next().value);
            double q = 0.10 * Math.exp(rng.next().value);

            testCaseSpecs.add(new OptionSpec(optionType, spot, strike,
                    maturityInDays, vol, r, q, pdeValue, -1));
        }

        testCaseSpecs.addAll(List.of(edgeTestCases));

        SimpleQuote spot = new SimpleQuote(1.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));

        QdPlusAmericanEngine qrPlusAmericanEngine =
                new QdPlusAmericanEngine(bsProcess, 8,
                        QdPlusAmericanEngine.SolverType.Halley, 1e-10);

        for (OptionSpec testCaseSpec : testCaseSpecs) {
            Date maturityDate = today.add(
                    new Period(testCaseSpec.maturityInDays(), TimeUnit.DAYS));

            spot.setValue(testCaseSpec.spot());
            rRate.setValue(testCaseSpec.r());
            qRate.setValue(testCaseSpec.q());
            vol.setValue(testCaseSpec.volatility());

            EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(testCaseSpec.optionType(),
                            testCaseSpec.strike()),
                    new AmericanExercise(today, maturityDate));
            option.setPricingEngine(qrPlusAmericanEngine);

            double calculated = option.NPV();
            double expected = testCaseSpec.expectedValue();
            double precision = testCaseSpec.precision();

            if ((precision > 0
                    && Math.abs(expected - calculated) > precision)
                    || (precision < 0 && expected > 0.1
                        && Math.abs(calculated - expected) / expected > 0.005)
                    || (precision < 0 && expected <= 0.1
                        && Math.abs(expected - calculated) > 5e-4)) {
                fail("QR+ boundary approximation failed to "
                        + "reproduce cached edge and PDE values for "
                        + "\n    OptionType: " + testCaseSpec.optionType()
                        + "\n    spot:       " + spot.value()
                        + "\n    strike:     " + testCaseSpec.strike()
                        + "\n    r:          " + rRate.value()
                        + "\n    q:          " + qRate.value()
                        + "\n    vol:        " + vol.value()
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected);
            }
        }
    }

    @Test
    public void testQdFpIterationScheme() {
        double tol = 1e-8;
        int l = 32, m = 6, n = 18, p = 36;

        QdFpIterationScheme[] schemes = {
                new QdFpLegendreScheme(l, m, n, p),
                new QdFpLegendreTanhSinhScheme(l, m, n, tol),
                new QdFpTanhSinhIterationScheme(m, n, tol)
        };

        NormalDistribution nd = new NormalDistribution();

        for (QdFpIterationScheme scheme : schemes) {
            assertTrue(n == scheme.getNumberOfChebyshevInterpolationNodes(),
                    "number of interpolation nodes");
            assertTrue(1 == scheme.getNumberOfJacobiNewtonFixedPointSteps(),
                    "number of Jacobi-Newton steps");
            assertTrue(m - 1 == scheme.getNumberOfNaiveFixedPointSteps(),
                    "number of naive fixed-point steps");

            double fpIntegral = scheme.getFixedPointIntegrator()
                    .value(x -> nd.value(x), -10.0, 10.0);
            checkSmall(fpIntegral - 1.0, tol,
                    "fixed-point integrator");

            double boundaryIntegral =
                    scheme.getExerciseBoundaryToPriceIntegrator()
                            .value(x -> nd.value(x), -10.0, 10.0);
            checkSmall(boundaryIntegral - 1.0, tol,
                    "exercise boundary integrator");
        }
    }

    @Test
    public void testAndersenLakeHighPrecisionExample() {
        record SchemeSpec(int l, int m, int n, double r, double[] expected,
                          double tol) {
        }

        SchemeSpec[] testCases = {
                new SchemeSpec(24, 3, 9, 0.05,
                        new double[]{0.1069528125898476, 0.1069524359360852}, 1e-6),
                new SchemeSpec(5, 1, 4, 0.05,
                        new double[]{0.1070237787625299, 0.1070042740171235}, 1e-3),
                new SchemeSpec(11, 2, 5, 0.05,
                        new double[]{0.106938750864602, 0.1069479057531648}, 1e-4),
                new SchemeSpec(35, 8, 16, 0.05,
                        new double[]{0.1069527032381714, 0.106952558361499}, 1e-9),
                new SchemeSpec(65, 8, 32, 0.05,
                        new double[]{0.1069527028247546, 0.1069526779971959}, 1e-11),
                new SchemeSpec(5, 1, 4, 0.075,
                        new double[]{0.3674420299196104, 0.3674766444325588}, 1e-3),
                new SchemeSpec(11, 2, 5, 0.075,
                        new double[]{0.3671056766787473, 0.3671024005532715}, 1e-4),
                new SchemeSpec(35, 8, 16, 0.075,
                        new double[]{0.3671116758420414, 0.3671111055677869}, 1e-9),
                new SchemeSpec(65, 8, 32, 0.075,
                        new double[]{0.3671112309062572, 0.3671111267813689}, 1e-11)
        };

        DayCounter dc = new Actual365Fixed();
        Date today = new Date(25, Month.JULY, 2022);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(100.0);
        double strike = 100.0;
        double q = 0.05;
        double vol = 0.25;
        Date maturityDate = today.add(new Period(1, TimeUnit.YEARS));

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);

        QdFpAmericanEngine.FixedPointEquation[] schemes = {
                QdFpAmericanEngine.FixedPointEquation.FP_A,
                QdFpAmericanEngine.FixedPointEquation.FP_B
        };

        for (SchemeSpec testCase : testCases) {
            int l = testCase.l();
            int m = testCase.m();
            int n = testCase.n();
            double r = testCase.r();
            double tol = testCase.tol();

            BlackScholesMertonProcess bsProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(flatRate(today, q, dc), true),
                            new Handle<>(flatRate(today, r, dc), true),
                            new Handle<>(flatVol(today, vol, dc), true));

            EuropeanOption americanOption = new EuropeanOption(payoff,
                    new AmericanExercise(today, maturityDate));
            EuropeanOption europeanOption = new EuropeanOption(payoff,
                    new EuropeanExercise(maturityDate));

            europeanOption.setPricingEngine(
                    new AnalyticEuropeanEngine(bsProcess));
            double europeanNPV = europeanOption.NPV();

            for (int i = 0; i < schemes.length; ++i) {
                americanOption.setPricingEngine(
                        new QdFpAmericanEngine(bsProcess,
                                new QdFpLegendreTanhSinhScheme(l, m, n, tol),
                                schemes[i]));

                double americanNPV = americanOption.NPV();
                double americanPremium = americanNPV - europeanNPV;

                double diff = Math.abs(
                        americanPremium - testCase.expected()[i]);
                if (diff > tol) {
                    fail("failed to reproduce high precision literature values"
                            + "\n    FP-Scheme: " + schemes[i]
                            + "\n    r        : " + r
                            + "\n    (l,m,n)  : (" + l + "," + m + "," + n + ")"
                            + "\n    diff     : " + diff
                            + "\n    tol      : " + tol);
                }
            }
        }
    }

    @Test
    public void testQdEngineStandardExample() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(1, Month.JUNE, 2022);
        Settings.instance.evaluationDate().equal(today);

        double S = 100;
        double K = 95;
        double r = 0.075;
        double q = 0.05;
        double sigma = 0.25;
        Date maturityDate = today.add(new Period(1, TimeUnit.YEARS));

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(S), true),
                new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, r, dc), true),
                new Handle<>(flatVol(today, sigma, dc), true));

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, K);

        EuropeanOption europeanOption = new EuropeanOption(payoff,
                new EuropeanExercise(maturityDate));
        europeanOption.setPricingEngine(
                new AnalyticEuropeanEngine(bsProcess));

        EuropeanOption americanOption = new EuropeanOption(payoff,
                new AmericanExercise(today, maturityDate));

        QdFpAmericanEngine.FixedPointEquation[] schemes = {
                QdFpAmericanEngine.FixedPointEquation.FP_A,
                QdFpAmericanEngine.FixedPointEquation.FP_B
        };
        double[] expected = {0.2386475283369327, 0.2386596962737606};

        for (int i = 0; i < schemes.length; ++i) {
            americanOption.setPricingEngine(
                    new QdFpAmericanEngine(bsProcess,
                            new QdFpLegendreScheme(32, 2, 15, 48),
                            schemes[i]));
            double calculated = americanOption.NPV()
                    - europeanOption.NPV();

            double tol = 7e-15;
            double diff = Math.abs(calculated - expected[i]);

            if (diff > tol) {
                fail("failed to reproduce high precision test values"
                        + "\n    diff     : " + diff
                        + "\n    tol      : " + tol);
            }
        }
    }

    /**
     * Test-local iteration scheme using Gauss-Lobatto integration, ported
     * from the {@code QdFpGaussLobattoScheme} helper in the QuantLib test.
     */
    private static class QdFpGaussLobattoScheme
            implements QdFpIterationScheme {
        private final int m_, n_;
        private final Integrator integrator_;

        QdFpGaussLobattoScheme(int m, int n, double eps) {
            m_ = m;
            n_ = n;
            integrator_ = new GaussLobattoIntegral(
                    100000, QL_MAX_REAL, 0.1 * eps, true);
        }

        @Override
        public int getNumberOfChebyshevInterpolationNodes() {
            return n_;
        }

        @Override
        public int getNumberOfNaiveFixedPointSteps() {
            return m_ - 1;
        }

        @Override
        public int getNumberOfJacobiNewtonFixedPointSteps() {
            return 1;
        }

        @Override
        public Integrator getFixedPointIntegrator() {
            return integrator_;
        }

        @Override
        public Integrator getExerciseBoundaryToPriceIntegrator() {
            return integrator_;
        }
    }

    @Test
    public void testBulkQdFpAmericanEngine() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(1, Month.JUNE, 2022);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(1.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);

        int[] T = {30, 182, 365};
        double[] rf = {0.02, 0.04, 0.06, 0.1};
        double[] qy = {0, 0.04, 0.08, 0.12};
        double[] S = {25, 75, 100, 125, 200};
        double[] sig = {0.1, 0.25, 0.6};

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 100);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));

        QdFpAmericanEngine qdFpFastAmericanEngine =
                new QdFpAmericanEngine(bsProcess,
                        QdFpAmericanEngine.fastScheme());

        QdFpAmericanEngine qdFpAccurateAmericanEngine =
                new QdFpAmericanEngine(bsProcess,
                        QdFpAmericanEngine.accurateScheme());

        QdFpAmericanEngine qdFpGaussLobattoAmericanEngine =
                new QdFpAmericanEngine(bsProcess,
                        new QdFpGaussLobattoScheme(3, 7, 1e-5));

        IncrementalStatistics statsAccurate = new IncrementalStatistics();
        IncrementalStatistics statsLobatto = new IncrementalStatistics();
        for (int t : T) {
            Date maturityDate = today.add(new Period(t, TimeUnit.DAYS));
            EuropeanOption option = new EuropeanOption(payoff,
                    new AmericanExercise(today, maturityDate));

            for (double r : rf) {
                rRate.setValue(r);
                for (double q : qy) {
                    qRate.setValue(q);
                    for (double v : sig) {
                        vol.setValue(v);
                        for (double s : S) {
                            spot.setValue(s);

                            option.setPricingEngine(qdFpFastAmericanEngine);
                            double fast = option.NPV();

                            option.setPricingEngine(
                                    qdFpAccurateAmericanEngine);
                            double accurate = option.NPV();
                            statsAccurate.add(Math.abs(fast - accurate), 1.0);

                            option.setPricingEngine(
                                    qdFpGaussLobattoAmericanEngine);
                            double lobatto = option.NPV();
                            statsLobatto.add(
                                    Math.abs(accurate - lobatto), 1.0);
                        }
                    }
                }
            }
        }

        double tolStdDev = 1e-4;
        if (statsAccurate.standardDeviation() > tolStdDev) {
            fail("failed to reproduce low RMSE with fast American engine"
                    + "\n    RMSE diff: "
                    + statsAccurate.standardDeviation()
                    + "\n    tol      : " + tolStdDev);
        }
        if (statsLobatto.standardDeviation() > tolStdDev) {
            fail("failed to reproduce low RMSE with fast Lobatto American engine"
                    + "\n    RMSE diff: "
                    + statsLobatto.standardDeviation()
                    + "\n    tol      : " + tolStdDev);
        }

        double tolMax = 2.5e-3;
        if (statsAccurate.max() > tolMax) {
            fail("failed to reproduce low max deviation "
                    + "with fast American engine"
                    + "\n    max diff: " + statsAccurate.max()
                    + "\n    tol     : " + tolMax);
        }
        if (statsLobatto.max() > tolMax) {
            fail("failed to reproduce low max deviation "
                    + "with fast Lobatto American engine"
                    + "\n    max diff: " + statsLobatto.max()
                    + "\n    tol     : " + tolMax);
        }
    }

    @Test
    public void testQdEngineWithLobattoIntegral() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(5, Month.NOVEMBER, 2022);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(36);
        double K = 40;
        double r = 0.075;
        double q = 0.01;
        double sigma = 0.40;
        Date maturityDate = today.add(new Period(2, TimeUnit.YEARS));

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, r, dc), true),
                new Handle<>(flatVol(today, sigma, dc), true));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Put, K),
                new AmericanExercise(today, maturityDate));

        QdFpAmericanEngine.FixedPointEquation[] schemes = {
                QdFpAmericanEngine.FixedPointEquation.FP_A,
                QdFpAmericanEngine.FixedPointEquation.FP_B
        };

        QdFpGaussLobattoScheme gaussLobattoScheme =
                new QdFpGaussLobattoScheme(10, 30, 1e-10);

        double[] spots = {36, 40 - 1e-8, 40, 40 + 1e-8, 50};

        for (QdFpAmericanEngine.FixedPointEquation scheme : schemes) {
            QdFpAmericanEngine highPrecisionEngine =
                    new QdFpAmericanEngine(bsProcess,
                            QdFpAmericanEngine.highPrecisionScheme(), scheme);
            QdFpAmericanEngine lobattoEngine =
                    new QdFpAmericanEngine(bsProcess,
                            gaussLobattoScheme, scheme);

            for (double s : spots) {
                spot.setValue(s);

                option.setPricingEngine(highPrecisionEngine);
                double highPrecisionNPV = option.NPV();

                option.setPricingEngine(lobattoEngine);
                double lobattoNPV = option.NPV();

                double diff = Math.abs(lobattoNPV - highPrecisionNPV);
                double tol = 1e-11;

                if (diff > tol || Double.isNaN(lobattoNPV)) {
                    fail("failed to reproduce high precision American "
                            + "option values with QD+ fixed point and "
                            + "Lobatto integration"
                            + "\n    FP-Scheme: " + scheme
                            + "\n    spot     : " + s
                            + "\n    diff     : " + diff
                            + "\n    tol      : " + tol);
                }
            }
        }
    }

    @Test
    public void testQdEnginesNotBelowExerciseValue() {
        Date today = new Date(15, Month.AUGUST, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);

        record Case(Option.Type type, double strike, double r, double q,
                    double v, int years) {
        }

        Case[] cases = {
                new Case(Option.Type.Call, 60.0, -0.10, 0.06, 0.40, 10),
                new Case(Option.Type.Call, 80.0, -0.05, 0.06, 0.20, 10),
                new Case(Option.Type.Put, 140.0, 0.06, 0.00, 0.20, 10)
        };

        for (Case c : cases) {
            BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(flatRate(today, c.q(), dc), true),
                            new Handle<>(flatRate(today, c.r(), dc), true),
                            new Handle<>(flatVol(today, c.v(), dc), true));

            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(c.type(), c.strike());
            AmericanExercise exercise = new AmericanExercise(today,
                    today.add(new Period(c.years(), TimeUnit.YEARS)));
            double intrinsic = payoff.value(spot.value());

            jquant.PricingEngine[] engines = {
                    new QdPlusAmericanEngine(process),
                    new QdFpAmericanEngine(process)
            };

            for (jquant.PricingEngine engine : engines) {
                EuropeanOption option = new EuropeanOption(payoff, exercise);
                option.setPricingEngine(engine);

                double calculated = option.NPV();
                if (calculated < intrinsic) {
                    fail("value below exercise"
                            + "\n    option type:      " + c.type()
                            + "\n    spot value:       " + spot.value()
                            + "\n    strike:           " + c.strike()
                            + "\n    dividend yield:   " + c.q()
                            + "\n    risk-free rate:   " + c.r()
                            + "\n    reference date:   " + today
                            + "\n    maturity:         "
                            + exercise.lastDate()
                            + "\n    volatility:       " + c.v()
                            + "\n    intrinsic value:  " + intrinsic
                            + "\n    calculated:       " + calculated
                            + "\n    difference:       "
                            + (intrinsic - calculated)
                            + "\n    tolerance:        0.0");
                }
            }
        }
    }

    @Test
    public void testQdNegativeDividendYield() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(5, Month.DECEMBER, 2022);
        Settings.instance.evaluationDate().equal(today);
        Date maturityDate = today.add(new Period(18, TimeUnit.MONTHS));

        double K = 90;
        SimpleQuote spot = new SimpleQuote(100);
        SimpleQuote qRate = new SimpleQuote(0);
        SimpleQuote rRate = new SimpleQuote(0);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, 0.4, dc), true));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Put, K),
                new AmericanExercise(today, maturityDate));

        QdPlusAmericanEngine qdPlusEngine =
                new QdPlusAmericanEngine(process);
        QdFpAmericanEngine qdFpEngine = new QdFpAmericanEngine(process);
        FdBlackScholesVanillaEngine fdmEngine =
                new FdBlackScholesVanillaEngine(process, 800, 200);

        double[] rRates = {0.025, 0.5, 0.0, 1e-6};
        double[] qRates = {-0.05, -0.1, -0.5, 0.0};

        for (double r : rRates) {
            rRate.setValue(r);
            for (double q : qRates) {
                qRate.setValue(q);

                option.setPricingEngine(qdFpEngine);
                double qdFpNPV = option.NPV();

                option.setPricingEngine(qdPlusEngine);
                double qdPlusNPV = option.NPV();

                option.setPricingEngine(fdmEngine);
                double fdmNPV = option.NPV();

                double tol = 1.5e-2;
                double diffFdmQdFp = Math.abs(fdmNPV - qdFpNPV);

                if (diffFdmQdFp > tol || Double.isNaN(diffFdmQdFp)) {
                    fail("failed to reproduce QD+ fixed point values"
                            + "\n    r        : " + r
                            + "\n    q        : " + q
                            + "\n    diff     : " + diffFdmQdFp
                            + "\n    tol      : " + tol);
                }

                double diffFdmQdPlus = Math.abs(fdmNPV - qdPlusNPV);
                if (diffFdmQdPlus > 5 * tol
                        || Double.isNaN(diffFdmQdPlus)) {
                    fail("failed to reproduce QD+ values"
                            + "\n    r        : " + r
                            + "\n    q        : " + q
                            + "\n    diff     : " + diffFdmQdPlus
                            + "\n    tol      : " + 5 * tol);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Carry/negative-carry engines using QdFp as reference
    // ------------------------------------------------------------------

    @Test
    public void testBaroneAdesiWhaleyAndJuWithCarryAboveVolatility() {
        Date today = new Date(15, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);

        record Case(Option.Type type, double strike, double r, double q,
                    double v, int years) {
        }

        Case[] cases = {
                new Case(Option.Type.Call, 100.0, 0.0257, 0.0571, 0.0166, 3),
                new Case(Option.Type.Call, 100.0, 0.0257, 0.0571, 0.0100, 3),
                new Case(Option.Type.Put, 100.0, 0.0647, 0.0234, 0.0089, 3),
                new Case(Option.Type.Put, 120.0, 0.1077, 0.0329, 0.0149, 10)
        };

        double bawTolerance = 5.0e-2;
        double juTolerance = 1.0e-2;

        for (Case c : cases) {
            BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(flatRate(today, c.q(), dc), true),
                            new Handle<>(flatRate(today, c.r(), dc), true),
                            new Handle<>(flatVol(today, c.v(), dc), true));

            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(c.type(), c.strike());
            AmericanExercise exercise = new AmericanExercise(today,
                    today.add(new Period(c.years(), TimeUnit.YEARS)));

            EuropeanOption reference = new EuropeanOption(payoff, exercise);
            reference.setPricingEngine(new QdFpAmericanEngine(
                    process, QdFpAmericanEngine.highPrecisionScheme()));
            double expected = reference.NPV();

            jquant.PricingEngine[] engines = {
                    new BaroneAdesiWhaleyApproximationEngine(process),
                    new JuQuadraticApproximationEngine(process)
            };
            double[] tolerances = {bawTolerance, juTolerance};

            for (int i = 0; i < engines.length; ++i) {
                EuropeanOption option =
                        new EuropeanOption(payoff, exercise);
                option.setPricingEngine(engines[i]);

                double calculated = assertDoesNotThrow(option::NPV);
                double tolerance = tolerances[i];

                double error = Math.abs(calculated - expected);
                if (!(error <= tolerance * expected)) {
                    fail("value"
                            + "\n    option type:      " + c.type()
                            + "\n    spot value:       " + spot.value()
                            + "\n    strike:           " + c.strike()
                            + "\n    dividend yield:   " + c.q()
                            + "\n    risk-free rate:   " + c.r()
                            + "\n    reference date:   " + today
                            + "\n    maturity:         " + exercise.lastDate()
                            + "\n    volatility:       " + c.v()
                            + "\n    expected:         " + expected
                            + "\n    calculated:       " + calculated
                            + "\n    error:            " + error
                            + "\n    tolerance:        "
                            + tolerance * expected);
                }
            }
        }
    }

    @Test
    public void testBjerksundStenslandNegativeCarryTrigger() {
        Date today = new Date(15, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);

        record Case(Option.Type type, double r, double q, double v,
                    int[] days) {
        }

        Case[] cases = {
                new Case(Option.Type.Put, 0.40, 0.05, 0.15,
                        new int[]{120, 182, 220, 250, 268, 365}),
                new Case(Option.Type.Call, 0.05, 0.40, 0.15,
                        new int[]{120, 182, 220, 250, 268, 365}),
                new Case(Option.Type.Put, 0.0768, 0.0075, 0.0231,
                        new int[]{91, 120, 140, 155, 160, 182})
        };

        double tolerance = 2.0e-3;

        for (Case c : cases) {
            BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(flatRate(today, c.q(), dc), true),
                            new Handle<>(flatRate(today, c.r(), dc), true),
                            new Handle<>(flatVol(today, c.v(), dc), true));
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(c.type(), 100.0);
            BjerksundStenslandApproximationEngine bjerksundStensland =
                    new BjerksundStenslandApproximationEngine(process);
            QdFpAmericanEngine qdFp = new QdFpAmericanEngine(
                    process, QdFpAmericanEngine.highPrecisionScheme());

            double previous = 0.0;
            for (int d : c.days()) {
                AmericanExercise exercise = new AmericanExercise(today,
                        today.add(new Period(d, TimeUnit.DAYS)));
                EuropeanOption option = new EuropeanOption(payoff, exercise);

                option.setPricingEngine(qdFp);
                double expected = option.NPV();

                option.setPricingEngine(bjerksundStensland);
                double calculated = option.NPV();

                double error = Math.abs(calculated - expected) / expected;
                if (error > tolerance) {
                    fail("value"
                            + "\n    option type:      " + c.type()
                            + "\n    spot value:       " + spot.value()
                            + "\n    strike:           100.0"
                            + "\n    dividend yield:   " + c.q()
                            + "\n    risk-free rate:   " + c.r()
                            + "\n    reference date:   " + today
                            + "\n    maturity:         " + exercise.lastDate()
                            + "\n    volatility:       " + c.v()
                            + "\n    expected:         " + expected
                            + "\n    calculated:       " + calculated
                            + "\n    error:            " + error
                            + "\n    tolerance:        " + tolerance);
                }
                if (calculated < previous) {
                    fail("Bjerksund-Stensland value decreases with maturity:"
                            + "\n    previous maturity: " + previous
                            + "\n    maturity of " + d
                            + " days: " + calculated);
                }
                previous = calculated;
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void checkSmall(double value, double tolerance,
                                   String name) {
        if (!(Math.abs(value) < tolerance)) {
            fail(name + ": value=" + value + " tolerance=" + tolerance);
        }
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc,
                Continuous, Frequency.ANNUAL);
    }

    private static YieldTermStructure flatRate(Date today, double rate,
                                               DayCounter dc) {
        return flatRate(today, new SimpleQuote(rate), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(vol, true), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, double vol,
                                                 DayCounter dc) {
        return flatVol(today, new SimpleQuote(vol), dc);
    }
}
