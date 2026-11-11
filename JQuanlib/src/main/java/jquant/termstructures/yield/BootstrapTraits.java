package jquant.termstructures.yield;

import jquant.Compounding;
import jquant.math.Interpolation;
import jquant.time.Date;
import jquant.time.Frequency;

/**
 * Bootstrap traits of {@link PiecewiseYieldCurve}, mirroring QuantLib's
 * {@code bootstraptraits.hpp}. Each trait decides what a curve node holds
 * (a discount factor, a zero rate, or a forward rate), how the root-finding
 * guess and bracket are built, and how a time {@code t} maps back to a
 * discount factor.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
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
            return curve.interpolation().value(t, true);
        }
    },

    /**
     * Nodes are (continuously compounded) zero rates; the curve is an
     * {@code InterpolatedZeroCurve}. The reference-date node is the average
     * rate and is also updated when the first pillar is solved.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zerocurve.hpp
     */
    ZeroYield {
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
            // InterpolatedZeroCurve: discount = exp(-zero(t) * t).
            if (t == 0.0)
                return 1.0;
            double zero = curve.interpolation().value(t, true);
            return Math.exp(-zero * t);
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
    };

    private static final double AVERAGE_RATE = 0.05;
    private static final double MAX_RATE = 1.0;

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
     * rates, the reference-date node on the first pillar).
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
     * ({@code InterpolatedDiscountCurve}, {@code InterpolatedZeroCurve}, or
     * {@code InterpolatedForwardCurve}).
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/interpolatedforwardcurve.hpp
     * https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/discountcurve.hpp
     */
    public abstract double discountImpl(PiecewiseYieldCurve curve, double t);

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
