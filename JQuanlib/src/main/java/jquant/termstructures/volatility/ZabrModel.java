package jquant.termstructures.volatility;

import jquant.math.Array;
import jquant.math.Point;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.interpolations.BicubicSpline;
import jquant.math.interpolations.CubicInterpolation;
import jquant.math.ode.AdaptiveRungeKutta;
import jquant.methods.finitedifferences.meshers.Concentrating1dMesher;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.Glued1dMesher;
import jquant.methods.finitedifferences.operators.FdmDupire1dOp;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;
import jquant.methods.finitedifferences.operators.FdmZabrOp;
import jquant.methods.finitedifferences.solvers.FdmBackwardSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;
import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;
import static jquant.termstructures.volatility.Sarb.validateSabrParameters;

/**
 * ZABR model functions.
 *
 * <p>Ported from QuantLib {@code ql/termstructures/volatility/zabr.{hpp,cpp}}.
 * Reference: Andreasen, Huge: ZABR - Expansions for the masses, Preliminary
 * Version, December 2011, http://ssrn.com/abstract=1980726
 */
public class ZabrModel {

    private final double expiryTime_, forward_;
    // nu_ here is a transformed version of the input nu
    private final double alpha_, beta_, nu_, rho_, gamma_;

    public ZabrModel(double expiryTime,
                     double forward,
                     double alpha,
                     double beta,
                     double nu,
                     double rho,
                     double gamma) {
        this.expiryTime_ = expiryTime;
        this.forward_ = forward;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu * Math.pow(alpha, 1.0 - gamma);
        this.rho_ = rho;
        this.gamma_ = gamma;

        validateSabrParameters(alpha, beta, nu, rho);
        QL_REQUIRE(gamma >= 0.0 /*&& gamma<=1.0*/,
                "gamma must be non negative: " + gamma + " not allowed");
        QL_REQUIRE(forward >= 0.0,
                "forward must be non negative: " + forward + " not allowed");
        QL_REQUIRE(expiryTime > 0.0,
                "expiry time must be positive: " + expiryTime + " not allowed");
    }

    public double forward() {
        return forward_;
    }

    public double expiryTime() {
        return expiryTime_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double nu() {
        return nu_;
    }

    public double rho() {
        return rho_;
    }

    public double gamma() {
        return gamma_;
    }

    public double localVolatility(double f) {
        return localVolatility(new double[]{f})[0];
    }

    public double[] localVolatility(double[] f) {
        double[] x_ = x(f);
        double[] result = new double[f.length];
        for (int i = 0; i < f.length; ++i) {
            result[i] = localVolatilityHelper(f[i], x_[i]);
        }
        return result;
    }

    public double fdPrice(double strike) {
        return fdPrice(new double[]{strike})[0];
    }

    public double[] fdPrice(double[] strikes) {
        QL_REQUIRE(strikes.length > 0, "ZabrModel::fdPrice(): empty strikes");
        for (int i = 0; i < strikes.length - 1; ++i) {
            QL_REQUIRE(strikes[i] < strikes[i + 1],
                    "ZabrModel::fdPrice(): non-ascending strikes");
        }
        // TODO put these parameters somewhere
        final double start = Math.min(0.00001, strikes[0] * 0.5); // lowest strike for grid
        final double end = Math.max(0.10, strikes[strikes.length - 1] * 1.5); // highest strike for grid
        final int size = 500;                    // grid points
        final double density = 0.1;              // density for concentrating mesher
        final int steps = (int) Math.ceil(expiryTime_ * 24); // number of steps in dimension t
        final int dampingSteps = 5;              // thereof damping steps

        // Layout
        final FdmLinearOpLayout layout = new FdmLinearOpLayout(new int[]{size});

        // Mesher
        final Fdm1dMesher m1 = new Concentrating1dMesher(
                start, end, size, new Point<>(forward_, density), true);
        final FdmMesher mesher = new FdmMesherComposite(layout,
                Collections.singletonList(m1));

        // Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // initial values
        final Array rhs = new Array(mesher.layout().size());
        for (FdmLinearOpIterator iter : layout) {
            final double k = mesher.location(iter, 0);
            rhs.set(iter.index(), Math.max(forward_ - k, 0.0));
        }

        // local vols
        final Array k = mesher.locations(0);
        final double[] kv = k.realVector.toArray();
        final double[] locVolv = localVolatility(kv);
        final Array locVol = new Array(locVolv);

        // solver
        final FdmDupire1dOp map = new FdmDupire1dOp(mesher, locVol);
        final FdmBackwardSolver solver = new FdmBackwardSolver(map, boundaries,
                null, FdmSchemeDesc.Douglas());
        solver.rollback(rhs, expiryTime_, 0.0, steps, dampingSteps);

        // interpolate solution
        final CubicInterpolation solution = new CubicInterpolation(
                kv, rhs.realVector.toArray(), Spline, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
        solution.disableExtrapolation();
        final double[] result = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i) {
            result[i] = solution.value(strikes[i], false);
        }
        return result;
    }

    public double fullFdPrice(double strike) {

        // TODO what are good values here, still experimenting with them
        final double eps = 0.01;
        final double scaleFactor = 1.5;
        final double normInvEps = new InverseCumulativeNormal().value(1.0 - eps);
        final double alphaI = alpha_ * Math.pow(forward_, beta_ - 1.0);
        // nu is already standardized within this class ...
        final double v0 = alpha_ * Math.exp(-scaleFactor * normInvEps
                * Math.sqrt(expiryTime_) * nu_);
        double v1 = alpha_ * Math.exp(scaleFactor * normInvEps
                * Math.sqrt(expiryTime_) * nu_);
        double f0 = forward_ * Math.exp(-scaleFactor * normInvEps
                * Math.sqrt(expiryTime_) * alphaI);
        double f1 = forward_ * Math.exp(scaleFactor * normInvEps
                * Math.sqrt(expiryTime_) * alphaI);
        v1 = Math.min(v1, 2.0);
        f0 = Math.min(strike / 2.0, f0);
        f1 = Math.max(strike * 1.5, Math.min(f1, Math.max(2.0, strike * 1.5)));

        final int sizef = 100;
        final int sizev = 100;
        final int steps = (int) (24 * expiryTime_ + 1);
        final int dampingSteps = 5;
        final double densityf = 0.1;
        final double densityv = 0.1;

        QL_REQUIRE(strike >= f0 && strike <= f1,
                "strike (" + strike + ") must be inside pde grid [" + f0
                        + ";" + f1 + "]");

        // Layout
        final FdmLinearOpLayout layout = new FdmLinearOpLayout(
                new int[]{sizef, sizev});

        // Mesher
        // two concentrating mesher around f and k to get the mesher for f
        final double x0 = Math.min(forward_, strike);
        final double x1 = Math.max(forward_, strike);
        final int sizefa = Math.max(4, (int) Math.ceil(
                ((x0 + x1) / 2.0 - f0) / (f1 - f0) * (double) sizef));
        final int sizefb = sizef - sizefa + 1; // common point, so we can spend
        // one more here
        final Fdm1dMesher mfa = new Concentrating1dMesher(
                f0, (x0 + x1) / 2.0, sizefa, new Point<>(x0, densityf), true);
        final Fdm1dMesher mfb = new Concentrating1dMesher(
                (x0 + x1) / 2.0, f1, sizefb, new Point<>(x1, densityf), true);
        final Fdm1dMesher mf = new Glued1dMesher(mfa, mfb);

        // Volatility mesher
        final Fdm1dMesher mv = new Concentrating1dMesher(
                v0, v1, sizev, new Point<>(alpha_, densityv), true);

        final List<Fdm1dMesher> meshers = new ArrayList<>();
        meshers.add(mf);
        meshers.add(mv);
        final FdmMesher mesher = new FdmMesherComposite(layout, meshers);

        // initial values
        final Array rhs = new Array(mesher.layout().size());
        final List<Double> f_ = new ArrayList<>();
        final List<Double> v_ = new ArrayList<>();
        for (FdmLinearOpIterator iter : layout) {
            final double f = mesher.location(iter, 0);
            rhs.set(iter.index(), Math.max(f - strike, 0.0));
            if (iter.coordinates()[1] == 0)
                f_.add(mesher.location(iter, 0));
            if (iter.coordinates()[0] == 0)
                v_.add(mesher.location(iter, 1));
        }

        // Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        final FdmZabrOp map = new FdmZabrOp(mesher, beta_, nu_, rho_, gamma_);
        final FdmBackwardSolver solver = new FdmBackwardSolver(map, boundaries,
                null, FdmSchemeDesc.Hundsdorfer());

        solver.rollback(rhs, expiryTime_, 0.0, steps, dampingSteps);

        // interpolate solution (this is not necessary when using concentrating
        // meshers with required point)
        final double[] fValues = new double[f_.size()];
        for (int i = 0; i < fValues.length; ++i)
            fValues[i] = f_.get(i);
        final double[] vValues = new double[v_.size()];
        for (int i = 0; i < vValues.length; ++i)
            vValues[i] = v_.get(i);
        final double[][] result = new double[vValues.length][fValues.length];
        for (int j = 0; j < vValues.length; ++j) {
            for (int i = 0; i < fValues.length; ++i) {
                result[j][i] = rhs.get(j * fValues.length + i);
            }
        }
        final BicubicSpline interpolation =
                new BicubicSpline(fValues, vValues, result);
        interpolation.disableExtrapolation();
        return interpolation.value(forward_, alpha_, false);
    }

    public double lognormalVolatility(double strike) {
        return lognormalVolatility(new double[]{strike})[0];
    }

    public double[] lognormalVolatility(double[] strikes) {
        final double[] x_ = x(strikes);
        final double[] result = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i) {
            result[i] = lognormalVolatilityHelper(strikes[i], x_[i]);
        }
        return result;
    }

    public double normalVolatility(double strike) {
        return normalVolatility(new double[]{strike})[0];
    }

    public double[] normalVolatility(double[] strikes) {
        final double[] x_ = x(strikes);
        final double[] result = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i) {
            result[i] = normalVolatilityHelper(strikes[i], x_[i]);
        }
        return result;
    }

    private double lognormalVolatilityHelper(double strike, double x) {
        if (close(strike, forward_))
            return Math.pow(forward_, beta_ - 1.0) * alpha_;
        else
            return Math.log(forward_ / strike) / x;
    }

    private double normalVolatilityHelper(double strike, double x) {
        if (close(strike, forward_))
            return Math.pow(forward_, beta_) * alpha_;
        else
            return (forward_ - strike) / x;
    }

    private double localVolatilityHelper(double f, double x) {
        return alpha_ * Math.pow(Math.abs(f), beta_)
                / F(y(f), Math.pow(alpha_, gamma_ - 1.0) * x);
        // TODO optimize this, y is computed together with x already
    }

    private double x(double strike) {
        return x(new double[]{strike})[0];
    }

    private double[] x(double[] strikes) {
        QL_REQUIRE(strikes.length > 0, "ZabrModel::x(): empty strikes.");
        QL_REQUIRE(strikes[0] > 0.0 || beta_ < 1.0,
                "strikes must be positive (" + strikes[0] + ") if beta = 1");
        for (int i = 1; i < strikes.length; ++i) {
            QL_REQUIRE(strikes[i] > strikes[i - 1],
                    "strikes must be strictly ascending ("
                            + strikes[i - 1] + "," + strikes[i] + ")");
        }

        final AdaptiveRungeKutta rk = new AdaptiveRungeKutta(1.0E-8, 1.0E-5,
                0.0); // TODO move the parameters here as parameters with
                      // default values to the constructor
        final int n = strikes.length;
        final double[] y = new double[n];
        final double[] result = new double[n];
        for (int m = 0; m < n; ++m) {
            y[m] = y(strikes[n - 1 - m]);
        }

        if (close(gamma_, 1.0)) {
            for (int m = 0; m < y.length; m++) {
                final double J = Math.sqrt(1.0 + nu_ * nu_ * y[m] * y[m]
                        - 2.0 * rho_ * nu_ * y[m]);
                result[y.length - 1 - m] =
                        Math.log((J + nu_ * y[m] - rho_) / (1.0 - rho_)) / nu_;
            }
        } else {
            int ynz = upperBound(y, 0.0);
            if (ynz > 0) {
                if (close(y[ynz - 1], 0.0))
                    ynz--;
            }
            if (ynz == y.length)
                ynz--;

            for (int dir = 1; dir >= -1; dir -= 2) {
                double y0 = 0.0, u0 = 0.0;
                for (int m = ynz + (dir == -1 ? -1 : 0);
                     dir == -1 ? m >= 0 : m < y.length; m += dir) {
                    final double ym = y[m];
                    final double u = rk.value(
                            (yy, uu) -> F(yy, uu), u0, y0, ym);
                    result[y.length - 1 - m] = u * Math.pow(alpha_, 1.0 - gamma_);
                    u0 = u;
                    y0 = ym;
                }
            }
        }

        return result;
    }

    private static int upperBound(double[] sorted, double value) {
        int low = 0, high = sorted.length;
        while (low < high) {
            final int mid = low + (high - low) / 2;
            if (sorted[mid] <= value) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private double y(double strike) {
        if (close(beta_, 1.0)) {
            return Math.log(forward_ / strike) * Math.pow(alpha_, gamma_ - 2.0);
        } else {
            return (strike < 0.0
                    ? Math.pow(forward_, 1.0 - beta_)
                    + Math.pow(-strike, 1.0 - beta_)
                    : Math.pow(forward_, 1.0 - beta_)
                    - Math.pow(strike, 1.0 - beta_))
                    * Math.pow(alpha_, gamma_ - 2.0) / (1.0 - beta_);
        }
    }

    private double F(double y, double u) {
        final double A = 1.0 + (gamma_ - 2.0) * (gamma_ - 2.0) * nu_ * nu_ * y * y
                + 2.0 * rho_ * (gamma_ - 2.0) * nu_ * y;
        final double B = 2.0 * rho_ * (1.0 - gamma_) * nu_
                + 2.0 * (1.0 - gamma_) * (gamma_ - 2.0) * nu_ * nu_ * y;
        final double C = (1.0 - gamma_) * (1.0 - gamma_) * nu_ * nu_;
        return (-B * u + Math.sqrt(B * B * u * u - 4.0 * A * (C * u * u - 1.0)))
                / (2.0 * A);
    }
}
