package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.Matrix;
import jquant.math.integrals.GaussKronrodAdaptive;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! %Libor market model parameterization
/*! Brigo, Damiano, Mercurio, Fabio, Morini, Massimo, 2003,
    Different Covariance  Parameterizations of the Libor Market Model
    and Joint Caps/Swaptions Calibration
  (<http://www.exoticderivatives.com/Files/Papers/brigomercuriomorini.pdf>)
*/
public abstract class LfmCovarianceParameterization {
    protected int size_;
    protected int factors_;

    private final class Var_Helper implements Function {
        private int i_, j_;
        private LfmCovarianceParameterization param_;

        public Var_Helper(final LfmCovarianceParameterization param, int i, int j) {
            i_ = i;
            j_ = j;
            param_ = param;
        }

        @Override
        public double value(double t) {
            final Matrix m = param_.diffusion(t, new Array(0));
            double res = 0d;
            for (int i = 0; i < m.cols(); i++) {
                res += m.get(i_, i) * m.get(i, j_);
            }
            return res;
        }
    }

    public LfmCovarianceParameterization(int size, int factors) {
        size_ = size;
        factors_ = factors;
    }

    public int size() {
        return size_;
    }

    public int factors() {
        return factors_;
    }

    public abstract Matrix diffusion(double t, final Array x);

    public Matrix covariance(double t, final Array x) {
        Matrix sigma = this.diffusion(t, x);
        return sigma.multipy(CommonUtil.transpose(sigma));
    }

    public Matrix integratedCovariance(double t, final Array x) {
        // this implementation is not intended for production.
        // because it is too slow and too inefficient.
        // This method is useful for testing and R&D.
        // Please overload the method within derived classes.
        QL_REQUIRE(x.empty(), "can not handle given x here");

        Matrix tmp = new Matrix(size_, size_, 0.0);

        for (int i = 0; i < size_; ++i) {
            for (int j = 0; j <= i; ++j) {
                Var_Helper helper = new Var_Helper(this, i, j);
                GaussKronrodAdaptive integrator = new GaussKronrodAdaptive(1e-10, 10000);
                for (int k = 0; k < 64; ++k) {
                    tmp.addEq(i, j, integrator.value(helper, k * t / 64., (k + 1) * t / 64.));
                }
                tmp.set(j, i, tmp.get(i, j));
            }
        }

        return tmp;
    }

}
