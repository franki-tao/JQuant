package jquant.math.integrals;

import jquant.math.CommonUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class MomentBasedGaussianPolynomial extends GaussianOrthogonalPolynomial {
    private List<Double> b_;
    private List<Double> c_;

    private List<List<Double>> z_;

    public MomentBasedGaussianPolynomial() {
        z_ = new ArrayList<>();
        z_.add(new ArrayList<>());
        b_ = new ArrayList<>();
        c_ = new ArrayList<>();
    }

    public abstract double moment(int i);

    private double alpha_(int u) {
        if (b_.size() <= u) {
            b_ = CommonUtil.resize(b_, u + 1, null);
        }

        if (b_.get(u) == null) {
            if (u == 0) {
                b_.set(u, moment(1));
            } else {
                final int iu = u;
                final double tmp =
                        -z(iu - 1, iu) / z(iu - 1, iu - 1) + z(iu, iu + 1) / z(iu, iu);
                b_.set(u, tmp);
            }
        }
        return b_.get(u);
    }

    private double beta_(int u) {
        if (u == 0)
            return 1.0;

        if (c_.size() <= u) {
            c_ = CommonUtil.resize(c_, u + 1, null);
        }
        if (c_.get(u) == null) {
            final int iu = u;
            final double tmp = z(iu, iu) / z(iu - 1, iu - 1);
            c_.set(u, tmp);
        }
        return c_.get(u);
    }

    private double z(int k, int i) {
        if (k == -1) {
            return 0.0;
        }

        final int rows = z_.size();
        final int cols = z_.get(0).size();

        if (cols <= i) {
            for (int l = 0; l < rows; ++l) {
                z_.set(l, CommonUtil.resize(z_.get(l), i + 1, null));
            }
        }
        if (rows <= k) {
            z_ = CommonUtil.resize(z_, k + 1, CommonUtil.ArrayInit(z_.get(0).size()));
        }

        if (z_.get(k).get(i) == null) {
            if (k == 0) {
                z_.get(k).set(i, moment(i));
            } else {
                final double tmp = z(k - 1, i + 1)
                        - alpha_(k - 1) * z(k - 1, i) - beta_(k - 1) * z(k - 2, i);
                z_.get(k).set(i, tmp);
            }
        }

        return z_.get(k).get(i);
    }

    @Override
    public double alpha(int i) {
        return alpha_(i);
    }

    @Override
    public double beta(int i) {
        return beta_(i);
    }

    @Override
    public double mu_0() {
        return moment(0);
    }
}
