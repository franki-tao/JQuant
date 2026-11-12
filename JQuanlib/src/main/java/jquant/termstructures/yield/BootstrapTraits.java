package jquant.termstructures.yield;

import jquant.Compounding;
import jquant.math.Array;
import jquant.math.Interpolation;
import jquant.time.Date;
import jquant.time.Frequency;

/**
 * Bootstrap traits of {@link PiecewiseYieldCurve}, mirroring QuantLib's
 * {@code bootstraptraits.hpp}. Each trait decides what a curve node holds
 * (a discount factor, a zero rate or a forward rate), how the root-finding
 * guess and bracket are built, and how a time {@code t} maps back to a
 * discount factor.
 *
 * <p>{@link #ZeroYield} mirrors QuantLib's {@code ZeroYield} traits with an
 * {@code InterpolatedZeroCurve}: nodes are continuously compounded zero rates
 * interpolated directly, and discounts follow
 * {@code ZeroYieldStructure::discountImpl}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zerocurve.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zeroyieldstructure.hpp
 */
public enum BootstrapTraits {
    /**
     * Nodes are discount factors; the curve is a
     * {@code InterpolatedDiscountCurve}. The reference-date node is 1.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    Discount {
        @Override
        public double initialValue(PiecewiseYieldCurve curve) {
            return 1.0;
        }

        @Override
        public double guess(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) // previous iteration value
                return curve.data()[i];
            if (i == 1) // first pillar
                return 1.0 / (1.0 + AVERAGE_RATE * curve.times()[1]);
            // flat rate extrapolation
            double r = -Math.log(curve.data()[i - 1]) / curve.times()[i - 1];
            return Math.exp(-r * curve.times()[i]);
        }

        @Override
        public double minValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData)
                return min(curve.data()) / 2.0;
            double dt = curve.times()[i] - curve.times()[i - 1];
            return curve.data()[i - 1] * Math.exp(-MAX_RATE * dt);
        }

        @Override
        public double maxValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            double dt = curve.times()[i] - curve.times()[i - 1];
            return curve.data()[i - 1] * Math.exp(MAX_RATE * dt);
        }

        @Override
        public void updateGuess(double[] data, double value, int i) {
            data[i] = value;
        }

        @Override
        public int maxIterations() {
            return 100;
        }

        @Override
        public double discountImpl(PiecewiseYieldCurve curve, double t) {
            double[] times = curve.times();
            Interpolation interpolation = curve.interpolation();
            double tMax = times[times.length - 1];
            if (t <= tMax)
                return interpolation.value(t, true);
            // Flat-forward extrapolation past the last pillar, matching
            // InterpolatedDiscountCurve::discountImpl in
            // ql/termstructures/yield/discountcurve.hpp:
            //   Rate instFwdMax = -interpolation_.derivative(tMax)/dMax;
            //   return dMax * exp(-instFwdMax*(t-tMax));
            double[] data = curve.data();
            double dMax = data[data.length - 1];
            double instFwdMax = -interpolation.derivative(tMax, true) / dMax;
            return dMax * Math.exp(-instFwdMax * (t - tMax));
        }

        // GlobalBootstrap support, from Discount::globalGuess() and
        // Discount::transformDirect()/transformInverse() in bootstraptraits.hpp.

        @Override
        public GlobalGuess globalGuess(PiecewiseYieldCurve curve, boolean validData) {
            double[] data = curve.data();
            if (validData)
                return new GlobalGuess(tail(data), true);
            double[] times = curve.times();
            Array guess = new Array(times.length - 1);
            for (int i = 1; i < times.length; ++i)
                guess.set(i - 1, -AVERAGE_RATE * times[i]);
            // Discount::transformInverse() is log(), so we simply don't call
            // exp() here and return false for needsTransform.
            return new GlobalGuess(guess, false);
        }

        @Override
        public boolean hasTransform() {
            return true;
        }

        @Override
        public double transformDirect(double x, int i, PiecewiseYieldCurve curve) {
            return Math.exp(x);
        }

        @Override
        public double transformInverse(double x, int i, PiecewiseYieldCurve curve) {
            return Math.log(x);
        }
    },

    /**
     * Nodes are (continuously compounded) forward rates; the curve is an
     * {@code InterpolatedForwardCurve}. The reference-date node is the average
     * rate and is also updated when the first pillar is solved.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    ForwardRate {
        @Override
        public double initialValue(PiecewiseYieldCurve curve) {
            return AVERAGE_RATE;
        }

        @Override
        public double guess(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) // previous iteration value
                return curve.data()[i];
            if (i == 1) // first pillar
                return AVERAGE_RATE;
            // extrapolate the instantaneous forward at the pillar date
            Date d = curve.dates().get(i);
            return curve.forwardRate(d, d, curve.dayCounter(),
                    Compounding.Continuous, Frequency.ANNUAL, true).rate();
        }

        @Override
        public double minValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) {
                double r = min(curve.data());
                return r < 0.0 ? r * 2.0 : r / 2.0;
            }
            // no constraints; a value very unlikely to be exceeded
            return -MAX_RATE;
        }

        @Override
        public double maxValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) {
                double r = max(curve.data());
                return r < 0.0 ? r / 2.0 : r * 2.0;
            }
            // no constraints; a value very unlikely to be exceeded
            return MAX_RATE;
        }

        @Override
        public void updateGuess(double[] data, double value, int i) {
            data[i] = value;
            if (i == 1)
                data[0] = value; // first point is updated as well
        }

        @Override
        public int maxIterations() {
            return 100;
        }

        @Override
        public double discountImpl(PiecewiseYieldCurve curve, double t) {
            // zeroYieldImpl of InterpolatedForwardCurve: primitive/t, with
            // flat-forward extrapolation beyond the last node.
            if (t == 0.0)
                return 1.0;
            double[] times = curve.times();
            double[] data = curve.data();
            Interpolation interpolation = curve.interpolation();
            double maxTime = times[times.length - 1];
            double integral;
            if (t <= maxTime) {
                integral = interpolation.primitive(t, true);
            } else {
                // flat fwd extrapolation
                integral = interpolation.primitive(maxTime, true)
                        + data[data.length - 1] * (t - maxTime);
            }
            double zero = integral / t;
            return Math.exp(-zero * t);
        }

        // GlobalBootstrap support, from ForwardRate::globalGuess() in
        // bootstraptraits.hpp.
        @Override
        public GlobalGuess globalGuess(PiecewiseYieldCurve curve, boolean validData) {
            if (validData)
                return new GlobalGuess(tail(curve.data()), true);
            return new GlobalGuess(new Array(curve.times().length - 1, AVERAGE_RATE), true);
        }
    },

    /**
     * Nodes are (continuously compounded) zero rates; the curve is an
     * {@code InterpolatedZeroCurve}. The reference-date node is the average
     * rate and is also updated when the first pillar is solved. Discounts
     * follow {@code ZeroYieldStructure::discountImpl}, {@code exp(-r*t)},
     * with flat instantaneous-forward extrapolation past the last node.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zerocurve.hpp
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zeroyieldstructure.hpp
     */
    ZeroYield {
        @Override
        public double initialValue(PiecewiseYieldCurve curve) {
            // dummy value at reference date
            return AVERAGE_RATE;
        }

        @Override
        public double guess(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) // previous iteration value
                return curve.data()[i];
            if (i == 1) // first pillar
                return AVERAGE_RATE;
            // extrapolate the zero rate at the pillar date
            Date d = curve.dates().get(i);
            return curve.zeroRate(d, curve.dayCounter(),
                    Compounding.Continuous, Frequency.ANNUAL, true).rate();
        }

        @Override
        public double minValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) {
                double r = min(curve.data());
                return r < 0.0 ? r * 2.0 : r / 2.0;
            }
            // no constraints; a value very unlikely to be exceeded
            return -MAX_RATE;
        }

        @Override
        public double maxValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) {
                double r = max(curve.data());
                return r < 0.0 ? r / 2.0 : r * 2.0;
            }
            // no constraints; a value very unlikely to be exceeded
            return MAX_RATE;
        }

        @Override
        public void updateGuess(double[] data, double value, int i) {
            data[i] = value;
            if (i == 1)
                data[0] = value; // first point is updated as well
        }

        @Override
        public int maxIterations() {
            return 100;
        }

        @Override
        public double discountImpl(PiecewiseYieldCurve curve, double t) {
            // zeroYieldImpl of InterpolatedZeroCurve + ZeroYieldStructure.
            if (t == 0.0)
                return 1.0;
            double[] times = curve.times();
            double[] data = curve.data();
            Interpolation interpolation = curve.interpolation();
            double tMax = times[times.length - 1];
            double r;
            if (t <= tMax) {
                r = interpolation.value(t, true);
            } else {
                // flat instantaneous-forward extrapolation
                double zMax = data[data.length - 1];
                double instFwdMax = zMax + tMax * interpolation.derivative(tMax, true);
                r = (zMax * tMax + instFwdMax * (t - tMax)) / t;
            }
            return Math.exp(-r * t);
        }

        // GlobalBootstrap support, from ZeroYield::globalGuess() in
        // bootstraptraits.hpp.
        @Override
        public GlobalGuess globalGuess(PiecewiseYieldCurve curve, boolean validData) {
            if (validData)
                return new GlobalGuess(tail(curve.data()), true);
            return new GlobalGuess(new Array(curve.times().length - 1, AVERAGE_RATE), true);
        }
    },

    /**
     * Nodes are simply compounded zero rates; the curve is an
     * {@code InterpolatedSimpleZeroCurve}. This follows QuantLib's
     * {@code SimpleZeroYield} traits, whose {@code discountImpl} is
     * {@code 1/(1+R*t)} (with flat-forward extrapolation past the last
     * pillar) rather than {@link #ZeroYield}'s {@code exp(-R*t)}.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/interpolatedsimplezerocurve.hpp
     */
    SimpleZeroYield {
        @Override
        public double initialValue(PiecewiseYieldCurve curve) {
            // dummy value at reference date
            return AVERAGE_RATE;
        }

        @Override
        public double guess(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) // previous iteration value
                return curve.data()[i];
            if (i == 1) // first pillar
                return AVERAGE_RATE;
            // extrapolate
            Date d = curve.dates().get(i);
            return curve.zeroRate(d, curve.dayCounter(),
                    Compounding.Simple, Frequency.ANNUAL, true).rate();
        }

        @Override
        public double minValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            double result;
            if (validData) {
                double r = min(curve.data());
                result = r < 0.0 ? r * 2.0 : r / 2.0;
            } else {
                // no constraints; a value very unlikely to be exceeded
                result = -MAX_RATE;
            }
            return Math.max(result, -1.0 / curve.times()[i] + 1E-8);
        }

        @Override
        public double maxValueAfter(int i, PiecewiseYieldCurve curve, boolean validData) {
            if (validData) {
                double r = max(curve.data());
                return r < 0.0 ? r / 2.0 : r * 2.0;
            }
            // no constraints; a value very unlikely to be exceeded
            return MAX_RATE;
        }

        @Override
        public void updateGuess(double[] data, double value, int i) {
            data[i] = value;
            if (i == 1)
                data[0] = value; // first point is updated as well
        }

        @Override
        public int maxIterations() {
            return 100;
        }

        @Override
        public double discountImpl(PiecewiseYieldCurve curve, double t) {
            // InterpolatedSimpleZeroCurve::discountImpl.
            double[] times = curve.times();
            double[] data = curve.data();
            Interpolation interpolation = curve.interpolation();
            double r;
            if (t <= times[times.length - 1]) {
                r = interpolation.value(t, true);
            } else {
                // flat fwd extrapolation after last pillar
                double tMax = times[times.length - 1];
                double zMax = data[data.length - 1];
                double instFwdMax = zMax + tMax * interpolation.derivative(tMax, true);
                r = (zMax * tMax + instFwdMax * (t - tMax)) / t;
            }
            return 1.0 / (1.0 + r * t);
        }

        // GlobalBootstrap support, from SimpleZeroYield::globalGuess() and
        // SimpleZeroYield::transformDirect()/transformInverse().
        @Override
        public GlobalGuess globalGuess(PiecewiseYieldCurve curve, boolean validData) {
            if (validData)
                return new GlobalGuess(tail(curve.data()), true);
            return new GlobalGuess(new Array(curve.times().length - 1, AVERAGE_RATE), true);
        }

        @Override
        public boolean hasTransform() {
            return true;
        }

        @Override
        public double transformDirect(double x, int i, PiecewiseYieldCurve curve) {
            return Math.exp(x) + (-1.0 / curve.times()[i] + 1E-8);
        }

        @Override
        public double transformInverse(double x, int i, PiecewiseYieldCurve curve) {
            return Math.log(x - (-1.0 / curve.times()[i] + 1E-8));
        }
    };

    private static final double AVERAGE_RATE = 0.05;
    private static final double MAX_RATE = 1.0;

    /**
     * Start of curve data. All QL traits use the curve's reference date.
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public Date initialDate(PiecewiseYieldCurve curve) {
        return curve.referenceDate();
    }

    /**
     * Dummy value at the reference date.
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public abstract double initialValue(PiecewiseYieldCurve curve);

    /**
     * Root-finding guess for pillar {@code i}.
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public abstract double guess(int i, PiecewiseYieldCurve curve, boolean validData);

    /**
     * Lower bound for pillar {@code i}.
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public abstract double minValueAfter(int i, PiecewiseYieldCurve curve, boolean validData);

    /**
     * Upper bound for pillar {@code i}.
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public abstract double maxValueAfter(int i, PiecewiseYieldCurve curve, boolean validData);

    /**
     * Root-finding update: stores the solved node value (and, for forward
     * and zero rates, the reference-date node on the first pillar).
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public abstract void updateGuess(double[] data, double value, int i);

    /**
     * Upper bound for the convergence loop.
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     */
    public abstract int maxIterations();

    /**
     * Discount factor at time {@code t}, as computed by the matching curve
     * ({@code InterpolatedDiscountCurve}, {@code InterpolatedZeroCurve} or
     * {@code InterpolatedForwardCurve}).
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zerocurve.hpp
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zeroyieldstructure.hpp
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/interpolatedforwardcurve.hpp
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/discountcurve.hpp
     */
    public abstract double discountImpl(PiecewiseYieldCurve curve, double t);

    /**
     * Guess for the whole curve and whether it still needs to be passed
     * through {@link #transformInverse}. Mirrors
     * {@code Traits::globalGuess()} in QuantLib's GlobalBootstrap.
     */
    public abstract GlobalGuess globalGuess(PiecewiseYieldCurve curve, boolean validData);

    /**
     * Whether this trait provides {@code transformDirect}/{@code transformInverse}.
     * Mirrors the {@code hasTransform} detection in globalbootstrap.hpp.
     */
    public boolean hasTransform() {
        return false;
    }

    public double transformDirect(double x, int i, PiecewiseYieldCurve curve) {
        throw new UnsupportedOperationException(name() + " has no transform");
    }

    public double transformInverse(double x, int i, PiecewiseYieldCurve curve) {
        throw new UnsupportedOperationException(name() + " has no transform");
    }

    /** Container for the result of {@link #globalGuess}. */
    public static final class GlobalGuess {
        public final Array guess;
        public final boolean needsTransform;

        public GlobalGuess(Array guess, boolean needsTransform) {
            this.guess = guess;
            this.needsTransform = needsTransform;
        }
    }

    /** Elements 1..n-1 of {@code data}, as a fresh Array. */
    private static Array tail(double[] data) {
        Array result = new Array(data.length - 1);
        for (int i = 1; i < data.length; ++i)
            result.set(i - 1, data[i]);
        return result;
    }

    private static double min(double[] values) {
        double result = values[0];
        for (double value : values)
            result = Math.min(result, value);
        return result;
    }

    private static double max(double[] values) {
        double result = values[0];
        for (double value : values)
            result = Math.max(result, value);
        return result;
    }
}
