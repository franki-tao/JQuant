package jquant.models.marketmodels.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/alphaformconcrete.hpp/.cpp} port,
 * class {@code AlphaFormLinearHyperbolic}.
 */
public class AlphaFormLinearHyperbolic implements AlphaForm {

    private final List<Double> times_;
    private double alpha_;

    public AlphaFormLinearHyperbolic(List<Double> times) {
        this(times, 0.0);
    }

    public AlphaFormLinearHyperbolic(List<Double> times, double alpha) {
        times_ = new ArrayList<>(times);
        alpha_ = alpha;
    }

    @Override
    public double value(int i) {
        double at = alpha_ * times_.get(i);
        double res = Math.atan(at) - 0.5 * Math.PI;
        res *= at;
        res += 1.0;
        res = Math.sqrt(res);
        return res;
    }

    @Override
    public void setAlpha(double alpha) {
        alpha_ = alpha;
    }
}
