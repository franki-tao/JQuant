package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.ArrayFunc;

public class SimpleCostFunction <ValuesFn extends ArrayFunc> extends CostFunction{
    private ArrayFunc values_;

    public SimpleCostFunction(ArrayFunc values_) {
        this.values_ = values_;
    }

    @Override
    public Array values(Array x) {
        return values_.value(x);
    }
}
