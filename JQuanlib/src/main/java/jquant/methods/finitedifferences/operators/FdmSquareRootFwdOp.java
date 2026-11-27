package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Forward Fokker-Planck operator for the square root process.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/fdmsquarerootfwdop.{hpp,cpp}}.
 */
public class FdmSquareRootFwdOp implements FdmLinearOpComposite {

    public enum TransformationType { Plain, Power, Log }

    private final int direction_;
    private final double kappa_, theta_, sigma_;
    private final TransformationType transform_;
    private final ModTripleBandLinearOp mapX_;
    private final double[] v_;

    public FdmSquareRootFwdOp(final FdmMesher mesher,
                              double kappa, double theta, double sigma,
                              int direction) {
        this(mesher, kappa, theta, sigma, direction, TransformationType.Plain);
    }

    public FdmSquareRootFwdOp(final FdmMesher mesher,
                              double kappa, double theta, double sigma,
                              int direction, TransformationType transform) {
        direction_ = direction;
        kappa_ = kappa;
        theta_ = theta;
        sigma_ = sigma;
        transform_ = transform;

        final Array locations = mesher.locations(direction_);
        final int size = mesher.layout().size();

        if (transform == TransformationType.Plain) {
            final Array drift = new Array(size);
            for (int i = 0; i < size; ++i)
                drift.set(i, kappa * (locations.get(i) - theta) + sigma * sigma);

            final Array diffusion = new Array(size);
            for (int i = 0; i < size; ++i)
                diffusion.set(i, 0.5 * sigma * sigma * locations.get(i));

            mapX_ = new ModTripleBandLinearOp(
                    new FirstDerivativeOp(direction_, mesher).mult(drift)
                            .add(new SecondDerivativeOp(direction_, mesher).mult(diffusion))
                            .add(new Array(size, kappa)));
        } else if (transform == TransformationType.Power) {
            final Array diffusion = new Array(size);
            final Array drift = new Array(size);
            for (int i = 0; i < size; ++i) {
                diffusion.set(i, 0.5 * sigma * sigma * locations.get(i));
                drift.set(i, kappa * (locations.get(i) + theta));
            }

            mapX_ = new ModTripleBandLinearOp(
                    new SecondDerivativeOp(direction_, mesher).mult(diffusion)
                            .add(new FirstDerivativeOp(direction_, mesher).mult(drift))
                            .add(new Array(size,
                                    2.0 * kappa * kappa * theta / (sigma * sigma))));
        } else {
            final Array drift = new Array(size);
            final Array diffusion = new Array(size);
            final Array diagonal = new Array(size);
            for (int i = 0; i < size; ++i) {
                final double e = Math.exp(-locations.get(i));
                drift.set(i, e * (-0.5 * sigma * sigma - kappa * theta) + kappa);
                diffusion.set(i, 0.5 * sigma * sigma * e);
                diagonal.set(i, kappa * theta * e);
            }

            mapX_ = new ModTripleBandLinearOp(
                    new FirstDerivativeOp(direction_, mesher).mult(drift)
                            .add(new SecondDerivativeOp(direction_, mesher).mult(diffusion))
                            .add(diagonal));
        }

        v_ = new double[mesher.layout().dim()[direction_]];
        for (FdmLinearOpIterator iter : mesher.layout()) {
            final double v = mesher.location(iter, direction_);
            v_[iter.coordinates()[direction_]] = v;
        }

        // zero flux boundary condition
        setLowerBC(mesher);
        setUpperBC(mesher);
    }

    private void setLowerBC(final FdmMesher mesher) {
        final int n = 1;
        final double[] coeff = getCoeff(n);
        final double f = lowerBoundaryFactor(transform_);

        final double b = -(h(n - 1) + h(n)) / zeta(n);
        final double c = h(n - 1) / zetap(n);

        for (FdmLinearOpIterator iter : mesher.layout()) {
            if (iter.coordinates()[direction_] == 0) {
                final int idx = iter.index();
                mapX_.setDiag(idx, coeff[1] + f * b);
                mapX_.setUpper(idx, coeff[2] + f * c);
            }
        }
    }

    private void setUpperBC(final FdmMesher mesher) {
        final int n = v_.length;
        final double[] coeff = getCoeff(n);
        final double f = upperBoundaryFactor(transform_);

        final double b = (h(n) + h(n - 1)) / zeta(n);
        final double c = -h(n) / zetam(n);

        for (FdmLinearOpIterator iter : mesher.layout()) {
            if (iter.coordinates()[direction_] == n - 1) {
                final int idx = iter.index();
                mapX_.setDiag(idx, coeff[1] + f * b);
                mapX_.setLower(idx, coeff[0] + f * c);
            }
        }
    }

    public double lowerBoundaryFactor(TransformationType transform) {
        if (transform == TransformationType.Plain)
            return f0Plain();
        else if (transform == TransformationType.Power)
            return f0Power();
        else if (transform == TransformationType.Log)
            return f0Log();
        else
            throw new IllegalArgumentException("unknown transform");
    }

    public double upperBoundaryFactor(TransformationType transform) {
        if (transform == TransformationType.Plain)
            return f1Plain();
        else if (transform == TransformationType.Power)
            return f1Power();
        else if (transform == TransformationType.Log)
            return f1Log();
        else
            throw new IllegalArgumentException("unknown transform");
    }

    private double f0Plain() {
        final int n = 1;
        final double a = -(2 * h(n - 1) + h(n)) / zetam(n);
        final double alpha = sigma_ * sigma_ * v(n) / zetam(n)
                - mu(n) * h(n) / zetam(n);
        final double nu = a * v(n - 1)
                + (2 * kappa_ * (v(n - 1) - theta_) + sigma_ * sigma_)
                / (sigma_ * sigma_);
        return alpha / nu * v(n - 1);
    }

    private double f1Plain() {
        final int n = v_.length;
        final double a = (2 * h(n) + h(n - 1)) / zetap(n);
        final double gamma = sigma_ * sigma_ * v(n) / zetap(n)
                + mu(n) * h(n - 1) / zetap(n);
        final double nu = a * v(n + 1)
                + (2 * kappa_ * (v(n + 1) - theta_) + sigma_ * sigma_)
                / (sigma_ * sigma_);
        return gamma / nu * v(n + 1);
    }

    private double f0Power() {
        final int n = 1;
        final double mu = kappa_ * (v(n) + theta_);
        final double a = -(2 * h(n - 1) + h(n)) / zetam(n);
        final double alpha = sigma_ * sigma_ * v(n) / zetam(n)
                - mu * h(n) / zetam(n);
        final double nu = a * v(n - 1)
                + 2 * (kappa_ * v(n - 1) / (sigma_ * sigma_));
        return alpha / nu * v(n - 1);
    }

    private double f1Power() {
        final int n = v_.length;
        final double mu = kappa_ * (v(n) + theta_);
        final double a = (2 * h(n) + h(n - 1)) / zetap(n);
        final double gamma = sigma_ * sigma_ * v(n) / zetap(n)
                + mu * h(n - 1) / zetap(n);
        final double nu = a * v(n + 1)
                + 2 * (kappa_ * v(n + 1) / (sigma_ * sigma_));
        return gamma / nu * v(n + 1);
    }

    private double f0Log() {
        final int n = 1;
        final double mu = ((-kappa_ * theta_ - sigma_ * sigma_ / 2.0) * Math.exp(-v(1)) + kappa_);
        final double a = -(2 * h(n - 1) + h(n)) / zetam(n);
        final double alpha = sigma_ * sigma_ * Math.exp(-v(n)) / zetam(n)
                - mu * h(n) / zetam(n);
        final double nu = a * Math.exp(-v(n - 1))
                + 2 * kappa_ * (1 - theta_ * Math.exp(-v(n - 1)))
                / (sigma_ * sigma_);
        return alpha / nu * Math.exp(-v(n - 1));
    }

    private double f1Log() {
        final int n = v_.length;
        final double mu = ((-kappa_ * theta_ - sigma_ * sigma_ / 2.0) * Math.exp(-v(n)) + kappa_);
        final double a = (2 * h(n) + h(n - 1)) / zetap(n);
        final double gamma = sigma_ * sigma_ * Math.exp(-v(n)) / zetap(n)
                + mu * h(n - 1) / zetap(n);
        final double nu = a * Math.exp(-v(n + 1))
                + 2 * kappa_ * (1 - theta_ * Math.exp(-v(n + 1)))
                / (sigma_ * sigma_);
        return gamma / nu * Math.exp(-v(n + 1));
    }

    public double v(int i) {
        if (i > 0 && i <= v_.length) {
            return v_[i - 1];
        } else if (i == 0) {
            if (transform_ == TransformationType.Log) {
                return 2 * v_[0] - v_[1];
            } else {
                return Math.max(0.5 * v_[0], v_[0] - 0.01 * (v_[1] - v_[0]));
            }
        } else if (i == v_.length + 1) {
            return v_[v_.length - 1] + (v_[v_.length - 1] - v_[v_.length - 2]);
        } else {
            QL_FAIL("unknown index");
            return Double.NaN;
        }
    }

    private double h(int i) {
        return v(i + 1) - v(i);
    }

    private double mu(int i) {
        return kappa_ * (v(i) - theta_) + sigma_ * sigma_;
    }

    private double zetam(int i) {
        return h(i - 1) * (h(i - 1) + h(i));
    }

    private double zeta(int i) {
        return h(i - 1) * h(i);
    }

    private double zetap(int i) {
        return h(i) * (h(i - 1) + h(i));
    }

    private double[] getCoeff(int n) {
        if (transform_ == TransformationType.Plain)
            return getCoeffPlain(n);
        else if (transform_ == TransformationType.Power)
            return getCoeffPower(n);
        else if (transform_ == TransformationType.Log)
            return getCoeffLog(n);
        else
            throw new IllegalArgumentException("unknown transformation type");
    }

    private double[] getCoeffPlain(int n) {
        final double alpha = sigma_ * sigma_ * v(n) / zetam(n)
                - mu(n) * h(n) / zetam(n);
        final double beta = -sigma_ * sigma_ * v(n) / zeta(n)
                + mu(n) * (h(n) - h(n - 1)) / zeta(n) + kappa_;
        final double gamma = sigma_ * sigma_ * v(n) / zetap(n)
                + mu(n) * h(n - 1) / zetap(n);
        return new double[]{alpha, beta, gamma};
    }

    private double[] getCoeffLog(int n) {
        final double mu = ((-kappa_ * theta_ - sigma_ * sigma_ / 2.0) * Math.exp(-v(n)) + kappa_);
        final double alpha = sigma_ * sigma_ * Math.exp(-v(n)) / zetam(n)
                - mu * h(n) / zetam(n);
        final double beta = -sigma_ * sigma_ * Math.exp(-v(n)) / zeta(n)
                + mu * (h(n) - h(n - 1)) / zeta(n)
                + kappa_ * theta_ * Math.exp(-v(n));
        final double gamma = sigma_ * sigma_ * Math.exp(-v(n)) / zetap(n)
                + mu * h(n - 1) / zetap(n);
        return new double[]{alpha, beta, gamma};
    }

    private double[] getCoeffPower(int n) {
        final double mu = kappa_ * (theta_ + v(n));
        final double alpha = (sigma_ * sigma_ * v(n) - mu * h(n)) / zetam(n);
        final double beta = (-sigma_ * sigma_ * v(n) + mu * (h(n) - h(n - 1))) / zeta(n)
                + 2 * kappa_ * kappa_ * theta_ / (sigma_ * sigma_);
        final double gamma = (sigma_ * sigma_ * v(n) + mu * h(n - 1)) / zetap(n);
        return new double[]{alpha, beta, gamma};
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void setTime(double t1, double t2) {
    }

    @Override
    public Array apply(Array p) {
        return mapX_.apply(p);
    }

    @Override
    public Array apply_mixed(Array r) {
        return new Array(r.size(), 0.0);
    }

    @Override
    public Array apply_direction(int direction, Array r) {
        if (direction == direction_) {
            return mapX_.apply(r);
        } else {
            return new Array(r.size(), 0.0);
        }
    }

    @Override
    public Array solve_splitting(int direction, Array r, double dt) {
        if (direction == direction_) {
            return mapX_.solve_splitting(r, dt, 1.0);
        } else {
            return r;
        }
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(direction_, r, dt);
    }
}
