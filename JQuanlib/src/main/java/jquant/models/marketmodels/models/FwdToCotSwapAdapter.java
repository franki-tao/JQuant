package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.SwapForwardMappings;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/models/fwdtocotswapadapter.hpp/.cpp} port.
 */
public class FwdToCotSwapAdapter extends MarketModel {

    private final MarketModel fwdModel_;
    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final List<Double> initialRates_;
    private final List<Matrix> pseudoRoots_ = new ArrayList<>();

    public FwdToCotSwapAdapter(final MarketModel fwdModel) {
        fwdModel_ = fwdModel;
        numberOfFactors_ = fwdModel.numberOfFactors();
        numberOfRates_ = fwdModel.numberOfRates();
        numberOfSteps_ = fwdModel.numberOfSteps();

        final List<Double> displacements = fwdModel_.displacements();
        for (int i = 1; i < displacements.size(); ++i) {
            QL_REQUIRE(displacements.get(i).equals(displacements.get(0)),
                    jquant.models.marketmodels.Utilities.ordinal(i + 1)
                            + " displacement (" + displacements.get(i)
                            + ") not equal to the previous ones ("
                            + displacements.get(0) + ")");
        }

        final List<Double> rateTimes = fwdModel_.evolution().rateTimes();
        // we must ensure we step through all rateTimes
        final List<Double> evolutionTimes = fwdModel_.evolution().evolutionTimes();
        for (int i = 0;
             i < rateTimes.size() && rateTimes.get(i) <= evolutionTimes.get(evolutionTimes.size() - 1);
             ++i) {
            QL_REQUIRE(evolutionTimes.contains(rateTimes.get(i)),
                    "skipping " + jquant.models.marketmodels.Utilities.ordinal(i + 1)
                            + " rate time");
        }

        LMMCurveState cs = new LMMCurveState(rateTimes);
        final List<Double> initialFwdRates = fwdModel_.initialRates();
        cs.setOnForwardRates(initialFwdRates);
        initialRates_ = new ArrayList<>(cs.coterminalSwapRates());

        Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(
                cs, displacements.get(0));

        final List<Integer> alive = fwdModel_.evolution().firstAliveRate();
        for (int k = 0; k < numberOfSteps_; ++k) {
            Matrix pseudoRoot = zedMatrix.multipy(fwdModel_.pseudoRoot(k));
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
        return fwdModel_.displacements();
    }

    @Override
    public EvolutionDescription evolution() {
        return fwdModel_.evolution();
    }

    @Override
    public int numberOfRates() {
        return fwdModel_.numberOfRates();
    }

    @Override
    public int numberOfFactors() {
        return fwdModel_.numberOfFactors();
    }

    @Override
    public int numberOfSteps() {
        return fwdModel_.numberOfSteps();
    }

    @Override
    public Matrix pseudoRoot(int i) {
        return pseudoRoots_.get(i);
    }
}
