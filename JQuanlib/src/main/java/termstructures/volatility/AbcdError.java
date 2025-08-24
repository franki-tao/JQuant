package termstructures.volatility;

import math.Array;
import math.optimization.CostFunction;

public class AbcdError extends CostFunction {
    public AbcdError(AbcdCalibration abcd_) {
        this.abcd_ = abcd_;
    }

    @Override
    public double value(Array x) {
        Array y = abcd_.transformation_.direct(x);
        abcd_.a_ = y.get(0);
        abcd_.b_ = y.get(1);
        abcd_.c_ = y.get(2);
        abcd_.d_ = y.get(3);
        return abcd_.error();
    }

    @Override
    public Array values(Array x) {
        Array y = abcd_.transformation_.direct(x);
        abcd_.a_ = y.get(0);
        abcd_.b_ = y.get(1);
        abcd_.c_ = y.get(2);
        abcd_.d_ = y.get(3);
        return abcd_.errors();
    }

    private AbcdCalibration abcd_;
}
