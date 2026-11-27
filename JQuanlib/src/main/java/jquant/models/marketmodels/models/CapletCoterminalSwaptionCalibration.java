package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.SwapForwardMappings;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/capletcoterminalswaptioncalibration.hpp/.cpp}
 * port (class {@code CTSMMCapletOriginalCalibration}, the GHLS calibration).
 */
public class CapletCoterminalSwaptionCalibration extends CTSMMCapletCalibration {

    private final List<Double> alpha_;
    private final boolean lowestRoot_;
    private final boolean useFullApprox_;

    public CapletCoterminalSwaptionCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> mktCapletVols,
            final CurveState cs,
            double displacement,
            final List<Double> alpha,
            boolean lowestRoot,
            boolean useFullApprox) {
        super(evolution, corr, displacedSwapVariances,
                mktCapletVols, cs, displacement);
        alpha_ = new ArrayList<>(alpha);
        lowestRoot_ = lowestRoot;
        useFullApprox_ = useFullApprox;

        QL_REQUIRE(numberOfRates_ == alpha.size(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and alpha (" + alpha.size() + ")");
    }

    public static int calibrationFunction(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> capletVols,
            final CurveState cs,
            double displacement,
            final List<Double> alpha,
            boolean lowestRoot,
            boolean useFullApprox,
            int numberOfFactors,
            List<Matrix> swapCovariancePseudoRoots) {

        CTSMMCapletCalibration.performChecks(evolution, corr,
                displacedSwapVariances, capletVols, cs);

        int numberOfSteps = evolution.numberOfSteps();
        int numberOfRates = evolution.numberOfRates();
        final List<Double> rateTimes = evolution.rateTimes();

        QL_REQUIRE(numberOfFactors <= numberOfRates,
                "number of factors (" + numberOfFactors +
                        ") cannot be greater than numberOfRates (" +
                        numberOfRates + ")");
        QL_REQUIRE(numberOfFactors > 0,
                "number of factors (" + numberOfFactors +
                        ") must be greater than zero");

        int failures = 0;
        double extraMultiplier = useFullApprox ? 1.0 : 0.0;

        // factor reduction
        List<Matrix> corrPseudo = new ArrayList<>();
        for (int i = 0; i < corr.times().size(); ++i)
            corrPseudo.add(MatrixUtil.rankReducedSqrt(corr.correlation(i),
                    numberOfFactors, 1.0, SalvagingAlgorithm.Type.None));

        Matrix zedMatrix =
                SwapForwardMappings.coterminalSwapZedMatrix(cs, displacement);
        Matrix invertedZedMatrix = zedMatrix.inverse();

        // do alpha part
        // first modify variances to take account of alpha
        // then rescale so total variance is unchanged
        Matrix swapTimeInhomogeneousVariances =
                new Matrix(numberOfSteps, numberOfRates, 0.0);
        List<Double> originalVariances = filledList(numberOfRates, 0.0);
        List<Double> modifiedVariances = filledList(numberOfRates, 0.0);
        final List<Double> evolutionTimes = evolution.evolutionTimes();
        for (int i = 0; i < numberOfSteps; ++i) {
            double s = (i == 0 ? 0.0 : evolutionTimes.get(i - 1));
            for (int j = i; j < numberOfRates; ++j) {
                final List<Double> var = displacedSwapVariances.get(j).variances();
                originalVariances.set(j, originalVariances.get(j) + var.get(i));
                swapTimeInhomogeneousVariances.set(i, j, var.get(i) /
                        ((1.0 + alpha.get(j) * s) * (1.0 + alpha.get(j) * s)));
                modifiedVariances.set(j, modifiedVariances.get(j)
                        + swapTimeInhomogeneousVariances.get(i, j));
            }
        }

        for (int i = 0; i < numberOfSteps; ++i)
            for (int j = i; j < numberOfRates; ++j)
                swapTimeInhomogeneousVariances.set(i, j,
                        swapTimeInhomogeneousVariances.get(i, j)
                                * originalVariances.get(j) / modifiedVariances.get(j));

        // compute swap covariances for caplet approximation formula
        // without taking into account A and B
        List<Matrix> CovarianceSwapPseudos = new ArrayList<>();
        List<Matrix> CovarianceSwapCovs = new ArrayList<>(); // this is total cov
        List<Matrix> CovarianceSwapMarginalCovs = new ArrayList<>(); // this is cov for one step

        for (int i = 0; i < numberOfSteps; ++i) {
            Matrix pseudo = copyMatrix(corrPseudo.get(i));
            for (int j = 0; j < numberOfRates; ++j)
                for (int k = 0; k < pseudo.cols(); ++k)
                    pseudo.set(j, k, pseudo.get(j, k)
                            * Math.sqrt(swapTimeInhomogeneousVariances.get(i, j)));
            CovarianceSwapPseudos.add(pseudo);

            Matrix marginal = pseudo.multipy(pseudo.transpose());
            CovarianceSwapMarginalCovs.add(marginal);

            Matrix cov = copyMatrix(marginal);
            if (i > 0)
                cov = cov.add(CovarianceSwapCovs.get(i - 1));
            CovarianceSwapCovs.add(cov);
        }

        // compute partial variances and covariances which will take A and B coefficients
        List<Double> totVariance = filledList(numberOfRates, 0.0);
        List<Double> almostTotVariance = filledList(numberOfRates, 0.0);
        List<Double> almostTotCovariance = filledList(numberOfRates, 0.0);
        List<Double> leftCovariance = filledList(numberOfRates, 0.0);
        for (int i = 0; i < numberOfRates; ++i) {
            for (int jj = 0; jj <= i; ++jj)
                totVariance.set(i, totVariance.get(i)
                        + displacedSwapVariances.get(i).variances().get(jj));
            int j = 0;
            for (; j <= i - 1; ++j)
                almostTotVariance.set(i, almostTotVariance.get(i)
                        + swapTimeInhomogeneousVariances.get(j, i));
            for (j = 0; j <= i - 2; ++j) {
                final Matrix thisPseudo = corrPseudo.get(j);
                double correlation = 0.0;
                for (int k = 0; k < numberOfFactors; ++k)
                    correlation += thisPseudo.get(i - 1, k) * thisPseudo.get(i, k);
                almostTotCovariance.set(i, almostTotCovariance.get(i)
                        + correlation *
                        Math.sqrt(swapTimeInhomogeneousVariances.get(j, i) *
                                swapTimeInhomogeneousVariances.get(j, i - 1)));
            }
            if (i > 0) {
                // Official quirk: j is left from the preceding loop, which
                // re-initialises it to 0 (so j==0 when i==1, else j==i-1).
                final Matrix thisPseudo = corrPseudo.get(j);
                double correlation = 0.0;
                for (int k = 0; k < numberOfFactors; ++k)
                    correlation += thisPseudo.get(i - 1, k) * thisPseudo.get(i, k);
                leftCovariance.set(i, correlation *
                        Math.sqrt(swapTimeInhomogeneousVariances.get(j, i) *
                                swapTimeInhomogeneousVariances.get(j, i - 1)));
            }
        }

        // multiplier up to rate reset previous time
        // the first element is not used
        List<Double> a = filledList(numberOfSteps, 1.0);
        // multiplier afterward
        List<Double> b = filledList(numberOfSteps, 0.0);

        b.set(0, displacedSwapVariances.get(0).variances().get(0)
                / swapTimeInhomogeneousVariances.get(0, 0));
        // main loop where work is done
        for (int i = 1; i < numberOfSteps; ++i) {
            int j = 0;
            // up date variances to take account of last a and b computed
            for (; j <= i - 2; j++)
                swapTimeInhomogeneousVariances.set(j, i - 1,
                        swapTimeInhomogeneousVariances.get(j, i - 1)
                                * a.get(i - 1) * a.get(i - 1));
            swapTimeInhomogeneousVariances.set(j, i - 1,
                    swapTimeInhomogeneousVariances.get(j, i - 1)
                            * b.get(i - 1) * b.get(i - 1));

            double w0 = invertedZedMatrix.get(i - 1, i - 1);
            double w1 = -invertedZedMatrix.get(i - 1, i);
            double v1t1 = capletVols.get(i - 1) * capletVols.get(i - 1) * rateTimes.get(i - 1);

            // now compute contribution from lower right corner
            double extraConstantPart = 0.0;
            // part of caplet approximation formula coming from later rates
            for (int k = i + 1; k < numberOfSteps; ++k)
                for (int l = i + 1; l < numberOfSteps; ++l)
                    extraConstantPart += invertedZedMatrix.get(i - 1, k) *
                            CovarianceSwapCovs.get(i - 1).get(k, l) *
                            invertedZedMatrix.get(i - 1, l);

            // now compute contribution from top row excluding first two columns and its transpose
            // we divide into two parts as one part is multiplied by a[i-1] and the other by b[i-1]
            for (int k = i + 1; k < numberOfSteps; ++k) {
                if (i > 1) {
                    extraConstantPart += invertedZedMatrix.get(i - 1, i - 1) *
                            CovarianceSwapCovs.get(i - 2).get(i - 1, k) *
                            invertedZedMatrix.get(i - 1, k) * a.get(i - 1);

                    extraConstantPart += invertedZedMatrix.get(i - 1, k) *
                            CovarianceSwapCovs.get(i - 2).get(k, i - 1) *
                            invertedZedMatrix.get(i - 1, i - 1) * a.get(i - 1);
                }

                extraConstantPart += invertedZedMatrix.get(i - 1, i - 1) *
                        CovarianceSwapMarginalCovs.get(i - 1).get(i - 1, k) *
                        invertedZedMatrix.get(i - 1, k) * b.get(i - 1);

                extraConstantPart += invertedZedMatrix.get(i - 1, k) *
                        CovarianceSwapCovs.get(i - 1).get(k, i - 1) *
                        invertedZedMatrix.get(i - 1, i - 1) * b.get(i - 1);
            }

            // we also get an extra linear part, this corresponds to row i, and columns j>i+1, and transpose
            double extraLinearPart = 0.0;
            for (int k = i + 1; k < numberOfSteps; ++k) {
                extraLinearPart += invertedZedMatrix.get(i - 1, k) *
                        CovarianceSwapCovs.get(i - 1).get(k, i) *
                        invertedZedMatrix.get(i - 1, i);

                extraLinearPart += invertedZedMatrix.get(i - 1, i) *
                        CovarianceSwapCovs.get(i - 1).get(i, k) *
                        invertedZedMatrix.get(i - 1, k);
            }

            double constantPart = w0 * w0 * totVariance.get(i - 1) +
                    extraConstantPart * extraMultiplier - v1t1;
            double linearPart = -2 * w0 * w1 * (a.get(i - 1) * almostTotCovariance.get(i) +
                    b.get(i - 1) * leftCovariance.get(i))
                    + extraLinearPart * extraMultiplier;
            double quadraticPart = w1 * w1 * almostTotVariance.get(i);
            double disc = linearPart * linearPart - 4.0 * constantPart * quadraticPart;

            double root;
            double minimum = -linearPart / (2.0 * quadraticPart);
            boolean rightUsed = false;
            if (disc < 0.0) {
                ++failures;
                // pick up the minimum vol for the caplet
                root = minimum;
            } else if (lowestRoot) {
                root = (-linearPart - Math.sqrt(disc)) / (2.0 * quadraticPart);
            } else {
                if (minimum > 1.0)
                    root = (-linearPart - Math.sqrt(disc)) / (2.0 * quadraticPart);
                else {
                    rightUsed = true;
                    root = (-linearPart + Math.sqrt(disc)) / (2.0 * quadraticPart);
                }
            }

            double varianceFound = root * root * almostTotVariance.get(i);
            double varianceToFind = totVariance.get(i) - varianceFound;
            double mult = varianceToFind / swapTimeInhomogeneousVariances.get(i, i);
            if (mult <= 0.0 && rightUsed) {
                root = (-linearPart - Math.sqrt(disc)) / (2.0 * quadraticPart);
                varianceFound = root * root * almostTotVariance.get(i);
                varianceToFind = totVariance.get(i) - varianceFound;
                mult = varianceToFind / swapTimeInhomogeneousVariances.get(i, i);
            }

            if (mult < 0.0) { // no solution...
                ++failures;
                a.set(i, root);
                b.set(i, 0.0);
            } else {
                a.set(i, root);
                b.set(i, Math.sqrt(mult));
            }

            QL_REQUIRE(root >= 0.0,
                    "negative root -- it should have not happened");
        }

        {
            int i = numberOfSteps;
            int j = 0;
            for (; j <= i - 2; j++)
                swapTimeInhomogeneousVariances.set(j, i - 1,
                        swapTimeInhomogeneousVariances.get(j, i - 1)
                                * a.get(i - 1) * a.get(i - 1));
            swapTimeInhomogeneousVariances.set(j, i - 1,
                    swapTimeInhomogeneousVariances.get(j, i - 1)
                            * b.get(i - 1) * b.get(i - 1));
        }

        // compute the results
        swapCovariancePseudoRoots.clear();
        for (int k = 0; k < numberOfSteps; ++k) {
            Matrix root = copyMatrix(corrPseudo.get(k));
            for (int j = 0; j < numberOfRates; ++j) {
                double coeff = Math.sqrt(swapTimeInhomogeneousVariances.get(k, j));
                for (int i = 0; i < numberOfFactors; ++i)
                    root.set(j, i, root.get(j, i) * coeff);
            }
            QL_REQUIRE(root.rows() == numberOfRates,
                    "step " + k +
                            " abcd vol wrong number of rows: " +
                            root.rows() +
                            " instead of " + numberOfRates);
            QL_REQUIRE(root.cols() == numberOfFactors,
                    "step " + k +
                            " abcd vol wrong number of columns: " +
                            root.cols() +
                            " instead of " + numberOfFactors);
            swapCovariancePseudoRoots.add(root);
        }

        return failures;
    }

    @Override
    protected int calibrationImpl_(int numberOfFactors,
                                   int innerMaxIterations,
                                   double innerTolerance) {
        return calibrationFunction(evolution_,
                corr_,
                displacedSwapVariances_,
                // not mktCapletVols_ but...
                usedCapletVols_,
                cs_,
                displacement_,
                alpha_,
                lowestRoot_,
                useFullApprox_,
                numberOfFactors,
                swapCovariancePseudoRoots_);
    }
}
