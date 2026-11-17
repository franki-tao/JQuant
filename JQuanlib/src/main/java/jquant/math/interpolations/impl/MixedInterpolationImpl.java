package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.templateImpl;
import jquant.math.interpolations.CubicInterpolation;

import java.util.Arrays;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

public class MixedInterpolationImpl extends templateImpl {
    private final double[] xBegin2_;
    private final double switchX_;
    private final int n_;
    private final Interpolation interpolation1_, interpolation2_;
    private final boolean matchDerivatives_;

    public enum Behavior {
        ShareRanges,  /*!< Define both interpolations over the
                               whole range defined by the passed
                               iterators. This is the default
                               behavior. */
        SplitRanges   /*!< Define the first interpolation over the
                               first part of the range, and the second
                               interpolation over the second part. */
    }

    /*
    behavior= ShareRanges
     */
    public MixedInterpolationImpl(double[] x, double[] y, int n,
                                  Behavior behavior, Interpolator factory1,
                                  Interpolator factory2) {
        this(x, y, n, behavior, factory1, factory2, false);
    }

    public MixedInterpolationImpl(double[] x, double[] y, int n,
                                  Behavior behavior, Interpolator factory1,
                                  Interpolator factory2,
                                  boolean matchDerivatives) {
        super(x, y, Math.max(factory1.getRequiredPoints(), factory2.getRequiredPoints()));
        n_ = n;
        // the switch point xBegin_ + n is dereferenced in update() and
        // value(), so it must be a valid element; for SplitRanges the
        // first segment additionally reads xBegin_ + n + 1
        final int maxN = x.length - 1;
        QL_REQUIRE(n <= maxN,
                "n is too large (" + n + " > " + maxN + ")");
        switchX_ = x[n];
        xBegin2_ = Arrays.copyOfRange(x, n, x.length);
        matchDerivatives_ = matchDerivatives;
        switch (behavior) {
            case ShareRanges:
                interpolation1_ = factory1.interpolate(x, y);
                interpolation2_ = factory2.interpolate(x, y);
                break;
            case SplitRanges:
                interpolation1_ = factory1.interpolate(
                        Arrays.copyOfRange(x, 0, n + 1),
                        Arrays.copyOfRange(y, 0, n + 1));
                interpolation2_ = factory2.interpolate(xBegin2_,
                        Arrays.copyOfRange(y, n, y.length));
                break;
            default:
                QL_FAIL("unknown mixed-interpolation behavior: " + behavior);
                throw new IllegalStateException("unreachable");
        }
    }

    @Override
    public void update() {
        interpolation1_.update();
        if (matchDerivatives_) {
            ((CubicInterpolation) interpolation2_)
                    .setLeftConditionValue(interpolation1_.derivative(switchX_, true));
        }
        interpolation2_.update();
    }

    @Override
    public double value(double x) {
        if (x < xBegin2_[0])
            return interpolation1_.value(x, true);
        return interpolation2_.value(x, true);
    }

    @Override
    public double primitive(double x) {
        if (x < xBegin2_[0])
            return interpolation1_.primitive(x, true);
        return interpolation2_.primitive(x, true) -
                interpolation2_.primitive(xBegin2_[0], true) +
                interpolation1_.primitive(xBegin2_[0], true);
    }

    @Override
    public double derivative(double x) {
        if (x < xBegin2_[0])
            return interpolation1_.derivative(x, true);
        return interpolation2_.derivative(x, true);
    }

    @Override
    public double secondDerivative(double x) {
        if (x < xBegin2_[0])
            return interpolation1_.secondDerivative(x, true);
        return interpolation2_.secondDerivative(x, true);
    }

    public int switchIndex() {
        return n_;
    }
}
