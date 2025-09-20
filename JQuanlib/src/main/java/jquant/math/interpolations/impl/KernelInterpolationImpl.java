package jquant.math.interpolations.impl;

import jquant.math.*;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;


public class KernelInterpolationImpl extends templateImpl {
    private int xSize_;
    private double invPrec_;
    private Matrix M_;
    private Array alphaVec_, yVec_;
    private Function kernel_;

    public KernelInterpolationImpl(double[] x,
                                   double[] y,
                                   Function kernel,
                                   final double epsilon) {
        super(x, y, 2);
        xSize_ = x.length;
        invPrec_ = epsilon;
        M_ = new Matrix(xSize_, xSize_, 0);
        alphaVec_ = new Array(xSize_);
        yVec_ = new Array(xSize_);
        kernel_ = kernel;
    }

    private double kernelAbs(double x1, double x2) {
        return kernel_.value(Math.abs(x1 - x2));
    }

    private double gammaFunc(double x) {

        double res = 0.0;

        for (int i = 0; i < xSize_; ++i) {
            res += kernelAbs(x, xValue[i]);
        }
        return res;
    }

    private void updateAlphaVec() {
        // Function calculates the alpha vector with given
        // fixed pillars+values

        // Write Matrix M
        double tmp = 0.0;

        for (int rowIt = 0; rowIt < xSize_; ++rowIt) {
            yVec_.set(rowIt, yValue[rowIt]);
            tmp = 1.0 / gammaFunc(xValue[rowIt]);

            for (int colIt = 0; colIt < xSize_; ++colIt) {
                M_.set(rowIt, colIt, kernelAbs(xValue[rowIt],
                        xValue[colIt]) * tmp);
//                M_[rowIt][colIt]=kernelAbs(this->xBegin_[rowIt],
//                        this->xBegin_[colIt])*tmp;
            }
        }

        // Solve y=M*\alpha for \alpha
        alphaVec_ = CommonUtil.qrSolve(M_, yVec_, true, new Array(0));

        // check if inversion worked up to a reasonable precision.
        // I've chosen not to check determinant(M_)!=0 before solving

        Array diffVec = CommonUtil.Abs(M_.mutiply(alphaVec_).subtract(yVec_));

        for (int i = 0; i < diffVec.size(); i++) {
            QL_REQUIRE(diffVec.get(i) < invPrec_, "Inversion failed in 1d kernel interpolation");
        }
    }

    @Override
    public void update() {
        updateAlphaVec();
    }

    @Override
    public double value(double x) {
        double res = 0.0;

        for (int i = 0; i < xSize_; ++i) {
            res += alphaVec_.get(i) * kernelAbs(x, xValue[i]);
        }

        return res / gammaFunc(x);
    }

    @Override
    public double primitive(double v) {
        QL_FAIL("Primitive calculation not implemented " + "for kernel interpolation");
        return 0;
    }

    @Override
    public double derivative(double v) {
        QL_FAIL("First derivative calculation not implemented for kernel interpolation");
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        QL_FAIL("Second derivative calculation not implemented " +
                "for kernel interpolation");
        return 0;
    }
}
