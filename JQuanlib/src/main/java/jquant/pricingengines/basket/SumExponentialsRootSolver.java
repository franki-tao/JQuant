package jquant.pricingengines.basket;

import jquant.math.Array;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.math.solvers1d.Halley;
import jquant.math.solvers1d.Newton;
import jquant.math.solvers1d.Ridder;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Root finder for {@code sum_i a_i exp(sig_i x) = K}.
 *
 * <p>Ported from the {@code detail::SumExponentialsRootSolver} in
 * QuantLib {@code ql/pricingengines/basket/singlefactorbsmbaskengine.hpp}.
 */
public class SumExponentialsRootSolver implements Function {
    public enum Strategy {Ridder, Newton, Brent, Halley}

    private final Array a_;
    private final Array sig_;
    private final double K_;
    private int fCtr_ = 0;
    private int fPrimeCtr_ = 0;
    private int fDoublePrimeCtr_ = 0;

    public SumExponentialsRootSolver(Array a, Array sig, double K) {
        a_ = new Array(a);
        sig_ = new Array(sig);
        K_ = K;
        QL_REQUIRE(a_.size() == sig_.size(),
                "Arrays must have the same size");
    }

    @Override
    public double value(double x) {
        ++fCtr_;
        double s = 0.0;
        for (int i = 0; i < a_.size(); ++i) {
            s += a_.get(i) * Math.exp(sig_.get(i) * x);
        }
        return s - K_;
    }

    @Override
    public double derivative(double x) {
        ++fPrimeCtr_;
        double s = 0.0;
        for (int i = 0; i < a_.size(); ++i) {
            s += a_.get(i) * sig_.get(i) * Math.exp(sig_.get(i) * x);
        }
        return s;
    }

    @Override
    public double secondDerivative(double x) {
        ++fDoublePrimeCtr_;
        double s = 0.0;
        for (int i = 0; i < a_.size(); ++i) {
            double sig = sig_.get(i);
            s += a_.get(i) * sig * sig * Math.exp(sig * x);
        }
        return s;
    }

    public int getFCtr() {
        return fCtr_;
    }

    public int getDerivativeCtr() {
        return fPrimeCtr_;
    }

    public int getSecondDerivativeCtr() {
        return fDoublePrimeCtr_;
    }

    public double getRoot() {
        return getRoot(1e6 * QL_EPSILON, Strategy.Brent);
    }

    public double getRoot(double xTol) {
        return getRoot(xTol, Strategy.Brent);
    }

    public double getRoot(double xTol, Strategy strategy) {
        Array attr = new Array(a_);
        for (int i = 0; i < attr.size(); ++i) {
            attr.set(i, a_.get(i) * sig_.get(i));
        }
        for (int i = 0; i < attr.size(); ++i) {
            QL_REQUIRE(attr.get(i) >= 0.0, "a*sig should not be negative");
        }

        boolean logProb = true;
        for (int i = 0; i < a_.size(); ++i) {
            if (!(a_.get(i) > 0.0)) {
                logProb = false;
                break;
            }
        }

        QL_REQUIRE(K_ > 0 || !logProb,
                "non-positive strikes only allowed for spread options");

        double denom = 0.0;
        double aSum = 0.0;
        for (int i = 0; i < attr.size(); ++i) {
            denom += attr.get(i);
            aSum += a_.get(i);
        }
        double xInit = (Math.abs(denom) > 1000 * QL_EPSILON)
                ? Math.min(10.0, Math.max(-10.0, (K_ - aSum) / denom))
                : 0.0;

        switch (strategy) {
            case Brent:
                return new Brent().solve(this, xTol, xInit, 1.0);
            case Newton:
                return new Newton().solve(this, xTol, xInit, 1.0);
            case Ridder:
                return new Ridder().solve(this, xTol, xInit, 1.0);
            case Halley:
                return new Halley().solve(this, xTol, xInit, 1.0);
            default:
                QL_FAIL("unknown strategy type");
                return 0.0;
        }
    }
}
