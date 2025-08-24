package termstructures.volatility;

import math.Array;
import math.optimization.ParametersTransformation;

public class AbcdParametersTransformation implements ParametersTransformation {
    public AbcdParametersTransformation() {
        this.y_ = new Array(4);
    }

    @Override
    public Array direct(Array x) {
        y_.set(1, x.get(1));
        y_.set(2, Math.exp(x.get(2)));
        y_.set(3, Math.exp(x.get(3)));
        y_.set(0, Math.exp(x.get(0)) - y_.get(3));
        return y_;
    }

    @Override
    public Array inverse(Array x) {
        y_.set(1, x.get(1));
        y_.set(2, Math.log(x.get(2)));
        y_.set(3, Math.log(x.get(3)));
        y_.set(0, Math.log(x.get(0) + x.get(3)));
        return y_;
    }

    private Array y_;
}

