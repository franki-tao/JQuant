package math.optimization;

import math.Array;

import java.util.List;

public class ProjectedCostFunction extends CostFunction implements Projection{

    public ProjectedCostFunction(
                CostFunction costFunction,
                Array parameterValues,
                List<Boolean> fixParameters) {
        Projection(parameterValues, fixParameters);
        costFunction_ = costFunction;
    }

    @Override
    public double value(Array freeParameters) {
        mapFreeParameters(freeParameters);
        return costFunction_.value(actualParameters_.getT());
    }
    @Override
    public Array values(Array freeParameters) {
        mapFreeParameters(freeParameters);
        return costFunction_.values(actualParameters_.getT());
    }

    private CostFunction costFunction_;
}
