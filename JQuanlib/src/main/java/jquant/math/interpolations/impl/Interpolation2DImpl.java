package jquant.math.interpolations.impl;

import jquant.math.Matrix;

import java.util.List;

public abstract class Interpolation2DImpl {
    public abstract void calculate();
    public abstract double xMin();
    public abstract double xMax();
    public abstract List<Double> xValues();
    public abstract int locateX(double x);
    public abstract double yMin();
    public abstract double yMax();
    public abstract List<Double> yValues();
    public abstract int locateY(double y);
    public abstract Matrix zData();
    public abstract boolean isInRange(double x, double y);
    public abstract double value(double x, double y);
}
