package jquant.models.marketmodels.pathwisegreeks;

import jquant.models.marketmodels.MarketModel;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/vegabumpcluster.hpp/.cpp} port.
 *
 * <p>When bumping vols, bumping every pseudo-root element individually seems
 * excessive so we need to couple some together.
 */
public class VegaBumpCluster {

    private final int factorBegin_;
    private final int factorEnd_;
    private final int rateBegin_;
    private final int rateEnd_;
    private final int stepBegin_;
    private final int stepEnd_;

    public VegaBumpCluster(int factorBegin,
                           int factorEnd,
                           int rateBegin,
                           int rateEnd,
                           int stepBegin,
                           int stepEnd) {
        factorBegin_ = factorBegin;
        factorEnd_ = factorEnd;
        rateBegin_ = rateBegin;
        rateEnd_ = rateEnd;
        stepBegin_ = stepBegin;
        stepEnd_ = stepEnd;

        QL_REQUIRE(factorBegin_ < factorEnd_,
                "must have factorBegin_ < factorEnd_ in VegaBumpCluster ");
        QL_REQUIRE(rateBegin_ < rateEnd_,
                "must have rateBegin_ < rateEnd_ in VegaBumpCluster ");
        QL_REQUIRE(stepBegin_ < stepEnd_,
                "must have stepBegin_ < stepEnd_ in VegaBumpCluster ");
    }

    public boolean doesIntersect(final VegaBumpCluster comparee) {
        if (factorEnd_ <= comparee.factorBegin_)
            return false;

        if (rateEnd_ <= comparee.rateBegin_)
            return false;

        if (stepEnd_ <= comparee.stepBegin_)
            return false;

        if (comparee.factorEnd_ <= factorBegin_)
            return false;

        if (comparee.rateEnd_ <= rateBegin_)
            return false;

        if (comparee.stepEnd_ <= stepBegin_)
            return false;

        return true;
    }

    public boolean isCompatible(final MarketModel volStructure) {
        if (rateEnd_ > volStructure.numberOfRates())
            return false;

        if (stepEnd_ > volStructure.numberOfSteps())
            return false;

        if (factorEnd_ > volStructure.numberOfFactors())
            return false;

        int firstAliveRate = volStructure.evolution().firstAliveRate().get(stepEnd_ - 1);

        // if the rate has reset after the beginning of the last step of the bump
        return rateBegin_ >= firstAliveRate;
    }

    public int factorBegin() {
        return factorBegin_;
    }

    public int factorEnd() {
        return factorEnd_;
    }

    public int rateBegin() {
        return rateBegin_;
    }

    public int rateEnd() {
        return rateEnd_;
    }

    public int stepBegin() {
        return stepBegin_;
    }

    public int stepEnd() {
        return stepEnd_;
    }
}
