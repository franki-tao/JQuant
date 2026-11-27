package jquant.models.marketmodels.correlations;

import jquant.math.Matrix;
import jquant.math.matrixutilities.CovarianceDecomposition;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.SwapForwardMappings;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/correlations/cotswapfromfwdcorrelation.hpp/.cpp}
 * port.
 */
public class CotSwapFromFwdCorrelation extends PiecewiseConstantCorrelation {

    private final PiecewiseConstantCorrelation fwdCorr_;
    private final int numberOfRates_;
    private final List<Matrix> swapCorrMatrices_;

    public CotSwapFromFwdCorrelation(final PiecewiseConstantCorrelation fwdCorr,
                                     final CurveState curveState,
                                     double displacement) {
        fwdCorr_ = fwdCorr;
        numberOfRates_ = fwdCorr.numberOfRates();
        swapCorrMatrices_ = new ArrayList<>();

        QL_REQUIRE(numberOfRates_ == curveState.numberOfRates(),
                "mismatch between number of rates in fwdCorr (" +
                        numberOfRates_ + ") and curveState (" +
                        curveState.numberOfRates() + ")");

        Matrix zed = SwapForwardMappings.coterminalSwapZedMatrix(
                curveState, displacement);
        Matrix zedT = zed.transpose();
        List<Matrix> fwdCorrMatrices = fwdCorr.correlations();
        for (int k = 0; k < fwdCorrMatrices.size(); ++k) {
            Matrix swapCorr = new CovarianceDecomposition(
                    zed.multipy(fwdCorrMatrices.get(k)).multipy(zedT), 1e-12)
                    .correlationMatrix();
            // zero expired rates' correlation coefficients
            List<Double> rateTimes = curveState.rateTimes();
            List<Double> corrTimes = fwdCorr.times();
            for (int i = 0; i < numberOfRates_; ++i)
                for (int j = 0; j <= i; ++j)
                    if (corrTimes.get(k) > rateTimes.get(j)) {
                        swapCorr.set(i, j, 0.0);
                        swapCorr.set(j, i, 0.0);
                    }
            swapCorrMatrices_.add(swapCorr);
        }
    }

    @Override
    public List<Double> times() {
        return fwdCorr_.times();
    }

    @Override
    public List<Double> rateTimes() {
        return fwdCorr_.rateTimes();
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }

    @Override
    public List<Matrix> correlations() {
        return swapCorrMatrices_;
    }
}
