package jquant.math.interpolations.impl;

import jquant.math.Matrix;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

public abstract class Interpolation2DTemplateImpl extends Interpolation2DImpl {
    protected double[] xValues;
    protected double[] yValues;
    protected double[][] zData_;

    public Interpolation2DTemplateImpl(double[] x, double[] y, double[][] z) {
        xValues = x;
        yValues = y;
        zData_ = z;
        QL_REQUIRE(x.length >= 2,
                "not enough x points to interpolate: at least 2 " +
                        "required, " + x.length + " provided");
        QL_REQUIRE(y.length >= 2,
                "not enough y points to interpolate: at least 2 " +
                        "required, " + y.length + " provided");
    }

    @Override
    public double xMin() {
        return xValues[0];
    }

    @Override
    public double xMax() {
        return xValues[xValues.length - 1];
    }

    @Override
    public List<Double> xValues() {
        List<Double> res = new ArrayList<>();
        for (double xValue : xValues) {
            res.add(xValue);
        }
        return res;
    }

    @Override
    public int locateX(double x) {
        if (x < xValues[0]) {
            return 0;
        } else if (x > xValues[xValues.length - 1]) {
            return xValues.length - 2;
        } else {
            for (int i = 0; i < xValues.length - 1; i++) {
                if (x > xValues[i] && x <= xValues[i + 1]) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public double yMin() {
        return yValues[0];
    }

    @Override
    public double yMax() {
        return yValues[yValues.length - 1];
    }

    @Override
    public List<Double> yValues() {
        List<Double> res = new ArrayList<>();
        for (double xValue : yValues) {
            res.add(xValue);
        }
        return res;
    }

    @Override
    public int locateY(double y) {
        if (y < yValues[0]) {
            return 0;
        } else if (y > yValues[yValues.length - 1]) {
            return yValues.length - 2;
        } else {
            for (int i = 0; i < yValues.length - 1; i++) {
                if (y > yValues[i] && y <= yValues[i + 1]) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public Matrix zData() {
        return new Matrix(zData_);
    }

    @Override
    public boolean isInRange(double x, double y) {
        double x1 = xMin(), x2 = xMax();
        boolean xIsInrange = (x >= x1 && x <= x2) || close(x, x1) || close(x, x2);
        if (!xIsInrange)
            return false;

        double y1 = yMin(), y2 = yMax();
        return (y >= y1 && y <= y2) || close(y, y1) || close(y, y2);
    }

    @Override
    public double value(double x, double y) {
        return 0;
    }
}
