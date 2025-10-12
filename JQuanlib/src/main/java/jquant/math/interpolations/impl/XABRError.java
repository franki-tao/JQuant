package jquant.math.interpolations.impl;

import jquant.math.Array;
import jquant.math.optimization.CostFunction;

public class XABRError extends CostFunction {

    private XABRInterpolationImpl xabr_;

    public XABRError(XABRInterpolationImpl xabr) {
        this.xabr_ = xabr;
    }



    @Override
    public Array values(Array x) {
        final Array y = xabr_.modelTmp.direct(x, xabr_.paramIsFixed_,
                xabr_.params_, xabr_.forward_);
        for (int i = 0; i < xabr_.params_.size(); ++i)
            xabr_.params_.set(i, y.get(i));
        xabr_.updateModelInstance();
        return xabr_.interpolationErrors();
    }

    @Override
    public double value(Array x) {
        final Array y = xabr_.modelTmp.direct(x, xabr_.paramIsFixed_,
                xabr_.params_, xabr_.forward_);
        for (int i = 0; i < xabr_.params_.size(); ++i)
            xabr_.params_.set(i, y.get(i));
        xabr_.updateModelInstance();
        return xabr_.interpolationSquaredError();
    }
}
