package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.math.ReferencePkg;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.SwapForwardMappings;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/capletcoterminalalphacalibration.hpp/.cpp}
 * port (class {@code CTSMMCapletAlphaFormCalibration}).
 */
public class CapletCoterminalAlphaCalibration extends CTSMMCapletCalibration {

    // input
    private final List<Double> alphaInitial_;
    private final List<Double> alphaMax_;
    private final List<Double> alphaMin_;
    private final boolean maximizeHomogeneity_;
    private final AlphaForm parametricForm_;
    // results
    private final List<Double> alpha_;
    private final List<Double> a_;
    private final List<Double> b_;

    public CapletCoterminalAlphaCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> mktCapletVols,
            final CurveState cs,
            double displacement,
            final List<Double> alphaInitial,
            final List<Double> alphaMax,
            final List<Double> alphaMin,
            boolean maximizeHomogeneity) {
        this(evolution, corr, displacedSwapVariances, mktCapletVols, cs,
                displacement, alphaInitial, alphaMax, alphaMin,
                maximizeHomogeneity, null);
    }

    public CapletCoterminalAlphaCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> mktCapletVols,
            final CurveState cs,
            double displacement,
            final List<Double> alphaInitial,
            final List<Double> alphaMax,
            final List<Double> alphaMin,
            boolean maximizeHomogeneity,
            AlphaForm parametricForm) {
        super(evolution, corr, displacedSwapVariances,
                mktCapletVols, cs, displacement);
        alphaInitial_ = new ArrayList<>(alphaInitial);
        alphaMax_ = new ArrayList<>(alphaMax);
        alphaMin_ = new ArrayList<>(alphaMin);
        maximizeHomogeneity_ = maximizeHomogeneity;
        parametricForm_ = (parametricForm != null)
                ? parametricForm
                : new AlphaFormLinearHyperbolic(evolution.rateTimes());
        alpha_ = filledList(numberOfRates_, 0.0);
        a_ = filledList(numberOfRates_, 0.0);
        b_ = filledList(numberOfRates_, 0.0);

        QL_REQUIRE(numberOfRates_ == alphaInitial.size(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and alphaInitial (" + alphaInitial.size() + ")");

        QL_REQUIRE(numberOfRates_ == alphaMax.size(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and alphaMax (" + alphaMax.size() + ")");

        QL_REQUIRE(numberOfRates_ == alphaMin.size(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and alphaMin (" + alphaMin.size() + ")");
    }

    public List<Double> alpha() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return alpha_;
    }

    public static int capletAlphaFormCalibration(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> capletVols,
            final CurveState cs,
            double displacement,
            final List<Double> alphaInitial,
            final List<Double> alphaMax,
            final List<Double> alphaMin,
            boolean maximizeHomogeneity,
            final AlphaForm parametricForm,
            int numberOfFactors,
            int maxIterations,
            double tolerance,
            List<Double> alpha,
            List<Double> a,
            List<Double> b,
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

        alpha.clear();
        a.clear();
        b.clear();
        for (int i = 0; i < numberOfRates; ++i) {
            alpha.add(0.0);
            a.add(0.0);
            b.add(0.0);
        }

        // factor reduction
        List<Matrix> corrPseudo = new ArrayList<>();
        for (int i = 0; i < corr.times().size(); ++i)
            corrPseudo.add(MatrixUtil.rankReducedSqrt(corr.correlation(i),
                    numberOfFactors, 1.0, SalvagingAlgorithm.Type.None));

        // get Zinverse, we can get wj later
        Matrix zedMatrix =
                SwapForwardMappings.coterminalSwapZedMatrix(cs, displacement);
        Matrix invertedZedMatrix = zedMatrix.inverse();

        // vectors for new vol
        List<List<Double>> newVols = new ArrayList<>();
        List<Double> theseNewVols = filledList(numberOfRates, 0.0);
        List<Double> firstRateVols = filledList(numberOfRates, 0.0);
        firstRateVols.set(0, Math.sqrt(displacedSwapVariances.get(0).variances().get(0)));
        List<Double> secondRateVols = filledList(numberOfRates, 0.0);
        List<Double> correlations = filledList(numberOfRates, 0.0);
        newVols.add(new ArrayList<>(firstRateVols));

        alpha.set(0, alphaInitial.get(0)); // has no effect on anything in any case
        a.set(0, 1.0);
        b.set(0, 1.0); // no modifications to swap vol for first rate

        AlphaFinder solver = new AlphaFinder(parametricForm);

        // final caplet and swaption are the same, so we skip that case
        for (int i = 0; i < numberOfRates - 1; ++i) {
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

            double targetVariance = capletVols.get(i) * capletVols.get(i) * rateTimes.get(i);

            ReferencePkg<Double> alphaRef = new ReferencePkg<>(alpha.get(i + 1));
            ReferencePkg<Double> aRef = new ReferencePkg<>(a.get(i + 1));
            ReferencePkg<Double> bRef = new ReferencePkg<>(b.get(i + 1));

            boolean success;
            if (maximizeHomogeneity)
                success = solver.solveWithMaxHomogeneity(
                        alphaInitial.get(i + 1),
                        i,
                        firstRateVols,
                        secondRateVols,
                        correlations,
                        w0,
                        w1,
                        targetVariance,
                        tolerance,
                        alphaMax.get(i + 1),
                        alphaMin.get(i + 1),
                        maxIterations,
                        alphaRef,
                        aRef,
                        bRef,
                        theseNewVols);
            else
                success = solver.solve(alphaInitial.get(i + 1),
                        i,
                        firstRateVols,
                        secondRateVols,
                        correlations,
                        w0,
                        w1,
                        targetVariance,
                        tolerance,
                        alphaMax.get(i + 1),
                        alphaMin.get(i + 1),
                        maxIterations,
                        alphaRef,
                        aRef,
                        bRef,
                        theseNewVols);

            alpha.set(i + 1, alphaRef.getT());
            a.set(i + 1, aRef.getT());
            b.set(i + 1, bRef.getT());

            if (!success) {
                //++failures;
                QL_FAIL("alpha form failure");
            }

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
                                   int maxIterations,
                                   double tolerance) {
        return capletAlphaFormCalibration(evolution_,
                corr_,
                displacedSwapVariances_,
                // not mktCapletVols_ but...
                usedCapletVols_,
                cs_,
                displacement_,
                alphaInitial_,
                alphaMax_,
                alphaMin_,
                maximizeHomogeneity_,
                parametricForm_,
                numberOfFactors,
                maxIterations,
                tolerance,
                alpha_,
                a_,
                b_,
                swapCovariancePseudoRoots_);
    }
}
