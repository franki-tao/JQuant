package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.SwapForwardMappings;
import jquant.models.marketmodels.curvestates.CoterminalSwapCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/cotswaptofwdadapter.hpp/.cpp} port.
 */
public class CotSwapToFwdAdapter extends MarketModel {

    private final MarketModel coterminalModel_;
    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final List<Double> initialRates_;
    private final List<Matrix> pseudoRoots_ = new ArrayList<>();

    public CotSwapToFwdAdapter(final MarketModel coterminalModel) {
        coterminalModel_ = coterminalModel;
        numberOfFactors_ = coterminalModel.numberOfFactors();
        numberOfRates_ = coterminalModel.numberOfRates();
        numberOfSteps_ = coterminalModel.numberOfSteps();

        final List<Double> displacements = coterminalModel_.displacements();
        for (int i = 1; i < displacements.size(); ++i) {
            QL_REQUIRE(displacements.get(i).equals(displacements.get(0)),
                    jquant.models.marketmodels.Utilities.ordinal(i + 1)
                            + " displacement (" + displacements.get(i)
                            + ") not equal to the previous ones ("
                            + displacements.get(0) + ")");
        }

        final List<Double> rateTimes = coterminalModel_.evolution().rateTimes();
        // we must ensure we step through all rateTimes
        final List<Double> evolutionTimes = coterminalModel_.evolution().evolutionTimes();
        for (int i = 0;
             i < rateTimes.size() && rateTimes.get(i) <= evolutionTimes.get(evolutionTimes.size() - 1);
             ++i) {
            QL_REQUIRE(evolutionTimes.contains(rateTimes.get(i)),
                    "skipping " + jquant.models.marketmodels.Utilities.ordinal(i + 1)
                            + " rate time");
        }

        CoterminalSwapCurveState cs = new CoterminalSwapCurveState(rateTimes);
        final List<Double> initialCoterminalSwapRates = coterminalModel_.initialRates();
        cs.setOnCoterminalSwapRates(initialCoterminalSwapRates);
        initialRates_ = new ArrayList<>(cs.forwardRates());

        Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(
                cs, displacements.get(0));
        Matrix invertedZedMatrix = zedMatrix.inverse();

        final List<Integer> alive = coterminalModel_.evolution().firstAliveRate();
        for (int k = 0; k < numberOfSteps_; ++k) {
            Matrix pseudoRoot = invertedZedMatrix.multipy(coterminalModel_.pseudoRoot(k));
            for (int i = 0; i < alive.get(k); ++i)
                for (int j = 0; j < pseudoRoot.cols(); ++j)
                    pseudoRoot.set(i, j, 0.0);
            pseudoRoots_.add(pseudoRoot);
        }
    }

    @Override
    public List<Double> initialRates() {
        return initialRates_;
    }

    @Override
    public List<Double> displacements() {
        return coterminalModel_.displacements();
    }

    @Override
    public EvolutionDescription evolution() {
        return coterminalModel_.evolution();
    }

    @Override
    public int numberOfRates() {
        return coterminalModel_.numberOfRates();
    }

    @Override
    public int numberOfFactors() {
        return coterminalModel_.numberOfFactors();
    }

    @Override
    public int numberOfSteps() {
        return coterminalModel_.numberOfSteps();
    }

    @Override
    public Matrix pseudoRoot(int i) {
        return pseudoRoots_.get(i);
    }
}
