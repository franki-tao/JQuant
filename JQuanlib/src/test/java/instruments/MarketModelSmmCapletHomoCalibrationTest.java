package instruments;

import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.Matrix;
import jquant.math.ReferencePkg;
import jquant.math.optimization.SphereCylinderOptimizer;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.correlations.CotSwapFromFwdCorrelation;
import jquant.models.marketmodels.correlations.ExponentialForwardCorrelation;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.models.CapletCoterminalMaxHomogeneityCalibration;
import jquant.models.marketmodels.models.CapletCoterminalPeriodicCalibration;
import jquant.models.marketmodels.models.CotSwapToFwdAdapter;
import jquant.models.marketmodels.models.FwdPeriodAdapter;
import jquant.models.marketmodels.models.FwdToCotSwapAdapter;
import jquant.models.marketmodels.models.PiecewiseConstantAbcdVariance;
import jquant.models.marketmodels.models.PiecewiseConstantVariance;
import jquant.models.marketmodels.models.PseudoRootFacade;
import jquant.models.marketmodels.models.VolatilityInterpolationSpecifier;
import jquant.models.marketmodels.models.VolatilityInterpolationSpecifierAbcd;
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
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/marketmodel_smmcaplethomocalibration.cpp}
 * port: {@code testFunction}, {@code testPeriodFunction} and
 * {@code testSphereCylinder}.
 */
public class MarketModelSmmCapletHomoCalibrationTest {

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

    // Official BOOST_ERROR records a failure but continues the test case.
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
    void testFunction() {
        System.out.println("Testing max homogeneity caplet calibration "
                + "in a lognormal coterminal swap market model...");

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
        double caplet0Swaption1Priority = 1.0;
        if (printReport_) {
            System.out.println("caplet market vols: " + toStream(capletVols));
            System.out.println("caplet0Swapt1Prior: " + caplet0Swaption1Priority);
        }
        CapletCoterminalMaxHomogeneityCalibration calibrator =
                new CapletCoterminalMaxHomogeneityCalibration(evolution,
                        corr,
                        swapVariances,
                        capletVols,
                        cs,
                        displacement,
                        caplet0Swaption1Priority);
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
        MarketModel flmm = new CotSwapToFwdAdapter(smm);
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

        int period = 2;
        int offset = 0;
        List<Double> adaptedDisplacements = new ArrayList<>();
        MarketModel adapted = new FwdPeriodAdapter(flmm, period, offset,
                adaptedDisplacements);
        // FwdToCotSwapAdapter newSwapMM(adapted);
        // for (Size i=0; i < newSwapMM.numberOfRates(); ++i)
        //      BOOST_TEST_MESSAGE("swap MM time dependent vols: "<< i << std::fixed <<
        //               std::setprecision(6) << Array(newSwapMM.timeDependentVolatility(i)));

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    void testPeriodFunction() {
        System.out.println("Testing max homogeneity periodic caplet calibration "
                + "in a lognormal coterminal swap market model...");

        setup();

        int numberOfRates = todaysForwards.size();
        int period = 2;
        int offset = numberOfRates % period;
        int numberBigRates = numberOfRates / period;

        EvolutionDescription evolution = new EvolutionDescription(rateTimes);

        List<Double> bigRateTimes = filledList(numberBigRates + 1, 0.0);

        for (int i = 0; i <= numberBigRates; ++i)
            bigRateTimes.set(i, rateTimes.get(i * period + offset));

        PiecewiseConstantCorrelation fwdCorr =
                new ExponentialForwardCorrelation(rateTimes,
                        longTermCorrelation,
                        beta);

        LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);

        PiecewiseConstantCorrelation corr =
                new CotSwapFromFwdCorrelation(fwdCorr, cs, displacement);

        List<PiecewiseConstantAbcdVariance> swapVariances = new ArrayList<>();
        for (int i = 0; i < numberBigRates; ++i) {
            swapVariances.add(new PiecewiseConstantAbcdVariance(a, b, c, d,
                    i, bigRateTimes));
        }

        VolatilityInterpolationSpecifierAbcd varianceInterpolator =
                new VolatilityInterpolationSpecifierAbcd(period, offset,
                        swapVariances, // these should be associated with the long rates
                        rateTimes // these should be associated with the shorter rates
                );

        // create calibrator
        double caplet0Swaption1Priority = 1.0;
        if (printReport_) {
            System.out.println("caplet market vols: " + toStream(capletVols));
            System.out.println("caplet0Swapt1Prior: " + caplet0Swaption1Priority);
        }

        // calibrate
        int maxUnperiodicIterations = 10;
        double toleranceUnperiodic = 1e-5; // i.e. 1 bp
        int max1dIterations = 100;
        double tolerance1d = 1e-8;
        int maxPeriodIterations = 30;
        double periodTolerance = 1e-5;

        List<Matrix> swapPseudoRoots = new ArrayList<>();
        ReferencePkg<Double> deformationSize = new ReferencePkg<>(0.0);
        ReferencePkg<Double> totalSwaptionError = new ReferencePkg<>(0.0);
        List<Double> finalScales = new ArrayList<>(); //scalings used for matching
        ReferencePkg<Integer> iterationsDone = new ReferencePkg<>(0); // number of period iterations done
        ReferencePkg<Double> errorImprovement = new ReferencePkg<>(0.0); // improvement in error for last iteration
        ReferencePkg<Matrix> modelSwaptionVolsMatrix = new ReferencePkg<>(null);

        if (printReport_) {
            System.out.println("numberOfFactors:    " + numberOfFactors);
            System.out.println("maxUnperiodicIterations:      " + maxUnperiodicIterations);
            System.out.println("toleranceUnperiodic:    " + rate(toleranceUnperiodic));
            System.out.println("max1dIterations: " + max1dIterations);
            System.out.println("tolerance1d:     " + rate(tolerance1d));
        }

        /* int failures = */ CapletCoterminalPeriodicCalibration.capletSwaptionPeriodicCalibration(
                evolution,
                corr,
                varianceInterpolator,
                capletVols,
                cs,
                displacement,
                caplet0Swaption1Priority,
                numberOfFactors,
                period,
                max1dIterations,
                tolerance1d,
                maxUnperiodicIterations,
                toleranceUnperiodic,
                maxPeriodIterations,
                periodTolerance,
                deformationSize,
                totalSwaptionError, // ?
                swapPseudoRoots, // the thing we really want the pseudo root for each time step
                finalScales, //scalings used for matching
                iterationsDone, // number of period iterations done
                errorImprovement, // improvement in error for last iteration
                modelSwaptionVolsMatrix // the swaption vols calibrated to at each step of the iteration
        );

        MarketModel smm = new PseudoRootFacade(swapPseudoRoots,
                rateTimes,
                cs.coterminalSwapRates(),
                filledList(numberOfRates, displacement));
        MarketModel flmm = new CotSwapToFwdAdapter(smm);
        Matrix capletTotCovariance = flmm.totalCovariance(numberOfRates - 1);

        List<Double> modelCapletVols = filledList(numberOfRates, 0.0);
        for (int i = 0; i < numberOfRates; ++i)
            modelCapletVols.set(i,
                    Math.sqrt(capletTotCovariance.get(i, i) / rateTimes.get(i)));

        double error;
        double capletTolerance = 1e-4; // i.e. 1 bp

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

        List<Double> adaptedDisplacements = filledList(numberBigRates, displacement);
        MarketModel adaptedFlmm = new FwdPeriodAdapter(flmm, period, offset,
                adaptedDisplacements);

        MarketModel adaptedsmm = new FwdToCotSwapAdapter(adaptedFlmm);

        // check perfect swaption fit
        double swapTolerance = 2e-5;

        Matrix swapTerminalCovariance =
                adaptedsmm.totalCovariance(adaptedsmm.numberOfSteps() - 1);

        for (int i = 0; i < numberBigRates; ++i) {
            double expSwaptionVol = swapVariances.get(i).totalVolatility(i);
            // double cov = swapTerminalCovariance[i][i];
            double time = adaptedsmm.evolution().rateTimes().get(i);
            double swaptionVol = Math.sqrt(swapTerminalCovariance.get(i, i) / time);

            error = Math.abs(swaptionVol - expSwaptionVol);
            if (error > swapTolerance)
                boostErrors.add("failed to reproduce " + Utilities.ordinal(i) + " swaption vol:"
                        + "\n expected:  " + rate(expSwaptionVol)
                        + "\n realized:  " + rate(swaptionVol)
                        + "\n error:     " + error
                        + "\n tolerance: " + swapTolerance);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    void testSphereCylinder() {
        System.out.println("Testing sphere-cylinder optimization...");

        {
            double R = 1.0;
            double S = 0.5;
            double alpha = 1.5;
            double Z1 = 1.0 / Math.sqrt(3.0);
            double Z2 = 1.0 / Math.sqrt(3.0);
            double Z3 = 1.0 / Math.sqrt(3.0);

            SphereCylinderOptimizer optimizer =
                    new SphereCylinderOptimizer(R, S, alpha, Z1, Z2, Z3);
            int maxIterations = 100;
            double tolerance = 1e-8;
            ReferencePkg<Double> y1 = new ReferencePkg<>(0.0);
            ReferencePkg<Double> y2 = new ReferencePkg<>(0.0);
            ReferencePkg<Double> y3 = new ReferencePkg<>(0.0);

            optimizer.findClosest(maxIterations, tolerance, y1, y2, y3);

            double errorTol = 1e-12;
            if (Math.abs(y1.getT() - 1.0) > errorTol)
                boostErrors.add("\n failed to reproduce y1=1: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            if (Math.abs(y2.getT() - 0.0) > errorTol)
                boostErrors.add("\n failed to reproduce y2=0: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            if (Math.abs(y3.getT() - 0.0) > errorTol)
                boostErrors.add("\n failed to reproduce y3=0: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            optimizer.findByProjection(y1, y2, y3);

            if (Math.abs(y1.getT() - 1.0) > errorTol)
                boostErrors.add("\nfindByProjection failed to reproduce y1=1: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            if (Math.abs(y2.getT() - 0.0) > errorTol)
                boostErrors.add("\n findByProjection failed to reproduce y2=0: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            if (Math.abs(y3.getT() - 0.0) > errorTol)
                boostErrors.add("\n findByProjection failed to reproduce y3=0: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());
        }

        {
            double R = 5.0;
            double S = 1.0;
            double alpha = 1.0;
            double Z1 = 1.0;
            double Z2 = 2.0;
            double Z3 = Math.sqrt(20.0);

            SphereCylinderOptimizer optimizer =
                    new SphereCylinderOptimizer(R, S, alpha, Z1, Z2, Z3);
            int maxIterations = 100;
            double tolerance = 1e-8;
            ReferencePkg<Double> y1 = new ReferencePkg<>(0.0);
            ReferencePkg<Double> y2 = new ReferencePkg<>(0.0);
            ReferencePkg<Double> y3 = new ReferencePkg<>(0.0);

            optimizer.findClosest(maxIterations, tolerance, y1, y2, y3);

            double errorTol = 1e-4;
            if (Math.abs(y1.getT() - 1.03306) > errorTol)
                boostErrors.add("\n failed to reproduce y1=1.03306: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            if (Math.abs(y2.getT() - 0.999453) > errorTol)
                boostErrors.add("\n failed to reproduce y2=0.999453: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            if (Math.abs(y3.getT() - 4.78893) > errorTol)
                boostErrors.add("\n failed to reproduce y3=4.78893: "
                        + y1.getT() + ", " + y2.getT() + ", " + y3.getT());

            optimizer.findByProjection(y1, y2, y3);

            if (Math.abs(y1.getT() - 1.0) > errorTol)
                boostErrors.add("\n findByProjection failed to reproduce y1 =1: "
                        + y1.getT() + " " + y2.getT() + " " + y3.getT());

            if (Math.abs(y2.getT() - 1.0) > errorTol)
                boostErrors.add("\n findByProjection failed to reproduce y2 =1: "
                        + y1.getT() + " " + y2.getT() + " " + y3.getT());

            if (Math.abs(y3.getT() - Math.sqrt(23.0)) > errorTol)
                boostErrors.add("\n findByProjection failed to reproduce y3 =sqrt(23): "
                        + y1.getT() + " " + y2.getT() + " " + y3.getT());
        }

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
