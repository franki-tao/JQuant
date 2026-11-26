package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.ForwardForwardMappings;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/models/fwdperiodadapter.hpp/.cpp} port.
 */
public class FwdPeriodAdapter extends MarketModel {

    private final int numberOfFactors_;
    private final int numberOfRates_;
    private int numberOfSteps_;
    private final List<Matrix> pseudoRoots_ = new ArrayList<>();
    private final List<Double> displacements_;
    private final List<Double> initialRates_;
    private final EvolutionDescription evolution_;

    public FwdPeriodAdapter(final MarketModel largeModel,
                            int period,
                            int offset,
                            List<Double> newDisplacements) {
        numberOfFactors_ = largeModel.numberOfFactors();
        numberOfRates_ = (largeModel.numberOfRates() - offset)
                / (period > 0 ? period : 1);
        numberOfSteps_ = largeModel.numberOfSteps();
        for (int i = 0; i < numberOfSteps_; ++i)
            pseudoRoots_.add(new Matrix(numberOfRates_, numberOfFactors_));
        displacements_ = new ArrayList<>(newDisplacements);
        QL_REQUIRE(period > 0, "period must  be greater than zero in fwdperiodadapter");
        QL_REQUIRE(period > offset, "period must be greater than offset in fwdperiodadapter");

        final List<Double> largeDisplacements_ = largeModel.displacements();

        if (displacements_.size() == 1) {
            double dis = displacements_.get(0);
            displacements_.clear();
            for (int i = 0; i < numberOfRates_; ++i)
                displacements_.add(dis);
        }

        if (displacements_.isEmpty()) { // if not specified use average across rate
            int m = 0;
            double sum = 0.0;
            for (int k = 0; k < numberOfRates_; ++k) {
                for (int l = 0; l < period; ++l, ++m)
                    sum += largeDisplacements_.get(m);

                displacements_.add(sum / period);
            }
        }
        QL_REQUIRE(displacements_.size() == numberOfRates_,
                "newDisplacements should be empty,1, or number of new rates in fwdperiodadapter");

        LMMCurveState largeCS = new LMMCurveState(largeModel.evolution().rateTimes());
        largeCS.setOnForwardRates(largeModel.initialRates());

        LMMCurveState smallCS =
                ForwardForwardMappings.RestrictCurveState(largeCS, period, offset);

        initialRates_ = new ArrayList<>(smallCS.forwardRates());

        double finalReset = smallCS.rateTimes().get(smallCS.numberOfRates() - 1);
        final List<Double> oldEvolutionTimes = largeModel.evolution().evolutionTimes();
        List<Double> newEvolutionTimes = new ArrayList<>();
        for (int i = 0; i < oldEvolutionTimes.size()
                && oldEvolutionTimes.get(i) <= finalReset; ++i)
            newEvolutionTimes.add(oldEvolutionTimes.get(i));

        evolution_ = new EvolutionDescription(smallCS.rateTimes(), newEvolutionTimes);

        numberOfSteps_ = newEvolutionTimes.size();

        final List<Double> rateTimes = smallCS.rateTimes();
        // we must ensure we step through all rateTimes
        final List<Double> evolutionTimes = evolution_.evolutionTimes();

        Set<Double> setTimes = new HashSet<>(evolutionTimes);

        for (int i = 0; i < rateTimes.size() - 1; ++i)
            QL_REQUIRE(setTimes.contains(rateTimes.get(i)),
                    "every new rate time except last must be an evolution time in fwdperiod adapter");

        Matrix YMatrix = ForwardForwardMappings.YMatrix(largeCS,
                largeDisplacements_,
                displacements_,
                period,
                offset);

        final List<Integer> alive = evolution_.firstAliveRate();

        for (int k = 0; k < numberOfSteps_; ++k) {
            Matrix pseudoRoot = YMatrix.multipy(largeModel.pseudoRoot(k));
            for (int i = 0; i < alive.get(k); ++i)
                for (int j = 0; j < pseudoRoot.cols(); ++j)
                    pseudoRoot.set(i, j, 0.0);
            pseudoRoots_.set(k, pseudoRoot);
        }
    }

    @Override
    public List<Double> initialRates() {
        return initialRates_;
    }

    @Override
    public List<Double> displacements() {
        return displacements_;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }

    @Override
    public int numberOfFactors() {
        return numberOfFactors_;
    }

    @Override
    public int numberOfSteps() {
        return numberOfSteps_;
    }

    @Override
    public Matrix pseudoRoot(int i) {
        return pseudoRoots_.get(i);
    }
}
