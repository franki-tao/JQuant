package jquant.models.marketmodels.models;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.Quadratic;
import jquant.math.ReferencePkg;
import jquant.math.matrixutilities.BasisIncompleteOrdered;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.math.optimization.SphereCylinderOptimizer;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.SwapForwardMappings;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/capletcoterminalmaxhomogeneity.hpp/.cpp}
 * port (class {@code CTSMMCapletMaxHomogeneityCalibration}).
 */
public class CapletCoterminalMaxHomogeneityCalibration extends CTSMMCapletCalibration {

    private final double caplet0Swaption1Priority_;
    // results
    private double totalSwaptionError_;

    public CapletCoterminalMaxHomogeneityCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> mktCapletVols,
            final CurveState cs,
            double displacement,
            double caplet0Swaption1Priority) {
        super(evolution, corr, displacedSwapVariances,
                mktCapletVols, cs, displacement);
        caplet0Swaption1Priority_ = caplet0Swaption1Priority;

        QL_REQUIRE(caplet0Swaption1Priority >= 0.0 &&
                        caplet0Swaption1Priority <= 1.0,
                "caplet0Swaption1Priority (" + caplet0Swaption1Priority +
                        ") must be in [0.0, 1.0]");
    }

    private static boolean singleRateClosestPointFinder(
            int capletNumber,
            final List<Double> homogeneousSolution,
            final List<Double> previousRateSolution,
            double capletVariance,
            final List<Double> correlations,
            double w0,
            double w1,
            double capletSwaptionPriority,
            int maxIterations,
            double tolerance,
            List<Double> solution,
            double finalWeight,
            ReferencePkg<Double> swaptionError,
            ReferencePkg<Double> capletError) {
        if (capletNumber == 0) // there only is one point so to go through everything would be silly
        {
            double previousSwapVariance = previousRateSolution.get(0) * previousRateSolution.get(0);
            double thisSwapVariance = homogeneousSolution.get(0) * homogeneousSolution.get(0)
                    + homogeneousSolution.get(1) * homogeneousSolution.get(1);
            double crossTerm = 2 * w0 * w1 * correlations.get(0) * previousRateSolution.get(0);
            double constantTerm = w0 * w0 * previousSwapVariance - capletVariance;
            double theta = w1 * w1;

            Quadratic q = new Quadratic(theta, crossTerm, constantTerm);
            ReferencePkg<Double> volminus = new ReferencePkg<>(0.0);
            ReferencePkg<Double> volplus = new ReferencePkg<>(0.0);
            boolean capSuccess = q.roots(volminus, volplus);
            double residual = thisSwapVariance - volminus.getT() * volminus.getT();
            boolean swapSuccess = residual >= 0;
            boolean success = capSuccess && swapSuccess;

            if (success) {
                solution.set(0, volminus.getT());
                solution.set(1, Math.sqrt(residual));
                swaptionError.setT(0.0);
                capletError.setT(0.0);
                return success;
            }

            boolean prioritizeCaplet = capletSwaptionPriority < 0.5;

            if (capSuccess && prioritizeCaplet) {
                solution.set(0, volminus.getT());
                solution.set(1, 0.0); // residual is negative or we'd have totally succeeded
                swaptionError.setT(Math.sqrt(thisSwapVariance) - volminus.getT());
                capletError.setT(0.0);
                return success;
            }

            if (capSuccess && !prioritizeCaplet) {
                solution.set(0, Math.sqrt(thisSwapVariance));
                solution.set(1, 0.0);
                swaptionError.setT(0.0);
                capletError.setT(Math.sqrt(q.value(solution.get(0)) + capletVariance)
                        - Math.sqrt(capletVariance));
                return success;
            }

            // ok caplets have failed
            if (swapSuccess) {
                solution.set(0, volminus.getT());
                solution.set(1, Math.sqrt(residual));
                swaptionError.setT(0.0);
                capletError.setT(Math.sqrt(q.value(solution.get(0)) + capletVariance)
                        - Math.sqrt(capletVariance));
                return success;
            }

            // ok caplets have failed and swaps fail with optimal caplet solution
            if (prioritizeCaplet) {
                solution.set(0, volminus.getT());
                solution.set(1, 0.0); // residual is negative or we'd have totally succeeded
                swaptionError.setT(Math.sqrt(thisSwapVariance) - volminus.getT());
                capletError.setT(0.0);
            } else {
                solution.set(0, Math.sqrt(thisSwapVariance));
                solution.set(1, 0.0);
                swaptionError.setT(0.0);
                capletError.setT(Math.sqrt(q.value(solution.get(0)) + capletVariance)
                        - Math.sqrt(capletVariance));
            }

            return success;
        }

        // first get in correct format
        double previousSwapVariance = 0.0;
        double thisSwapVariance = 0.0;
        {
            int i = 0;
            for (; i < capletNumber + 1; ++i) {
                previousSwapVariance += previousRateSolution.get(i) *
                        previousRateSolution.get(i);
                thisSwapVariance += homogeneousSolution.get(i) *
                        homogeneousSolution.get(i);
            }
            thisSwapVariance += homogeneousSolution.get(i) * homogeneousSolution.get(i);
        }

        double theta = w1 * w1;
        List<Double> b = filledList(capletNumber + 1, 0.0);
        Array cylinderCentre = new Array(capletNumber + 1, 0.0);
        Array targetArray = new Array(capletNumber + 2, 0.0);
        Array targetArrayRestricted = new Array(capletNumber + 1, 0.0);

        double bsq = 0.0;
        for (int i = 0; i < capletNumber + 1; ++i) {
            b.set(i, 2 * w0 * w1 * correlations.get(i) * previousRateSolution.get(i) / theta);
            cylinderCentre.set(i, -0.5 * b.get(i));
            targetArray.set(i, homogeneousSolution.get(i));
            targetArrayRestricted.set(i, targetArray.get(i));
            bsq += b.get(i) * b.get(i);
        }
        targetArray.set(capletNumber + 1, homogeneousSolution.get(capletNumber + 1));

        double A = previousSwapVariance * w0 * w0 / theta;
        double constQuadraticTerm = A - 0.25 * bsq;
        double S2 = capletVariance / theta - constQuadraticTerm;

        // if S2 < 0, there are no solutions so we take the best we can.
        double S = S2 > 0 ? Math.sqrt(S2) : 0;

        double R = Math.sqrt(thisSwapVariance);

        BasisIncompleteOrdered basis = new BasisIncompleteOrdered(capletNumber + 1);
        basis.addVector(cylinderCentre);
        basis.addVector(targetArrayRestricted);
        for (int i = 0; i < capletNumber + 1; ++i) {
            Array ei = new Array(capletNumber + 1, 0.0);
            ei.set(i, 1.0);
            basis.addVector(ei);
        }

        Matrix orthTransformationRestricted = basis.getBasisAsRowsInMatrix();
        Matrix orthTransformation = new Matrix(capletNumber + 2, capletNumber + 2, 0.0);

        orthTransformation.set(capletNumber + 1, capletNumber + 1, 1.0);
        for (int k = 0; k < capletNumber + 1; ++k)
            for (int l = 0; l < capletNumber + 1; ++l)
                orthTransformation.set(k, l, orthTransformationRestricted.get(k, l));

        Array movedCentre = matrixVectorProduct(
                orthTransformationRestricted, cylinderCentre);
        double alpha = movedCentre.get(0);
        Array movedTarget = matrixVectorProduct(orthTransformation, targetArray);

        double Z1 = 0.0, Z2 = 0.0, Z3 = 0.0;

        SphereCylinderOptimizer optimizer = new SphereCylinderOptimizer(R, S, alpha,
                movedTarget.get(0), movedTarget.get(1),
                movedTarget.get(movedTarget.size() - 1), finalWeight);

        boolean success = false;

        if (!optimizer.isIntersectionNonEmpty()) {
            Z1 = R * capletSwaptionPriority + (1 - capletSwaptionPriority) * (alpha - S);
            Z2 = 0.0;
            Z3 = 0.0;
            swaptionError.setT(Z1 - R);
            capletError.setT((alpha - S) - Z1);
        } else {
            success = true;
            capletError.setT(0.0);
            swaptionError.setT(0.0);

            ReferencePkg<Double> z1 = new ReferencePkg<>(0.0);
            ReferencePkg<Double> z2 = new ReferencePkg<>(0.0);
            ReferencePkg<Double> z3 = new ReferencePkg<>(0.0);
            if (maxIterations > 0.0) {
                optimizer.findClosest(maxIterations,
                        tolerance,
                        z1,
                        z2,
                        z3);
            } else {
                optimizer.findByProjection(
                        z1,
                        z2,
                        z3);
            }
            Z1 = z1.getT();
            Z2 = z2.getT();
            Z3 = z3.getT();
        }

        Array rotatedSolution = new Array(capletNumber + 2, 0.0);
        rotatedSolution.set(0, Z1);
        rotatedSolution.set(1, Z2);
        rotatedSolution.set(capletNumber + 1, Z3);

        Array arraySolution = transposeVectorProduct(orthTransformation, rotatedSolution);
        {
            int i = 0;
            for (; i < arraySolution.size(); ++i)
                solution.set(i, arraySolution.get(i));
            for (; i < solution.size(); ++i)
                solution.set(i, 0.0);
        }

        return success;
    }

    public static int capletMaxHomogeneityCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> capletVols,
            final CurveState cs,
            double displacement,
            double caplet0Swaption1Priority,
            int numberOfFactors,
            int maxIterations,
            double tolerance,
            ReferencePkg<Double> deformationSize,
            ReferencePkg<Double> totalSwaptionError,
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

        totalSwaptionError.setT(0.0);
        deformationSize.setT(0.0);

        // factor reduction
        List<Matrix> corrPseudo = new ArrayList<>();
        for (int i = 0; i < corr.times().size(); ++i)
            corrPseudo.add(MatrixUtil.rankReducedSqrt(corr.correlation(i),
                    numberOfFactors, 1.0, SalvagingAlgorithm.Type.None));

        // get Zinverse, we can get wj later
        Matrix zedMatrix =
                SwapForwardMappings.coterminalSwapZedMatrix(cs, displacement);
        Matrix invertedZedMatrix = zedMatrix.inverse();

        // vectors for the new vol of all swap rates
        List<List<Double>> newVols = new ArrayList<>();
        List<Double> theseNewVols = filledList(numberOfRates, 0.0);
        List<Double> firstRateVols = filledList(numberOfRates, 0.0);
        firstRateVols.set(0, Math.sqrt(displacedSwapVariances.get(0).variances().get(0)));
        List<Double> secondRateVols = filledList(numberOfRates, 0.0);
        List<Double> correlations = filledList(numberOfRates, 0.0);
        newVols.add(new ArrayList<>(firstRateVols));

        // final caplet and swaption are the same, so we skip that case
        for (int i = 0; i < numberOfRates - 1; ++i) {
            // final weight dont do anything when i < 2
            double thisFinalWeight = i > 1 ? (i - 1) / 2.0 : 1.0;
            // we will calibrate caplet on forward rate i,
            // we will do this by modifying the vol of swap rate i+1
            final List<Double> var = displacedSwapVariances.get(i + 1).variances();

            for (int j = 0; j < i + 2; ++j)
                secondRateVols.set(j, Math.sqrt(var.get(j)));

            for (int k = 0; k < i + 1; k++) {
                double correlation = 0.0;
                for (int l = 0; l < numberOfFactors; ++l) {
                    double term1 = corrPseudo.get(k).get(i, l);
                    double term2 = corrPseudo.get(k).get(i + 1, l);
                    correlation += term1 * term2;
                }
                correlations.set(k, correlation);
            }

            double w0 = invertedZedMatrix.get(i, i);
            double w1 = invertedZedMatrix.get(i, i + 1);
            // w0 adjustment
            for (int k = i + 2; k < invertedZedMatrix.cols(); ++k)
                w0 += invertedZedMatrix.get(i, k);

            double targetCapletVariance = capletVols.get(i) * capletVols.get(i) * rateTimes.get(i);

            ReferencePkg<Double> thisCapletError = new ReferencePkg<>(0.0);
            ReferencePkg<Double> thisSwaptionError = new ReferencePkg<>(0.0);

            boolean success = singleRateClosestPointFinder(
                    i, secondRateVols, firstRateVols, targetCapletVariance, correlations,
                    w0, w1, caplet0Swaption1Priority, maxIterations, tolerance,
                    theseNewVols, thisFinalWeight, thisSwaptionError, thisCapletError);

            totalSwaptionError.setT(totalSwaptionError.getT()
                    + thisSwaptionError.getT() * thisSwaptionError.getT());

            if (!success)
                ++failures;

            // Official quirk: index i (not j) is used inside the j loop.
            for (int j = 0; j < i + 2; ++j)
                deformationSize.setT(deformationSize.getT()
                        + (theseNewVols.get(i) - secondRateVols.get(i))
                        * (theseNewVols.get(i) - secondRateVols.get(i)));

            newVols.add(new ArrayList<>(theseNewVols));
            firstRateVols = new ArrayList<>(theseNewVols);
        }

        swapCovariancePseudoRoots.clear();
        for (int k = 0; k < numberOfSteps; ++k) {
            Matrix root = copyMatrix(corrPseudo.get(k));
            for (int j = 0; j < numberOfRates; ++j) {
                double coeff = newVols.get(j).get(k);
                for (int i = 0; i < numberOfFactors; ++i)
                    root.set(j, i, root.get(j, i) * coeff);
            }
            QL_REQUIRE(root.rows() == numberOfRates,
                    "step " + k + " abcd vol wrong number of rows: " +
                            root.rows() +
                            " instead of " + numberOfRates);
            QL_REQUIRE(root.cols() == numberOfFactors,
                    "step " + k + " abcd vol wrong number of columns: " +
                            root.cols() +
                            " instead of " + numberOfFactors);
            swapCovariancePseudoRoots.add(root);
        }

        return failures;
    }

    @Override
    protected int calibrationImpl_(int numberOfFactors,
                                   int maxIterations,
                                   double tolerance) {
        ReferencePkg<Double> deformationSize = new ReferencePkg<>(deformationSize_);
        ReferencePkg<Double> totalSwaptionError = new ReferencePkg<>(totalSwaptionError_);
        int failures = capletMaxHomogeneityCalibration(evolution_,
                corr_,
                displacedSwapVariances_,
                // not mktCapletVols_ but...
                usedCapletVols_,
                cs_,
                displacement_,
                caplet0Swaption1Priority_,
                numberOfFactors,
                maxIterations,
                tolerance,
                deformationSize,
                totalSwaptionError,
                swapCovariancePseudoRoots_);
        deformationSize_ = deformationSize.getT();
        totalSwaptionError_ = totalSwaptionError.getT();
        return failures;
    }

    private static Array matrixVectorProduct(final Matrix matrix, final Array vector) {
        Array result = new Array(matrix.rows(), 0.0);
        for (int i = 0; i < matrix.rows(); ++i) {
            double sum = 0.0;
            for (int j = 0; j < matrix.cols(); ++j)
                sum += matrix.get(i, j) * vector.get(j);
            result.set(i, sum);
        }
        return result;
    }

    private static Array transposeVectorProduct(final Matrix matrix, final Array vector) {
        Array result = new Array(matrix.cols(), 0.0);
        for (int i = 0; i < matrix.cols(); ++i) {
            double sum = 0.0;
            for (int j = 0; j < matrix.rows(); ++j)
                sum += matrix.get(j, i) * vector.get(j);
            result.set(i, sum);
        }
        return result;
    }
}
