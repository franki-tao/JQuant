package jquant.math.interpolations.impl;

import jquant.math.Impl;
import jquant.math.Interpolation;

import java.util.List;

public class FlatExtrapolatorImpl extends Impl {
    private final Interpolation decoratedInterp_;

    public FlatExtrapolatorImpl(Interpolation decoratedInterpolation) {
        decoratedInterp_ = decoratedInterpolation;
        calculate();
    }

    private void calculate() {
        decoratedInterp_.update();
    }

    @Override
    public void update() {
        decoratedInterp_.update();
    }

    @Override
    public double xMin() {
        return decoratedInterp_.xMin();
    }

    @Override
    public double xMax() {
        return decoratedInterp_.xMax();
    }

    @Override
    public List<Double> xValues() {
        return decoratedInterp_.xValues();
    }

    @Override
    public List<Double> yValues() {
        return decoratedInterp_.yValues();
    }

    @Override
    public boolean isInRange(double x) {
        return decoratedInterp_.isInRange(x);
    }

    @Override
    public double value(double x) {
        return decoratedInterp_.value(bind(x), true);
    }

    @Override
    public double primitive(double x) {
        if (x < xMin())
            return decoratedInterp_.primitive(xMin(), true)
                    + decoratedInterp_.value(xMin(), true) * (x - xMin());
        if (x > xMax())
            return decoratedInterp_.primitive(xMax(), true)
                    + decoratedInterp_.value(xMax(), true) * (x - xMax());
        return decoratedInterp_.primitive(x, true);
    }

    @Override
    public double derivative(double x) {
        if (x < xMin() || x > xMax())
            return 0.0;
        return decoratedInterp_.derivative(x, true);
    }

    @Override
    public double secondDerivative(double x) {
        if (x < xMin() || x > xMax())
            return 0.0;
        return decoratedInterp_.secondDerivative(x, true);
    }

    private double bind(double x) {
        return Math.max(xMin(), Math.min(x, xMax()));
    }
}
