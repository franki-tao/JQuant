package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.math.ReferencePkg;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/capletcoterminalperiodic.hpp/.cpp} port.
 *
 * <p>Java container for the official free function
 * {@code capletSwaptionPeriodicCalibration}.
 */
public final class CapletCoterminalPeriodicCalibration {

    private CapletCoterminalPeriodicCalibration() {
    }

    // for the displaced swap
    public static int capletSwaptionPeriodicCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            VolatilityInterpolationSpecifier displacedSwapVariances,
            final List<Double> capletVols,
            final CurveState cs,
            double displacement,
            double caplet0Swaption1Priority,
            int numberOfFactors,
            int period,
            int max1dIterations,
            double tolerance1d,
            int maxUnperiodicIterations,
            double toleranceUnperiodic,
            int maxPeriodIterations,
            double periodTolerance,
            ReferencePkg<Double> deformationSize,
            ReferencePkg<Double> totalSwaptionError,
            List<Matrix> swapCovariancePseudoRoots,
            List<Double> finalScales,
            ReferencePkg<Integer> iterationsDone,
            ReferencePkg<Double> errorImprovement,
            ReferencePkg<Matrix> modelSwaptionVolsMatrix) {

        int numberSmallRates = evolution.numberOfRates();
        int numberSmallSteps = evolution.numberOfSteps();

        QL_REQUIRE(numberSmallSteps == numberSmallRates,
                "periodic calibration class requires evolution to the reset of each rate");

        int numberBigRates = numberSmallRates / period;
        int offset = numberSmallRates % period;

        List<Double> newDisplacements = filledList(numberBigRates, displacement);

        QL_REQUIRE(displacedSwapVariances.getNoBigRates() == numberBigRates,
                "mismatch between number of swap variances given and number of rates and period");

        int failures = 0;

        List<Double> scalingFactors = filledList(numberBigRates, 1.0);

        displacedSwapVariances.setLastCapletVol(capletVols.get(capletVols.size() - 1));

        List<Double> marketSwaptionVols = filledList(numberBigRates, 0.0);
        for (int i = 0; i < numberBigRates; ++i) {
            marketSwaptionVols.set(i,
                    displacedSwapVariances.originalVariances().get(i).totalVolatility(i));
        }

        List<Double> modelSwaptionVols = filledList(numberBigRates, 0.0);

        double periodSwaptionRmsError;

        iterationsDone.setT(0);

        double previousError = 1.0e+10; // very large number

        modelSwaptionVolsMatrix.setT(new Matrix(maxPeriodIterations, numberBigRates, 0.0));

        while (true) {
            displacedSwapVariances.setScalingFactors(scalingFactors);

            CapletCoterminalMaxHomogeneityCalibration unperiodicCalibrator =
                    new CapletCoterminalMaxHomogeneityCalibration(
                            evolution,
                            corr,
                            displacedSwapVariances.interpolatedVariances(),
                            capletVols,
                            cs,
                            displacement,
                            caplet0Swaption1Priority);

            // Official assigns the bool result of calibrate() to Integer failures.
            failures = unperiodicCalibrator.calibrate(numberOfFactors, maxUnperiodicIterations,
                    toleranceUnperiodic, max1dIterations, tolerance1d) ? 1 : 0;

            swapCovariancePseudoRoots.clear();
            for (Matrix root : unperiodicCalibrator.swapPseudoRoots())
                swapCovariancePseudoRoots.add(copyMatrix(root));

            MarketModel smm = new PseudoRootFacade(swapCovariancePseudoRoots,
                    evolution.rateTimes(), cs.coterminalSwapRates(),
                    filledList(evolution.numberOfRates(), displacement));

            MarketModel flmm = new CotSwapToFwdAdapter(smm);

            Matrix capletTotCovariance = flmm.totalCovariance(numberSmallRates - 1);

            MarketModel periodflmm = new FwdPeriodAdapter(flmm, period, offset,
                    newDisplacements);

            MarketModel periodsmm = new FwdToCotSwapAdapter(periodflmm);

            Matrix swaptionTotCovariance =
                    periodsmm.totalCovariance(periodsmm.numberOfSteps() - 1);

            totalSwaptionError.setT(0.0);

            for (int i = 0; i < numberBigRates; ++i) {
                modelSwaptionVols.set(i,
                        Math.sqrt(swaptionTotCovariance.get(i, i)
                                / periodsmm.evolution().rateTimes().get(i)));
                double scale = marketSwaptionVols.get(i) / modelSwaptionVols.get(i);
                scalingFactors.set(i, scalingFactors.get(i) * scale); // since applied to vol

                totalSwaptionError.setT(totalSwaptionError.getT()
                        + (marketSwaptionVols.get(i) - modelSwaptionVols.get(i))
                        * (marketSwaptionVols.get(i) - modelSwaptionVols.get(i)));
            }

            for (int i = 0; i < numberBigRates; ++i)
                modelSwaptionVolsMatrix.getT().set(iterationsDone.getT(), i,
                        modelSwaptionVols.get(i));

            periodSwaptionRmsError = Math.sqrt(totalSwaptionError.getT() / numberBigRates);
            errorImprovement.setT(previousError - periodSwaptionRmsError);
            previousError = periodSwaptionRmsError;

            // Official do/while condition, including the short-circuit
            // pre-increment of iterationsDone.
            if (!(errorImprovement.getT() > periodTolerance / 10.0))
                break;
            if (!(periodSwaptionRmsError > periodTolerance))
                break;
            iterationsDone.setT(iterationsDone.getT() + 1);
            if (!(iterationsDone.getT() < maxPeriodIterations))
                break;
        }

        finalScales.clear();
        finalScales.addAll(scalingFactors);

        return failures;
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }

    private static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
