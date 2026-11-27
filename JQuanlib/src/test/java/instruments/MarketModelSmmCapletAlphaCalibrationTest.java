package instruments;

import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.Matrix;
import jquant.math.ReferencePkg;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.correlations.CotSwapFromFwdCorrelation;
import jquant.models.marketmodels.correlations.ExponentialForwardCorrelation;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.models.AlphaFinder;
import jquant.models.marketmodels.models.AlphaForm;
import jquant.models.marketmodels.models.AlphaFormInverseLinear;
import jquant.models.marketmodels.models.CapletCoterminalAlphaCalibration;
import jquant.models.marketmodels.models.CotSwapToFwdAdapter;
import jquant.models.marketmodels.models.PiecewiseConstantAbcdVariance;
import jquant.models.marketmodels.models.PiecewiseConstantVariance;
import jquant.models.marketmodels.models.PseudoRootFacade;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.SimpleDayCounter;
import jquant.time.Schedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib
 * {@code test-suite/marketmodel_smmcapletalphacalibration.cpp} port.
 *
 * <p>The seven {@code testAlphaFinder*} cases exercise {@link AlphaFinder}
 * directly; {@code testFunction} and {@code testMaximumHomogeneityCalibration}
 * exercise the {@link CapletCoterminalAlphaCalibration} wrapper. The official
 * file-local {@code BoundedLinearAlphaForm} / {@code InteriorTestAlphaForm}
 * test doubles are reproduced here rather than in production.
 */
public class MarketModelSmmCapletAlphaCalibrationTest {

    // Official file-local test doubles (marketmodel_smmcapletalphacalibration.cpp
    // anonymous namespace).
    private static class BoundedLinearAlphaForm implements AlphaForm {
        private final double offset_, slope_;
        private double alpha_ = 0.0;
        private int evaluations_ = 0;

        BoundedLinearAlphaForm(double offset, double slope) {
            offset_ = offset;
            slope_ = slope;
        }

        @Override
        public double value(int index) {
            return index == 0 ? 1.0 : offset_ + slope_ * alpha_;
        }

        @Override
        public void setAlpha(double alpha) {
            QL_REQUIRE(++evaluations_ <= 10000, "alpha evaluation limit exceeded");
            alpha_ = alpha;
        }
    }

    private static class InteriorTestAlphaForm implements AlphaForm {
        private final double center_;
        private double alpha_ = 0.0;

        InteriorTestAlphaForm(double center) {
            center_ = center;
        }

        @Override
        public double value(int index) {
            double distance = alpha_ - center_;
            return index == 0 ? 1.0 : 0.2 + 4.0 * distance * distance;
        }

        @Override
        public void setAlpha(double alpha) {
            alpha_ = alpha;
        }
    }

    private SavedSettings savedSettings;

    private Date todaysDate, startDate, endDate;
    private List<Double> rateTimes;
    private List<Double> accruals;
    private Calendar calendar;
    private DayCounter dayCounter;
    private List<Double> todaysForwards, todaysSwaps;
    private List<Double> coterminalAnnuity;
    private int numberOfFactors;
    private double alpha, alphaMax, alphaMin;
    private double displacement;
    private List<Double> todaysDiscounts;
    private List<Double> swaptionDisplacedVols, swaptionVols;
    private List<Double> capletDisplacedVols, capletVols;
    private double a, b, c, d;
    private double longTermCorrelation, beta;
    private int measureOffset;
    private long seed;
    private int paths, trainingPaths;
    private boolean printReport_ = false;

    // Official BOOST_ERROR / BOOST_CHECK records a failure but continues the
    // test case; the official messages are accumulated here.
    private final List<String> boostErrors = new ArrayList<>();

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private void setup() {
        // Times
        calendar = new NullCalendar();
        todaysDate = Settings.instance.evaluationDate().Date();
        //startDate = todaysDate + 5*Years;
        endDate = todaysDate.add(new Period(66, TimeUnit.MONTHS));
        Schedule dates = new Schedule(todaysDate, endDate,
                new Period(Frequency.SEMIANNUAL),
                calendar, BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        rateTimes = filledList(dates.size() - 1, 0.0);
        accruals = filledList(rateTimes.size() - 1, 0.0);
        dayCounter = new SimpleDayCounter();
        for (int i = 1; i < dates.size(); ++i)
            rateTimes.set(i - 1, dayCounter.yearFraction(todaysDate, dates.dates().get(i)));
        for (int i = 1; i < rateTimes.size(); ++i)
            accruals.set(i - 1, rateTimes.get(i) - rateTimes.get(i - 1));

        // Rates & displacement
        todaysForwards = filledList(accruals.size(), 0.0);
        numberOfFactors = 3;
        alpha = 0.0;
        alphaMax = 1.0;
        alphaMin = -1.0;
        displacement = 0.0;
        for (int i = 0; i < todaysForwards.size(); ++i) {
            todaysForwards.set(i, 0.03 + 0.0025 * i);
            //    todaysForwards_[i] = 0.03;
        }
        LMMCurveState curveState_lmm = new LMMCurveState(rateTimes);
        curveState_lmm.setOnForwardRates(todaysForwards);
        todaysSwaps = new ArrayList<>(curveState_lmm.coterminalSwapRates());

        // Discounts
        todaysDiscounts = filledList(rateTimes.size(), 0.0);
        todaysDiscounts.set(0, 0.95);
        for (int i = 1; i < rateTimes.size(); ++i)
            todaysDiscounts.set(i, todaysDiscounts.get(i - 1)
                    / (1.0 + todaysForwards.get(i - 1) * accruals.get(i - 1)));

        //// Swaption Volatilities
        //Volatility mktSwaptionVols[] = {
        //                        0.15541283,
        //                        0.18719678,
        //                        0.20890740,
        //                        0.22318179,
        //                        0.23212717,
        //                        0.23731450,
        //                        0.23988649,
        //                        0.24066384,
        //                        0.24023111,
        //                        0.23900189,
        //                        0.23726699,
        //                        0.23522952,
        //                        0.23303022,
        //                        0.23076564,
        //                        0.22850101,
        //                        0.22627951,
        //                        0.22412881,
        //                        0.22206569,
        //                        0.22009939
        //};

        //a = -0.0597;
        //b =  0.1677;
        //c =  0.5403;
        //d =  0.1710;

        a = 0.0;
        b = 0.17;
        c = 1.0;
        d = 0.10;

        double[] mktCapletVols = {
                0.1640,
                0.1740,
                0.1840,
                0.1940,
                0.1840,
                0.1740,
                0.1640,
                0.1540,
                0.1440,
                0.1340376439125532
        };

        //swaptionDisplacedVols = std::vector<Volatility>(todaysSwaps.size());
        //swaptionVols = std::vector<Volatility>(todaysSwaps.size());
        //capletDisplacedVols = std::vector<Volatility>(todaysSwaps.size());
        capletVols = filledList(todaysSwaps.size(), 0.0);
        for (int i = 0; i < todaysSwaps.size(); i++) {
            //    swaptionDisplacedVols[i] = todaysSwaps[i]*mktSwaptionVols[i]/
            //                              (todaysSwaps[i]+displacement);
            //    swaptionVols[i]= mktSwaptionVols[i];
            //    capletDisplacedVols[i] = todaysForwards[i]*mktCapletVols[i]/
            //                            (todaysForwards[i]+displacement);
            capletVols.set(i, mktCapletVols[i]);
        }

        // Cap/Floor Correlation
        longTermCorrelation = 0.5;
        beta = 0.2;
        measureOffset = 5;

        // Monte Carlo
        seed = 42;

        //#ifdef _DEBUG
        //    paths_ = 127;
        //    trainingPaths_ = 31;
        //#else
        paths = 32767; //262144-1; //; // 2^15-1
        trainingPaths = 8191; // 2^13-1
        //#endif
    }

    @Test
    void testAlphaFinderExactBisectionRoot() {
        System.out.println("Testing AlphaFinder exact bisection roots...");
        for (boolean increasing : new boolean[]{false, true}) {
            BoundedLinearAlphaForm form = new BoundedLinearAlphaForm(
                    increasing ? 0.0 : 1.0,
                    increasing ? 1.0 : -1.0);
            AlphaFinder finder = new AlphaFinder(form);
            List<Double> calibratedVols = filledList(3, 0.0);
            ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
            ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
            ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
            boolean success = finder.solve(
                    increasing ? 1.0 : 0.0, 1,
                    Arrays.asList(1.0, 0.0),
                    Arrays.asList(1.0, 1.0, 1.0),
                    Arrays.asList(-1.0, 0.0),
                    1.0, 1.0, 1.0 - 0.8, 1.0e-12, 1.0, 0.0, 100,
                    alpha, scale, lastScale, calibratedVols);
            assertTrue(success);
            checkSmall(alpha.getT() - 0.5, 1.0e-12);
            checkSmall(calibratedVols.get(0) - 0.8, 1.0e-12);
            checkSmall(calibratedVols.get(1) - 0.4, 1.0e-12);
            checkSmall(calibratedVols.get(2) - Math.sqrt(2.2), 1.0e-12);
        }
        failIfErrors();
    }

    @Test
    void testAlphaFinderBisectionStagnation() {
        System.out.println("Testing AlphaFinder bisection stagnation...");
        for (boolean increasing : new boolean[]{false, true}) {
            BoundedLinearAlphaForm form = new BoundedLinearAlphaForm(
                    increasing ? 0.0 : 1.0,
                    increasing ? 1.0 : -1.0);
            AlphaFinder finder = new AlphaFinder(form);
            List<Double> calibratedVols = filledList(3, 0.0);
            ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
            ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
            ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
            boolean success = finder.solve(
                    increasing ? 1.0 : 0.0, 1,
                    Arrays.asList(1.0, 0.0),
                    Arrays.asList(1.0, 1.0, 1.0),
                    Arrays.asList(-1.0, 0.0),
                    1.0, 1.0, 0.3, 1.0e-20, 1.0, 0.0, 100,
                    alpha, scale, lastScale, calibratedVols);
            assertTrue(success);
            double expectedAlpha = increasing
                    ? Math.sqrt(3.0 / 7.0)
                    : 1.0 - Math.sqrt(3.0 / 7.0);
            checkSmall(alpha.getT() - expectedAlpha, 1.0e-12);
            double targetVariance = (1.0 - calibratedVols.get(0))
                    * (1.0 - calibratedVols.get(0))
                    + calibratedVols.get(1) * calibratedVols.get(1);
            double totalVariance = 0.0;
            for (double volatility : calibratedVols) {
                check(Double.isFinite(volatility), "volatility is not finite");
                totalVariance += volatility * volatility;
            }
            checkSmall(targetVariance - 0.3, 1.0e-12);
            checkSmall(totalVariance - 3.0, 1.0e-12);
        }
        failIfErrors();
    }

    @Test
    void testAlphaFinderMaximumHomogeneity() {
        System.out.println("Testing AlphaFinder maximum homogeneity...");
        boolean[] increasing = {false, true, true, false, true, true, false};
        double[] initial = {-1.0, -1.0, -1.0, -1.4, -0.6, -0.75, -1.25};
        double[] minimum = {-1.5, -1.5, -1.2, -1.5, -1.5, -1.0, -1.5};
        double[] maximum = {-0.5, -0.5, -0.8, -0.5, -0.5, -0.5, -1.0};
        for (int c = 0; c < increasing.length; ++c) {
            BoundedLinearAlphaForm form = new BoundedLinearAlphaForm(
                    increasing[c] ? 2.0 : 0.0,
                    increasing[c] ? 1.0 : -1.0);
            AlphaFinder finder = new AlphaFinder(form);
            List<Double> calibratedVols = filledList(3, 0.0);
            ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
            ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
            ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
            boolean success = finder.solveWithMaxHomogeneity(
                    initial[c], 1,
                    Arrays.asList(2.0, 1.0),
                    Arrays.asList(1.0, 1.0, 1.0),
                    Arrays.asList(1.0, 1.0),
                    1.0, -1.0, 1.0, 1.0e-10,
                    maximum[c], minimum[c], 100,
                    alpha, scale, lastScale, calibratedVols);
            assertTrue(success);
            checkGe(alpha.getT(), minimum[c]);
            checkLe(alpha.getT(), maximum[c]);
            checkSmall(alpha.getT() + 1.0, 1.0e-6);
            checkSmall(scale.getT() - 1.0, 1.0e-6);
            checkSmall(lastScale.getT() - 1.0, 1.0e-6);
            double totalVariance = 0.0;
            for (double volatility : calibratedVols) {
                checkSmall(volatility - 1.0, 1.0e-6);
                totalVariance += volatility * volatility;
            }
            double targetVariance = (2.0 - calibratedVols.get(0))
                    * (2.0 - calibratedVols.get(0))
                    + (1.0 - calibratedVols.get(1)) * (1.0 - calibratedVols.get(1));
            checkSmall(targetVariance - 1.0, 1.0e-10);
            checkSmall(totalVariance - 3.0, 1.0e-10);
        }
        failIfErrors();
    }

    @Test
    void testAlphaFinderDownwardInteriorSearch() {
        System.out.println("Testing AlphaFinder downward interior search...");
        InteriorTestAlphaForm form = new InteriorTestAlphaForm(-0.5);
        AlphaFinder finder = new AlphaFinder(form);
        List<Double> calibratedVols = filledList(3, 0.0);
        ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
        ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
        ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
        boolean success = finder.solve(
                0.0, 1,
                Arrays.asList(1.0, 0.0),
                Arrays.asList(1.0, 1.0, 1.0),
                Arrays.asList(-1.0, 0.0),
                1.0, 1.0, 0.3, 1.0e-10, 1.0, -1.0, 100,
                alpha, scale, lastScale, calibratedVols);
        assertTrue(success);
        double expectedAlpha = -0.5 + 0.5 * Math.sqrt(Math.sqrt(3.0 / 7.0) - 0.2);
        checkSmall(alpha.getT() - expectedAlpha, 1.0e-8);
        double totalVariance = 0.0;
        for (double volatility : calibratedVols) {
            check(Double.isFinite(volatility), "volatility is not finite");
            totalVariance += volatility * volatility;
        }
        double targetVariance = (1.0 - calibratedVols.get(0))
                * (1.0 - calibratedVols.get(0))
                + calibratedVols.get(1) * calibratedVols.get(1);
        checkSmall(totalVariance - 3.0, 1.0e-8);
        checkSmall(targetVariance - 0.3, 1.0e-8);
        failIfErrors();
    }

    @Test
    void testAlphaFinderInteriorSearch() {
        System.out.println("Testing AlphaFinder interior search...");
        double[] centers = {0.5, -0.5, 0.5};
        boolean[] maximizeHomogeneity = {false, true, true};
        for (int c = 0; c < centers.length; ++c) {
            InteriorTestAlphaForm form = new InteriorTestAlphaForm(centers[c]);
            AlphaFinder finder = new AlphaFinder(form);
            List<Double> calibratedVols = filledList(3, 0.0);
            ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
            ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
            ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
            boolean success;
            if (maximizeHomogeneity[c]) {
                success = finder.solveWithMaxHomogeneity(
                        0.0, 1,
                        Arrays.asList(1.0, 0.0),
                        Arrays.asList(1.0, 1.0, 1.0),
                        Arrays.asList(-1.0, 0.0),
                        1.0, 1.0, 0.3, 1.0e-10, 1.0, -1.0, 100,
                        alpha, scale, lastScale, calibratedVols);
            } else {
                success = finder.solve(
                        0.0, 1,
                        Arrays.asList(1.0, 0.0),
                        Arrays.asList(1.0, 1.0, 1.0),
                        Arrays.asList(-1.0, 0.0),
                        1.0, 1.0, 0.3, 1.0e-10, 1.0, -1.0, 100,
                        alpha, scale, lastScale, calibratedVols);
            }
            assertTrue(success);
            double halfWidth = 0.5 * Math.sqrt(Math.sqrt(3.0 / 7.0) - 0.2);
            checkGe(alpha.getT(), centers[c] - halfWidth - 1.0e-8);
            checkLe(alpha.getT(), centers[c] + halfWidth + 1.0e-8);
            if (!maximizeHomogeneity[c])
                checkSmall(alpha.getT() - (centers[c] - halfWidth), 1.0e-8);
            check(Double.isFinite(scale.getT()), "scale is not finite");
            check(Double.isFinite(lastScale.getT()), "lastScale is not finite");
            double totalVariance = 0.0;
            for (double volatility : calibratedVols) {
                check(Double.isFinite(volatility), "volatility is not finite");
                totalVariance += volatility * volatility;
            }
            double targetVariance = (1.0 - calibratedVols.get(0))
                    * (1.0 - calibratedVols.get(0))
                    + calibratedVols.get(1) * calibratedVols.get(1);
            checkSmall(totalVariance - 3.0, 1.0e-8);
            checkSmall(targetVariance - 0.3, 1.0e-8);
        }
        failIfErrors();
    }

    @Test
    void testAlphaFinderRejectsNegativeRemainingVariance() {
        System.out.println("Testing AlphaFinder rejection of negative remaining variance...");
        AlphaFormInverseLinear form =
                new AlphaFormInverseLinear(Arrays.asList(1.0, 2.0));
        AlphaFinder finder = new AlphaFinder(form);
        List<Double> firstVols = Arrays.asList(1.0);
        List<Double> homogeneousVols = Arrays.asList(0.1, 0.1);
        List<Double> correlations = Arrays.asList(-1.0);
        List<Double> calibratedVols = filledList(2, 0.0);
        ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
        ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
        ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
        check(!finder.solve(0.0, 0, firstVols, homogeneousVols, correlations,
                        1.0, 1.0, 0.25, 1.0e-10, 1.0, -0.5, 100,
                        alpha, scale, lastScale, calibratedVols),
                "solve should reject negative remaining variance");
        check(!finder.solveWithMaxHomogeneity(
                        0.0, 0, firstVols, homogeneousVols, correlations,
                        1.0, 1.0, 0.25, 1.0e-10, 1.0, -0.5, 100,
                        alpha, scale, lastScale, calibratedVols),
                "solveWithMaxHomogeneity should reject negative remaining variance");
        failIfErrors();
    }

    @Test
    void testAlphaFinderNoRealSolution() {
        System.out.println("Testing AlphaFinder when no real solution exists...");
        AlphaFormInverseLinear form =
                new AlphaFormInverseLinear(Arrays.asList(1.0, 2.0));
        AlphaFinder finder = new AlphaFinder(form);
        List<Double> calibratedVols = filledList(2, 0.0);
        ReferencePkg<Double> alpha = new ReferencePkg<>(0.0);
        ReferencePkg<Double> scale = new ReferencePkg<>(0.0);
        ReferencePkg<Double> lastScale = new ReferencePkg<>(0.0);
        check(!finder.solve(0.0, 0,
                        Arrays.asList(1.0),
                        Arrays.asList(1.0, 1.0),
                        Arrays.asList(0.0),
                        1.0, 1.0, 0.5, 1.0e-10, 1.0, -0.5, 100,
                        alpha, scale, lastScale, calibratedVols),
                "solve should fail when no real solution exists");
        check(!finder.solveWithMaxHomogeneity(
                        0.0, 0,
                        Arrays.asList(1.0),
                        Arrays.asList(1.0, 1.0),
                        Arrays.asList(0.0),
                        1.0, 1.0, 0.5, 1.0e-10, 1.0, -0.5, 100,
                        alpha, scale, lastScale, calibratedVols),
                "solveWithMaxHomogeneity should fail when no real solution exists");
        failIfErrors();
    }

    @Test
    void testFunction() {
        System.out.println("Testing LIBOR market-model alpha calibration...");

        testAlphaCalibration(false);
    }

    @Test
    void testMaximumHomogeneityCalibration() {
        System.out.println("Testing maximum-homogeneity LIBOR market-model alpha calibration...");

        testAlphaCalibration(true);
    }

    private void testAlphaCalibration(boolean maximizeHomogeneity) {
        setup();

        int numberOfRates = todaysForwards.size();

        EvolutionDescription evolution = new EvolutionDescription(rateTimes);
        // int numberOfSteps = evolution.numberOfSteps();

        PiecewiseConstantCorrelation fwdCorr =
                new ExponentialForwardCorrelation(rateTimes,
                        longTermCorrelation,
                        beta);

        LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);

        PiecewiseConstantCorrelation corr =
                new CotSwapFromFwdCorrelation(fwdCorr, cs, displacement);

        List<PiecewiseConstantVariance> swapVariances = new ArrayList<>();
        for (int i = 0; i < numberOfRates; ++i) {
            swapVariances.add(new PiecewiseConstantAbcdVariance(a, b, c, d,
                    i, rateTimes));
        }

        // create calibrator
        List<Double> alphaInitial = filledList(numberOfRates, alpha);
        List<Double> alphaMaxVector = filledList(numberOfRates, 1.0);
        List<Double> alphaMinVector = filledList(numberOfRates, -1.0);
        if (printReport_) {
            System.out.println("caplet market vols: " + toStream(capletVols));
            System.out.println("alphaMin:           " + alphaMin);
            System.out.println("alphaInitial:       " + alpha);
            System.out.println("alphaMax:           " + alphaMax);
            System.out.println("maximizeHomogeneity:" + maximizeHomogeneity);
        }
        CapletCoterminalAlphaCalibration calibrator =
                new CapletCoterminalAlphaCalibration(evolution,
                        corr,
                        swapVariances,
                        capletVols,
                        cs,
                        displacement,
                        alphaInitial,
                        alphaMaxVector,
                        alphaMinVector,
                        maximizeHomogeneity);
        // calibrate
        int maxIterations = 10;
        double capletTolerance = 1e-4; // i.e. 1 bp
        int innerMaxIterations = 100;
        double innerTolerance = 1e-8;

        if (printReport_) {
            System.out.println("numberOfFactors:    " + numberOfFactors);
            System.out.println("maxIterations:      " + maxIterations);
            System.out.println("capletTolerance:    " + rate(capletTolerance));
            System.out.println("innerMaxIterations: " + innerMaxIterations);
            System.out.println("innerTolerance:     " + rate(innerTolerance));
        }

        boolean result = calibrator.calibrate(numberOfFactors,
                maxIterations,
                capletTolerance,
                innerMaxIterations,
                innerTolerance);
        if (!result)
            boostErrors.add("calibration failed");

        List<Matrix> swapPseudoRoots = calibrator.swapPseudoRoots();
        MarketModel smm = new PseudoRootFacade(swapPseudoRoots,
                rateTimes,
                cs.coterminalSwapRates(),
                filledList(numberOfRates, displacement));
        CotSwapToFwdAdapter flmm = new CotSwapToFwdAdapter(smm);
        Matrix capletTotCovariance = flmm.totalCovariance(numberOfRates - 1);

        List<Double> modelCapletVols = filledList(numberOfRates, 0.0);
        for (int i = 0; i < numberOfRates; ++i)
            modelCapletVols.set(i,
                    Math.sqrt(capletTotCovariance.get(i, i) / rateTimes.get(i)));
        if (printReport_) {
            System.out.println("caplet smm implied vols: " + toStream(modelCapletVols));
            System.out.println("failures: " + calibrator.failures());
            System.out.println("deformationSize: " + calibrator.deformationSize());
            System.out.println("capletRmsError: " + calibrator.capletRmsError());
            System.out.println("capletMaxError: " + calibrator.capletMaxError());
            System.out.println("swaptionRmsError: " + calibrator.swaptionRmsError());
            System.out.println("swaptionMaxError: " + calibrator.swaptionMaxError());
        }

        // check perfect swaption fit
        double error;
        double swapTolerance = 1e-14;
        Matrix swapTerminalCovariance = new Matrix(numberOfRates, numberOfRates, 0.0);
        for (int i = 0; i < numberOfRates; ++i) {
            double expSwaptionVol = swapVariances.get(i).totalVolatility(i);
            swapTerminalCovariance = swapTerminalCovariance.add(
                    swapPseudoRoots.get(i).multipy(swapPseudoRoots.get(i).transpose()));
            double swaptionVol = Math.sqrt(
                    swapTerminalCovariance.get(i, i) / rateTimes.get(i));
            error = Math.abs(swaptionVol - expSwaptionVol);
            if (error > swapTolerance)
                boostErrors.add("failed to reproduce " + Utilities.ordinal(i + 1) + " swaption vol:"
                        + "\n expected:  " + rate(expSwaptionVol)
                        + "\n realized:  " + rate(swaptionVol)
                        + "\n error:     " + error
                        + "\n tolerance: " + swapTolerance);
        }

        // check caplet fit
        for (int i = 0; i < numberOfRates; ++i) {
            error = Math.abs(modelCapletVols.get(i) - capletVols.get(i));
            if (error > capletTolerance)
                boostErrors.add("failed to reproduce " + Utilities.ordinal(i + 1) + " caplet vol:"
                        + "\n expected:         " + rate(capletVols.get(i))
                        + "\n realized:         " + rate(modelCapletVols.get(i))
                        + "\n percentage error: " + (error / capletVols.get(i))
                        + "\n error:            " + error
                        + "\n tolerance:        " + capletTolerance);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    private void check(boolean condition, String message) {
        if (!condition)
            boostErrors.add(message);
    }

    private void checkGe(double value, double bound) {
        if (!(value >= bound))
            boostErrors.add("value (" + value + ") is less than expected lower bound ("
                    + bound + ")");
    }

    private void checkLe(double value, double bound) {
        if (!(value <= bound))
            boostErrors.add("value (" + value + ") is greater than expected upper bound ("
                    + bound + ")");
    }

    private void checkSmall(double value, double tolerance) {
        if (!(Math.abs(value) <= tolerance))
            boostErrors.add("value (" + value + ") not sufficiently small: |value| > "
                    + tolerance);
    }

    private void failIfErrors() {
        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }

    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.6f %%", value * 100.0);
    }

    private static String toStream(List<Double> values) {
        StringBuilder result = new StringBuilder("{ ");
        for (int i = 0; i < values.size(); ++i) {
            if (i > 0)
                result.append(", ");
            result.append(String.format(Locale.ROOT, "%.4f", values.get(i)));
        }
        return result.append(" }").toString();
    }
}
