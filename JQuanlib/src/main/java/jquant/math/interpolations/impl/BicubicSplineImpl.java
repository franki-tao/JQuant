package jquant.math.interpolations.impl;

import jquant.math.CommonUtil;
import jquant.math.Interpolation;
import jquant.math.interpolations.CubicInterpolation;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class BicubicSplineImpl extends Interpolation2DTemplateImpl implements BicubicSplineDerivatives {
    private List<Interpolation> splines_ = new ArrayList<>();

    public BicubicSplineImpl(double[] x, double[] y, double[][] z) {
        super(x, y, z);
        calculate();
    }

    @Override
    public double derivativeX(double x, double y) {
        List<Double> section = CommonUtil.ArrayInit(zData_[0].length);
        for (int i = 0; i < section.size(); ++i) {
            section.set(i, value(yValues[i], y));
            // section[i] = value(this->xBegin_[i], y);
        }

        return new CubicInterpolation(
                xValues,
                CommonUtil.toArray(section),
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0).derivative(x, false);
    }

    @Override
    public double derivativeY(double x, double y) {
        List<Double> section = CommonUtil.ArrayInit(splines_.size());
        for (int i = 0; i < splines_.size(); i++) {
            section.set(i, splines_.get(i).value(x, true));
            // section[i]=splines_[i](x,true);
        }
        return new CubicInterpolation(
                yValues,
                CommonUtil.toArray(section),
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0).derivative(y, false);
    }

    @Override
    public double derivativeXY(double x, double y) {
        List<Double> section = CommonUtil.ArrayInit(zData_[0].length);
        for (int i = 0; i < section.size(); ++i) {
            section.set(i, derivativeY(xValues[i], y));
            // section[i] = derivativeY(this->xBegin_[i], y);
        }
        return new CubicInterpolation(
                yValues,
                CommonUtil.toArray(section),
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0).derivative(x, false);
    }

    @Override
    public double secondDerivativeX(double x, double y) {
        List<Double> section = CommonUtil.ArrayInit(zData_[0].length);
        for (int i = 0; i < section.size(); ++i) {
            section.set(i, value(xValues[i], y));
            //section[i] = value(this->xBegin_[i], y);
        }
        return new CubicInterpolation(
                xValues,
                CommonUtil.toArray(section),
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0).secondDerivative(x, false);
    }

    @Override
    public double secondDerivativeY(double x, double y) {
        List<Double> section = CommonUtil.ArrayInit(splines_.size());
        for (int i = 0; i < splines_.size(); i++) {
            section.set(i, splines_.get(i).value(x, true));
            // section[i]=splines_[i](x,true);
        }
        return new CubicInterpolation(
                yValues,
                CommonUtil.toArray(section),
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0).secondDerivative(y, false);
    }

    @Override
    public void calculate() {
        for (double[] zd : zData_) {
            splines_.add(new CubicInterpolation(xValues, zd, Spline, false,
                    SecondDerivative, 0.0,
                    SecondDerivative, 0.0));
        }
    }

    @Override
    public double value(double x, double y) {
        List<Double> section = CommonUtil.ArrayInit(splines_.size());
        for (int i = 0; i < splines_.size(); i++) {
            section.set(i, splines_.get(i).value(x, true));
            //section[i]=splines_[i](x,true);
        }
        CubicInterpolation spline = new CubicInterpolation(yValues,
                CommonUtil.toArray(section),
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
        return spline.value(y, true);
    }
}
