package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/ctsmmcapletcalibration.hpp/.cpp} port.
 */
public abstract class CTSMMCapletCalibration {

    // input
    protected final EvolutionDescription evolution_;
    protected final PiecewiseConstantCorrelation corr_;
    protected final List<PiecewiseConstantVariance> displacedSwapVariances_;
    protected final List<Double> mktCapletVols_;
    protected final List<Double> mdlCapletVols_;
    protected final List<Double> mktSwaptionVols_;
    protected final List<Double> mdlSwaptionVols_;
    protected List<List<Double>> timeDependentCalibratedSwaptionVols_ = new ArrayList<>();
    protected final CurveState cs_;
    protected final double displacement_;
    protected final int numberOfRates_;
    // working variables
    protected List<Double> usedCapletVols_ = new ArrayList<>();
    // results
    protected boolean calibrated_;
    protected int failures_;
    protected double deformationSize_;
    protected double capletRmsError_;
    protected double capletMaxError_;
    protected double swaptionRmsError_;
    protected double swaptionMaxError_;
    protected final List<Matrix> swapCovariancePseudoRoots_ = new ArrayList<>();

    public CTSMMCapletCalibration(final EvolutionDescription evolution,
                                  final PiecewiseConstantCorrelation corr,
                                  final List<PiecewiseConstantVariance> displacedSwapVariances,
                                  final List<Double> mktCapletVols,
                                  final CurveState cs,
                                  double displacement) {
        evolution_ = evolution;
        corr_ = corr;
        displacedSwapVariances_ = new ArrayList<>(displacedSwapVariances);
        mktCapletVols_ = new ArrayList<>(mktCapletVols);
        cs_ = cs;
        displacement_ = displacement;
        numberOfRates_ = evolution.numberOfRates();
        mdlCapletVols_ = filledList(numberOfRates_, 0.0);
        mktSwaptionVols_ = filledList(numberOfRates_, 0.0);
        mdlSwaptionVols_ = filledList(numberOfRates_, 0.0);
        performChecks(evolution_, corr_, displacedSwapVariances_,
                mktCapletVols_, cs_);
    }

    public List<Double> timeDependentUnCalibratedSwaptionVols(int i) {
        QL_REQUIRE(i < numberOfRates_,
                "index (" + i + ") must less than number of rates (" +
                        numberOfRates_ + ")");
        return displacedSwapVariances_.get(i).volatilities();
    }

    public List<Double> timeDependentCalibratedSwaptionVols(int i) {
        QL_REQUIRE(i < numberOfRates_,
                "index (" + i + ") must less than number of rates (" +
                        numberOfRates_ + ")");
        return timeDependentCalibratedSwaptionVols_.get(i);
    }

    public static void performChecks(
            final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr,
            final List<PiecewiseConstantVariance> displacedSwapVariances,
            final List<Double> mktCapletVols,
            final CurveState cs) {
        final List<Double> evolutionTimes = evolution.evolutionTimes();
        QL_REQUIRE(evolutionTimes.equals(corr.times()),
                "evolutionTimes " + evolutionTimes +
                        " not equal to correlation times " + corr.times());

        final List<Double> rateTimes = evolution.rateTimes();
        QL_REQUIRE(rateTimes.equals(cs.rateTimes()),
                "mismatch between EvolutionDescription and " +
                        "CurveState rate times");

        int numberOfRates = evolution.numberOfRates();
        QL_REQUIRE(numberOfRates == displacedSwapVariances.size(),
                "mismatch between EvolutionDescription number of rates (" +
                        numberOfRates + ") and displacedSwapVariances size (" +
                        displacedSwapVariances.size() + ")");
        QL_REQUIRE(numberOfRates == corr.numberOfRates(),
                "mismatch between EvolutionDescription number of rates (" +
                        numberOfRates + ") and corr number of rates (" +
                        corr.numberOfRates() + ")");
        QL_REQUIRE(numberOfRates == mktCapletVols.size(),
                "mismatch between EvolutionDescription number of rates (" +
                        numberOfRates + ") and mktCapletVols size (" +
                        mktCapletVols.size() + ")");
        QL_REQUIRE(numberOfRates == cs.numberOfRates(),
                "mismatch between EvolutionDescription number of rates (" +
                        numberOfRates + ") and CurveState  number of rates (" +
                        cs.numberOfRates() + ")");

        List<Double> temp = new ArrayList<>(rateTimes.subList(0, rateTimes.size() - 1));
        QL_REQUIRE(temp.equals(evolutionTimes),
                "mismatch between evolutionTimes and rateTimes");

        double lastSwaptionVol =
                displacedSwapVariances.get(displacedSwapVariances.size() - 1)
                        .totalVolatility(numberOfRates - 1);
        QL_REQUIRE(close(lastSwaptionVol, mktCapletVols.get(numberOfRates - 1)),
                "last caplet vol (" + mktCapletVols.get(numberOfRates - 1) +
                        ") must be equal to last swaption vol (" +
                        lastSwaptionVol + "); discrepancy is " +
                        (lastSwaptionVol - mktCapletVols.get(numberOfRates - 1)));
    }

    public boolean calibrate(int numberOfFactors,
                             int maxIterations,
                             double capletVolTolerance,
                             int innerSolvingMaxIterations,
                             double innerSolvingTolerance) {
        // initialize results
        calibrated_ = false;
        failures_ = 987654321; // a positive large number
        deformationSize_ = 987654321;
        capletRmsError_ = swaptionRmsError_ = 987654321;
        capletMaxError_ = swaptionMaxError_ = 987654321;

        // initialize working variables
        usedCapletVols_ = new ArrayList<>(mktCapletVols_);

        for (int i = 0; i < numberOfRates_; ++i)
            mktSwaptionVols_.set(i, displacedSwapVariances_.get(i).totalVolatility(i));

        List<Double> displacements = filledList(numberOfRates_, displacement_);
        final List<Double> rateTimes = evolution_.rateTimes();
        int iterations = 0;

        // calibration loop
        do {
            failures_ = calibrationImpl_(numberOfFactors,
                    innerSolvingMaxIterations,
                    innerSolvingTolerance);

            MarketModel ctsmm = new PseudoRootFacade(swapCovariancePseudoRoots_,
                    rateTimes,
                    cs_.coterminalSwapRates(),
                    displacements);
            final Matrix swaptionTotCovariance =
                    ctsmm.totalCovariance(numberOfRates_ - 1);

            CotSwapToFwdAdapter flmm = new CotSwapToFwdAdapter(ctsmm);
            final Matrix capletTotCovariance =
                    flmm.totalCovariance(numberOfRates_ - 1);

            // check fit
            capletRmsError_ = swaptionRmsError_ = 0.0;
            capletMaxError_ = swaptionMaxError_ = -1.0;

            for (int i = 0; i < numberOfRates_; ++i) {
                mdlSwaptionVols_.set(i,
                        Math.sqrt(swaptionTotCovariance.get(i, i) / rateTimes.get(i)));
                double swaptionError = Math.abs(mktSwaptionVols_.get(i) - mdlSwaptionVols_.get(i));
                swaptionRmsError_ += swaptionError * swaptionError;
                swaptionMaxError_ = Math.max(swaptionMaxError_, swaptionError);

                mdlCapletVols_.set(i,
                        Math.sqrt(capletTotCovariance.get(i, i) / rateTimes.get(i)));
                double capletError = Math.abs(mktCapletVols_.get(i) - mdlCapletVols_.get(i));
                capletRmsError_ += capletError * capletError;
                capletMaxError_ = Math.max(capletMaxError_, capletError);

                if (i < numberOfRates_ - 1)
                    usedCapletVols_.set(i,
                            usedCapletVols_.get(i) * mktCapletVols_.get(i) / mdlCapletVols_.get(i));
            }
            swaptionRmsError_ = Math.sqrt(swaptionRmsError_ / numberOfRates_);
            capletRmsError_ = Math.sqrt(capletRmsError_ / numberOfRates_);
            ++iterations;
        } while (iterations < maxIterations &&
                capletRmsError_ > capletVolTolerance);

        MarketModel ctsmm = new PseudoRootFacade(swapCovariancePseudoRoots_,
                rateTimes,
                cs_.coterminalSwapRates(),
                displacements);

        timeDependentCalibratedSwaptionVols_.clear();
        for (int i = 0; i < numberOfRates_; ++i)
            timeDependentCalibratedSwaptionVols_.add(
                    new ArrayList<>(ctsmm.timeDependentVolatility(i)));

        // calculate deformationSize_ ??
        calibrated_ = true;
        return failures_ == 0;
    }

    protected abstract int calibrationImpl_(int numberOfFactors,
                                            int innerMaxIterations,
                                            double innerTolerance);

    // inspectors
    public int failures() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return failures_;
    }

    public double deformationSize() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return deformationSize_;
    }

    public double capletRmsError() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return capletRmsError_;
    }

    public double capletMaxError() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return capletMaxError_;
    }

    public double swaptionRmsError() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return swaptionRmsError_;
    }

    public double swaptionMaxError() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return swaptionMaxError_;
    }

    public List<Matrix> swapPseudoRoots() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return swapCovariancePseudoRoots_;
    }

    public Matrix swapPseudoRoot(int i) {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        QL_REQUIRE(i < swapCovariancePseudoRoots_.size(),
                i + "is an invalid index, must be less than " +
                        swapCovariancePseudoRoots_.size());
        return swapCovariancePseudoRoots_.get(i);
    }

    public List<Double> mktCapletVols() {
        return mktCapletVols_;
    }

    public List<Double> mdlCapletVols() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return mdlCapletVols_;
    }

    public List<Double> mktSwaptionVols() {
        return mktSwaptionVols_;
    }

    public List<Double> mdlSwaptionVols() {
        QL_REQUIRE(calibrated_, "not successfully calibrated yet");
        return mdlSwaptionVols_;
    }

    public CurveState curveState() {
        return cs_;
    }

    public List<Double> displacements() {
        return filledList(numberOfRates_, displacement_);
    }

    protected static boolean close(double x, double y) {
        double eps = 1e-12;
        return Math.abs(x - y) <= eps * Math.abs(x)
                && Math.abs(x - y) <= eps * Math.abs(y);
    }

    protected static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }

    protected static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
