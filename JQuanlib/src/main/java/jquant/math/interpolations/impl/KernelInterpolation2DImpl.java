package jquant.math.interpolations.impl;

import jquant.math.Array;
import jquant.math.Function;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.*;

/*
  Grid Explanation:

  Grid=[  (x1,y1) (x1,y2) (x1,y3)... (x1,yM);
          (x2,y1) (x2,y2) (x2,y3)... (x2,yM);
          .
          .
          .
          (xN,y1) (xN,y2) (xN,y3)... (xN,yM);
       ]

  The Passed variables are:
  - x which is N dimensional
  - y which is M dimensional
  - zData which is NxM dimensional and has the z values
    corresponding to the grid above.
  - kernel is a template which needs a Real operator()(Real x) implementation
*/
public class KernelInterpolation2DImpl extends Interpolation2DTemplateImpl {
    private int xSize_, ySize_, xySize_;
    private double invPrec_ = 1.0e-10;
    private Array alphaVec_, yVec_;
    private Matrix M_;
    private Function kernel_;

    public KernelInterpolation2DImpl(double[] x, double[] y, double[][] z, Function kernel) {
        super(x, y, z);
        xSize_ = x.length;
        ySize_ = y.length;
        xySize_ = xSize_ * ySize_;
        alphaVec_ = new Array(xySize_);
        yVec_ = new Array(xySize_);
        M_ = new Matrix(xySize_, xySize_, 0);
        kernel_ = kernel;
        QL_REQUIRE(z.length == xSize_,
                "Z value matrix has wrong number of rows");
        QL_REQUIRE(z[0].length == ySize_,
                "Z value matrix has wrong number of columns");
    }

    @Override
    public void calculate() {

    }

    @Override
    public double value(double x1, double x2) {
        double res = 0.0;

        Array X = new Array(2);
        Array Xn = new Array(2);
        X.set(0, x1);
        X.set(1, x2);
        int cnt = 0; // counter

        for (int j = 0; j < ySize_; ++j) {
            for (int i = 0; i < xSize_; ++i) {
                Xn.set(0, xValues[i]);
                Xn.set(1, yValues[j]);
                res += alphaVec_.get(cnt) * kernelAbs(X, Xn);
                cnt++;
            }
        }
        return res / gammaFunc(X);
    }

    // the calculation will solve y=M*a for a.  Due to
    // singularity or rounding errors the recalculation
    // M*a may not give y. Here, a failure will be thrown if
    // |M*a-y|>=invPrec_
    public void setInverseResultPrecision(double invPrec) {
        invPrec_ = invPrec;
    }

    // returns K(||X-Y||) where X,Y are vectors
    private double kernelAbs(final Array X, final Array Y) {
        return kernel_.value(Norm2(X.subtract(Y)));
    }

    private double gammaFunc(Array X) {
        double res = 0.0;
        Array Xn = new Array(X.size());
        for (int j = 0; j < ySize_; ++j) {
            for (int i = 0; i < xSize_; ++i) {
                Xn.set(0, xValues[i]);
                Xn.set(1, yValues[j]);
                res += kernelAbs(X, Xn);
            }
        }
        return res;
    }

    private void updateAlphaVec() {
        // Function calculates the alpha vector with given
        // fixed pillars+values

        Array Xk = new Array(2);
        Array Xn = new Array(2);

        int rowCnt = 0, colCnt = 0;
        double tmpVar = 0.0;

        // write y-vector and M-Matrix
        for (int j = 0; j < ySize_; ++j) {
            for (int i = 0; i < xSize_; ++i) {
                yVec_.set(rowCnt, zData_[i][j]);
                // calculate X_k
                Xk.set(0, xValues[i]);
                Xk.set(1, yValues[j]);

                tmpVar = 1 / gammaFunc(Xk);
                colCnt = 0;

                for (int jM = 0; jM < ySize_; ++jM) {
                    for (int iM = 0; iM < xSize_; ++iM) {
                        Xn.set(0, xValues[iM]);
                        Xn.set(1, yValues[jM]);
                        M_.set(rowCnt, colCnt, kernelAbs(Xk, Xn) * tmpVar);
                        colCnt++; // increase column counter
                    }// end iM
                }// end jM
                rowCnt++; // increase row counter
            } // end i
        }// end j

        alphaVec_ = qrSolve(M_, yVec_, true, new Array(0));

        // check if inversion worked up to a reasonable precision.
        // I've chosen not to check determinant(M_)!=0 before solving
        Array diffVec = Abs(M_.mutiply(alphaVec_).subtract(yVec_));
        for (int i = 0; i < diffVec.size(); i++) {
            QL_REQUIRE(diffVec.get(i) < invPrec_, "inversion failed in 2d kernel interpolation");
        }
    }
}
