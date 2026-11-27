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
            // Official std::inner_product over the i_-th and j_-th rows of
            // the diffusion matrix.
            double res = 0d;
            for (int q = 0; q < m.cols(); q++) {
                res += m.get(i_, q) * m.get(j_, q);
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

    /** Official default argument {@code x = {}}: empty state array. */
    public Matrix diffusion(double t) {
        return diffusion(t, new Array(0));
    }

    public Matrix covariance(double t, final Array x) {
        Matrix sigma = this.diffusion(t, x);
        return sigma.multipy(CommonUtil.transpose(sigma));
    }

    /** Official default argument {@code x = {}}: empty state array. */
    public Matrix covariance(double t) {
        return covariance(t, new Array(0));
    }

    public Matrix integratedCovariance(double t, final Array x) {
        QL_REQUIRE(x.empty(), "can not handle given x here");
        return integratedCovarianceBase(t);
    }

    /** Official default argument {@code x = {}}: empty state array. */
    public Matrix integratedCovariance(double t) {
        return integratedCovariance(t, new Array(0));
    }

    /**
     * Official base-class numerical integration
     * ({@code LfmCovarianceParameterization::integratedCovariance}).
     *
     * <p>In C++ the test can explicitly qualify the base implementation as
     * {@code param->LfmCovarianceParameterization::integratedCovariance(t)}
     * even when {@code integratedCovariance} is overridden. Java has no
     * equivalent qualified-call syntax, so the official numeric Gauss-Kronrod
     * implementation is exposed here for the test to call directly.
     */
    public Matrix integratedCovarianceBase(double t) {
        // this implementation is not intended for production.
        // because it is too slow and too inefficient.
        // This method is useful for testing and R&D.
        // Please overload the method within derived classes.

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
