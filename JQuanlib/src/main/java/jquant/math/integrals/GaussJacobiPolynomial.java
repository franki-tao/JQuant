package jquant.math.integrals;

import jquant.math.distributions.GammaFunction;

import static jquant.math.CommonUtil.*;
import static jquant.math.MathUtils.close_enough;

public class GaussJacobiPolynomial extends GaussianOrthogonalPolynomial {
    private double alpha_;
    private double beta_;

    public GaussJacobiPolynomial(double alpha, double beta) {
        this.alpha_ = alpha;
        this.beta_ = beta;
        QL_REQUIRE(alpha_ + beta_ > -2.0, "alpha+beta must be bigger than -2");
        QL_REQUIRE(alpha_ > -1.0, "alpha must be bigger than -1");
        QL_REQUIRE(beta_ > -1.0, "beta  must be bigger than -1");
    }

    @Override
    public double alpha(int i) {
        double num = beta_ * beta_ - alpha_ * alpha_;
        double denom = (2.0 * i + alpha_ + beta_) * (2.0 * i + alpha_ + beta_ + 2);

        if (close_enough(denom, 0.0)) {
            if (!close_enough(num, 0.0)) {
                QL_FAIL("can't compute a_k for jacobi integration\n");
            } else {
                // l'Hospital
                num = 2 * beta_;
                denom = 2 * (2.0 * i + alpha_ + beta_ + 1);

                QL_ASSERT(!close_enough(denom, 0.0), "can't compute a_k for jacobi integration\n");
            }
        }

        return num / denom;
    }

    @Override
    public double beta(int i) {
        double num = 4.0 * i * (i + alpha_) * (i + beta_) * (i + alpha_ + beta_);
        double denom = (2.0 * i + alpha_ + beta_) * (2.0 * i + alpha_ + beta_)
                * ((2.0 * i + alpha_ + beta_) * (2.0 * i + alpha_ + beta_) - 1);

        if (close_enough(denom, 0.0)) {
            if (!close_enough(num, 0.0)) {
                QL_FAIL("can't compute b_k for jacobi integration\n");
            } else {
                // l'Hospital
                num = 4.0 * i * (i + beta_) * (2.0 * i + 2 * alpha_ + beta_);
                denom = 2.0 * (2.0 * i + alpha_ + beta_);
                denom *= denom - 1;
                QL_ASSERT(!close_enough(denom, 0.0), "can't compute b_k for jacobi integration\n");
            }
        }
        return num / denom;
    }

    @Override
    public double w(double x) {
        return Math.pow(1 - x, alpha_) * Math.pow(1 + x, beta_);
    }

    @Override
    public double mu_0() {
        return Math.pow(2.0, alpha_ + beta_ + 1)
                * Math.exp(new GammaFunction().logValue(alpha_ + 1)
                + new GammaFunction().logValue(beta_ + 1)
                - new GammaFunction().logValue(alpha_ + beta_ + 2));
    }
}
