package instruments;

import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.Matrix;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.correlations.CotSwapFromFwdCorrelation;
import jquant.models.marketmodels.correlations.ExponentialForwardCorrelation;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.models.CapletCoterminalSwaptionCalibration;
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
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/marketmodel_smmcapletcalibration.cpp}
 * port (single case {@code testFunction}, GHLS calibration).
 */
public class MarketModelSmmCapletCalibrationTest {

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
        alpha = -0.05;
        alphaMax = 1.0;
        alphaMin = -1.0;
        displacement = 0.0;
        for (int i = 0; i < todaysForwards.size(); ++i) {
            todaysForwards.set(i, 0.03 + 0.0025 * i);
            //todaysForwards_[i] = 0.03;
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
        //Volatility mktSwaptionVols[] = { ... };
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

        // #ifdef _DEBUG
        // paths = 127;
        // trainingPaths = 31;
        // #else
        paths = 32767; //262144-1; //; // 2^15-1
        trainingPaths = 8191; // 2^13-1
        // #endif
    }

    @Test
    void testFunction() {
        System.out.println("Testing GHLS caplet calibration "
                + "in a lognormal coterminal swap market model...");

        setup();

        int numberOfRates = todaysForwards.size();

        EvolutionDescription evolution = new EvolutionDescription(rateTimes);

        PiecewiseConstantCorrelation fwdCorr =
                new ExponentialForwardCorrelation(rateTimes,
                        longTermCorrelation, beta);

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
        List<Double> alphaVector = filledList(numberOfRates, alpha);
        boolean lowestRoot = true;
        boolean useFullApprox = false;
        if (printReport_) {
            System.out.println("caplet market vols: " + toStream(capletVols));
            System.out.println("alpha:              " + alpha);
            System.out.println("lowestRoot:         " + lowestRoot);
            System.out.println("useFullApprox:      " + useFullApprox);
        }
        CapletCoterminalSwaptionCalibration calibrator =
                new CapletCoterminalSwaptionCalibration(evolution,
                        corr,
                        swapVariances,
                        capletVols,
                        cs,
                        displacement,
                        alphaVector,
                        lowestRoot,
                        useFullApprox);
        // calibrate
        int maxIterations = 2;
        double capletTolerance = 0.0001;
        int innerMaxIterations = 50;
        double innerTolerance = 1e-9;
        if (printReport_) {
            System.out.println("alpha:              " + alpha);
            System.out.println("lowestRoot:         " + lowestRoot);
            System.out.println("useFullApprox:      " + useFullApprox);
        }
        boolean result = calibrator.calibrate(numberOfFactors,
                maxIterations,
                capletTolerance / 10,
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
