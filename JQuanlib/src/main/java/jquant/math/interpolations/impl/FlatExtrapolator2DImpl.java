package jquant.math.interpolations.impl;

import jquant.math.Matrix;
import jquant.math.interpolations.Interpolation2D;

import java.util.List;

public class FlatExtrapolator2DImpl extends Interpolation2DImpl {
    private Interpolation2D decoratedInterp_;

    public FlatExtrapolator2DImpl(Interpolation2D decoratedInterpolation) {
        decoratedInterp_ = decoratedInterpolation;
        calculate();
    }

    @Override
    public void calculate() {

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
    public int locateX(double x) {
        return decoratedInterp_.locateX(x);
    }

    @Override
    public double yMin() {
        return decoratedInterp_.yMin();
    }

    @Override
    public double yMax() {
        return decoratedInterp_.yMax();
    }

    @Override
    public List<Double> yValues() {
        return decoratedInterp_.yValues();
    }

    @Override
    public int locateY(double y) {
        return decoratedInterp_.locateY(y);
    }

    @Override
    public Matrix zData() {
        return decoratedInterp_.zData();
    }

    @Override
    public boolean isInRange(double x, double y) {
        return decoratedInterp_.isInRange(x,y);
    }

    @Override
    public double value(double x, double y) {
        x = bindX(x);
        y = bindY(y);
        return decoratedInterp_.value(x, y, false);
    }

    public void update() {
        decoratedInterp_.update();
    }

    private double bindX(double x) {
        if (x < xMin())
            return xMin();
        if (x > xMax())
            return xMax();
        return x;
    }

    private double bindY(double y) {
        if (y < yMin())
            return yMin();
        if (y > yMax())
            return yMax();
        return y;
    }
}
