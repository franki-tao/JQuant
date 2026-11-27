package jquant.models.marketmodels.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/alphaformconcrete.hpp/.cpp} port,
 * class {@code AlphaFormInverseLinear}.
 */
public class AlphaFormInverseLinear implements AlphaForm {

    private final List<Double> times_;
    private double alpha_;

    public AlphaFormInverseLinear(List<Double> times) {
        this(times, 0.0);
    }

    public AlphaFormInverseLinear(List<Double> times, double alpha) {
        times_ = new ArrayList<>(times);
        alpha_ = alpha;
    }

    @Override
    public double value(int i) {
        return 1.0 / (1.0 + alpha_ * times_.get(i));
    }

    @Override
    public void setAlpha(double alpha) {
        alpha_ = alpha;
    }
}
