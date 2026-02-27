package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.Function;
import jquant.math.Matrix;
import jquant.math.integrals.GaussKronrodAdaptive;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! proxy for a libor forward model covariance parameterization
public class LfmCovarianceProxy extends LfmCovarianceParameterization {
    private final class Var_Helper implements Function {
        private int i_, j_;
        private LmVolatilityModel volaModel_;
        private LmCorrelationModel corrModel_;

        public Var_Helper(final LfmCovarianceProxy proxy, int i, int j) {
            i_ = i;
            j_ = j;
            volaModel_ = proxy.volaModel_;
            corrModel_ = proxy.corrModel_;
        }

        @Override
        public double value(double t) {
            double v1, v2;
            if (i_ == j_) {
                v1 = v2 = volaModel_.volatility(i_, t, new Array(0));
            } else {
                v1 = volaModel_.volatility(i_, t, new Array(0));
                v2 = volaModel_.volatility(j_, t, new Array(0));
            }

            return v1 * corrModel_.correlation(i_, j_, t, new Array(0)) * v2;
        }
    }

    protected LmVolatilityModel volaModel_;
    protected LmCorrelationModel corrModel_;

    public LfmCovarianceProxy(LmVolatilityModel volaModel, LmCorrelationModel corrModel) {
        super(corrModel.size(), corrModel.factors());
        volaModel_ = volaModel;
        corrModel_ = corrModel;
        QL_REQUIRE(volaModel_.size() == corrModel_.size(),
                "different size for the volatility (" + volaModel_.size() +
                        ") and correlation (" + corrModel_.size() +
                        ") models");
    }

    public LmVolatilityModel volatilityModel() {
        return volaModel_;
    }

    public LmCorrelationModel correlationModel() {
        return corrModel_;
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        Matrix pca = corrModel_.pseudoSqrt(t, x);
        Array vol = volaModel_.volatility(t, x);
        for (int i = 0; i < size_; ++i) {
            for (int j = 0; j < pca.cols(); j++) {
                pca.set(i, j, pca.get(i, j) * vol.get(i));
            }
        }
        return pca;
    }

    @Override
    public Matrix covariance(double t, final Array x) {
        Array volatility = volaModel_.volatility(t, x);
        Matrix correlation = corrModel_.correlation(t, x);

        Matrix tmp = new Matrix(size_, size_);
        for (int i = 0; i < size_; ++i) {
            for (int j = 0; j < size_; ++j) {
                tmp.set(i, j, volatility.get(i) * correlation.get(i, j) * volatility.get(j));
            }
        }

        return tmp;
    }

    public double integratedCovariance(int i, int j, double t, final Array x) {
        if (corrModel_.isTimeIndependent()) {
            try {
                // if all objects support these methods
                // thats by far the fastest way to get the
                // integrated covariance
                return corrModel_.correlation(i, j, 0.0, x)
                        * volaModel_.integratedVariance(j, i, t, x);
            } catch (Exception e) {
                // okay proceed with the
                // slow numerical integration routine
            }
        }

        QL_REQUIRE(x.empty(), "can not handle given x here");

        double tmp = 0.0;
        Var_Helper helper = new Var_Helper(this, i, j);

        GaussKronrodAdaptive integrator = new GaussKronrodAdaptive(1e-10, 10000);
        for (int k = 0; k < 64; ++k) {
            tmp += integrator.value(helper, k * t / 64., (k + 1) * t / 64.);
        }
        return tmp;
    }
}
