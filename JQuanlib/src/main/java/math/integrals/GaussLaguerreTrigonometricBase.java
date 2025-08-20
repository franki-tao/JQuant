package math.integrals;

import math.CommonUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class GaussLaguerreTrigonometricBase extends MomentBasedGaussianPolynomial {

    public GaussLaguerreTrigonometricBase(double u) {
        this.u_ = u;
        m_ = new ArrayList<>();
        f_ = new ArrayList<>();
    }

    private List<Double> m_;
    private List<Double> f_;

    protected double u_;

    protected abstract double m0();

    protected abstract double m1();

    protected double moment_(int n) {
        if (m_.size() <= n) {
            CommonUtil.resize(m_, n + 1, null);
        }
        if (m_.get(n) == null) {
            if (n == 0) {
                m_.set(0, m0());
            } else if (n == 1) {
                m_.set(1, m1());
            } else {
                m_.set(n, (2 * n * moment_(n - 1) - n * (n - 1) * moment_(n - 2)) / (1 + u_ * u_));
            }
        }
        return m_.get(n);
    }

    protected double fact(int n) {
        if (f_.size() <= n) {
            CommonUtil.resize(f_, n + 1, null);
        }
        if (f_.get(n) == null) {
            if (n == 0) {
                f_.set(0, 1d);
            } else {
                f_.set(n, n * fact(n - 1));
            }
        }
        return f_.get(n);
    }
}
