package jquant.math.ode;

import jquant.math.CommonUtil;

import java.util.List;

public class OdeFctWrapper implements OdeFct{
    public OdeFct1d ode1d_;

    public OdeFctWrapper(OdeFct1d ode1d) {
        ode1d_ = ode1d;
    }
    @Override
    public List<Double> value(double x, List<Double> y) {
        return CommonUtil.ArrayInit(1, ode1d_.value(x, y.get(0)));
    }
}
