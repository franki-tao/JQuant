package jquant.processes;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Point;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MIN_POSITIVE_REAL;

public class GsrProcessCore {
    protected Array times_;
    protected Array vols_;
    protected Array reversions_;
    private Map<Point<Double, Double>, Double> cache1_;
    private Map<Point<Double, Double>, Double> cache2a_;
    private Map<Point<Double, Double>, Double> cache2b_;
    private Map<Point<Double, Double>, Double> cache3_;
    private Map<Point<Double, Double>, Double> cache5_;
    private Map<Double, Double> cache4_;
    private double T_;
    private List<Boolean> revZero_;

    public GsrProcessCore(final Array times, final Array vols,
                          final Array reversions, final double T) {
        times_ = times;
        vols_ = vols;
        reversions_ = reversions;
        T_ = T;
        revZero_ = CommonUtil.ArrayInit(reversions.size(), false);

        QL_REQUIRE(times.size() == vols.size() - 1,
                "number of volatilities ("
                        + vols.size() + ") compared to number of times ("
                        + times_.size() + " must be bigger by one");
        QL_REQUIRE(times.size() == reversions.size() - 1 || reversions.size() == 1,
                "number of reversions ("
                        + vols.size() + ") compared to number of times ("
                        + times_.size() + " must be bigger by one, or exactly " +
                        "1 reversion must be given");
        for (int i = 0; i < times.size() - 1; i++)
            QL_REQUIRE(times.get(i) < times.get(i + 1), "times must be increasing ("
                    + times.get(i) + "@" + i + " , "
                    + times.get(i + 1) + "@" + i + 1
                    + ")");
        cache1_ = new HashMap<>();
        cache2a_ = new HashMap<>();
        cache2b_ = new HashMap<>();
        cache3_ = new HashMap<>();
        cache4_ = new HashMap<>();
        cache5_ = new HashMap<>();
        flushCache();
    }

    // conditional expectation, x0 dependent part
    public double expectation_x0dep_part(double w, double xw, double dt) {
        double t = w + dt;
        Point<Double, Double> key = new Point<>(w, t);
        if (cache1_.containsKey(key)) {
            return xw * cache1_.get(key);
        }
        // A(w,t)x(w)
        double res2 = 1.0;
        for (int i = lowerIndex(w); i <= upperIndex(t) - 1; i++) {
            res2 *= Math.exp(-rev(i) * (cappedTime(i + 1, t) - flooredTime(i, w)));
        }
        cache1_.put(key, res2);
        return res2 * xw;
    }

    // conditional expectation, x0 independent part
    // in the risk neutral measure
    public double expectation_rn_part(double w, double dt) {
        double t = w + dt;

        Point<Double, Double> key = new Point<>(w, t);
        if (cache2a_.containsKey(key)) {
            return cache2a_.get(key);
        }

        double res = 0.0;

        // \int A(s,t)y(s)
        for (int k = lowerIndex(w); k <= upperIndex(t) - 1; k++) {
            // l<k
            for (int l = 0; l <= k - 1; l++) {
                double res2 = 1.0;
                // alpha_l
                res2 *= revZero(l) ? (vol(l) * vol(l) * (time2(l + 1) - time2(l)))
                        : vol(l) * vol(l) / (2.0 * rev(l)) *
                        (1.0 - Math.exp(-2.0 * rev(l) *
                                (time2(l + 1) - time2(l))));
                // zeta_i (i>k)
                for (int i = k + 1; i <= upperIndex(t) - 1; i++)
                    res2 *= Math.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
                // beta_j (j<k)
                for (int j = l + 1; j <= k - 1; j++)
                    res2 *= Math.exp(-2.0 * rev(j) * (time2(j + 1) - time2(j)));
                // zeta_k beta_k
                res2 *=
                        revZero(k)
                                ? (2.0 * time2(k) - flooredTime(k, w) -
                                cappedTime(k + 1, t) -
                                2.0 * (time2(k) - cappedTime(k + 1, t)))
                                : ((Math.exp(rev(k) * (2.0 * time2(k) - flooredTime(k, w) -
                                cappedTime(k + 1, t))) -
                                Math.exp(2.0 * rev(k) * (time2(k) - cappedTime(k + 1, t)))) /
                                rev(k));
                // add to sum
                res += res2;
            }
            // l=k
            double res2 = 1.0;
            // alpha_k zeta_k
            res2 *=
                    revZero(k)
                            ? (vol(k) * vol(k) / 4.0 *
                            (4.0 * Math.pow(cappedTime(k + 1, t) - time2(k), 2.0) -
                                    (Math.pow(flooredTime(k, w) - 2.0 * time2(k) +
                                                    cappedTime(k + 1, t),
                                            2.0) +
                                            Math.pow(cappedTime(k + 1, t) - flooredTime(k, w), 2.0))))
                            : (vol(k) * vol(k) / (2.0 * rev(k) * rev(k)) *
                            (Math.exp(-2.0 * rev(k) * (cappedTime(k + 1, t) - time2(k))) +
                                    1.0 -
                                    (Math.exp(-rev(k) * (flooredTime(k, w) - 2.0 * time2(k) +
                                            cappedTime(k + 1, t))) +
                                            Math.exp(-rev(k) *
                                                    (cappedTime(k + 1, t) - flooredTime(k, w))))));
            // zeta_i (i>k)
            for (int i = k + 1; i <= upperIndex(t) - 1; i++)
                res2 *= Math.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
            // no beta_j in this case ...
            res += res2;
        }
        cache2a_.put(key, res);
        return res;
    } // expectation_rn_part

    // conditional expectation, drift adjustment for
    // the T-forward measure
    public double expectation_tf_part(double w, double dt) {
        double t = w + dt;

        Point<Double, Double> key = new Point<>(w, t);
        if (cache2b_.containsKey(key)) {
            return cache2b_.get(key);
        }

        double res = 0.0;
        // int -A(s,t) \sigma^2 G(s,T)
        for (int k = lowerIndex(w); k <= upperIndex(t) - 1; k++) {
            double res2 = 0.0;
            // l>k
            for (int l = k + 1; l <= upperIndex(T_) - 1; l++) {
                double res3 = 1.0;
                // eta_l
                res3 *= revZero(l)
                        ? (cappedTime(l + 1, T_) - time2(l))
                        : (1.0 -
                        Math.exp(-rev(l) * (cappedTime(l + 1, T_) - time2(l)))) /
                        rev(l);
                // zeta_i (i>k)
                for (int i = k + 1; i <= upperIndex(t) - 1; i++)
                    res3 *= Math.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
                // gamma_j (j>k)
                for (int j = k + 1; j <= l - 1; j++)
                    res3 *= Math.exp(-rev(j) * (time2(j + 1) - time2(j)));
                // zeta_k gamma_k
                res3 *=
                        revZero(k)
                                ? ((cappedTime(k + 1, t) - time2(k + 1) -
                                (2.0 * flooredTime(k, w) - cappedTime(k + 1, t) -
                                        time2(k + 1))) /
                                2.0)
                                : ((Math.exp(rev(k) * (cappedTime(k + 1, t) - time2(k + 1))) -
                                Math.exp(rev(k) * (2.0 * flooredTime(k, w) -
                                        cappedTime(k + 1, t) - time2(k + 1)))) /
                                (2.0 * rev(k)));
                // add to sum
                res2 += res3;
            }
            // l=k
            double res3 = 1.0;
            // eta_k zeta_k
            res3 *=
                    revZero(k)
                            ? ((-Math.pow(cappedTime(k + 1, t) - cappedTime(k + 1, T_), 2.0) -
                            2.0 * Math.pow(cappedTime(k + 1, t) - flooredTime(k, w), 2.0) +
                            Math.pow(2.0 * flooredTime(k, w) - cappedTime(k + 1, T_) -
                                            cappedTime(k + 1, t),
                                    2.0)) /
                            4.0)
                            : ((2.0 - Math.exp(rev(k) *
                            (cappedTime(k + 1, t) - cappedTime(k + 1, T_))) -
                            (2.0 * Math.exp(-rev(k) *
                                    (cappedTime(k + 1, t) - flooredTime(k, w))) -
                                    Math.exp(rev(k) *
                                            (2.0 * flooredTime(k, w) - cappedTime(k + 1, T_) -
                                                    cappedTime(k + 1, t))))) /
                            (2.0 * rev(k) * rev(k)));
            // zeta_i (i>k)
            for (int i = k + 1; i <= upperIndex(t) - 1; i++)
                res3 *= Math.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
            // no gamma_j in this case ...
            res2 += res3;
            // add to main accumulator
            res += -vol(k) * vol(k) * res2;
        }

        cache2b_.put(key, res);

        return res;
    }

    // conditional variance
    public double variance(double w, double dt) {
        double t = w + dt;

        Point<Double, Double> key = new Point<>(w, t);
        if (cache3_.containsKey(key)) {
            return cache3_.get(key);
        }
        double res = 0.0;
        for (int k = lowerIndex(w); k <= upperIndex(t) - 1; k++) {
            double res2 = vol(k) * vol(k);
            // zeta_k^2
            res2 *= revZero(k)
                    ? (-(flooredTime(k, w) - cappedTime(k + 1, t)))
                    : (1.0 - Math.exp(2.0 * rev(k) *
                    (flooredTime(k, w) - cappedTime(k + 1, t)))) /
                    (2.0 * rev(k));
            // zeta_i (i>k)
            for (int i = k + 1; i <= upperIndex(t) - 1; i++) {
                res2 *= Math.exp(-2.0 * rev(i) * (cappedTime(i + 1, t) - time2(i)));
            }
            res += res2;
        }

        cache3_.put(key, res);
        return res;
    }

    // y(t)
    public double y(double t) {
        double key;
        key = t;
        if (cache4_.containsKey(key)) {
            return cache4_.get(key);
        }

        double res = 0.0;
        for (int i = 0; i <= upperIndex(t) - 1; i++) {
            double res2 = 1.0;
            for (int j = i + 1; j <= upperIndex(t) - 1; j++) {
                res2 *= Math.exp(-2.0 * rev(j) * (cappedTime(j + 1, t) - time2(j)));
            }
            res2 *= revZero(i) ? (vol(i) * vol(i) * (cappedTime(i + 1, t) - time2(i)))
                    : (vol(i) * vol(i) / (2.0 * rev(i)) *
                    (1.0 - Math.exp(-2.0 * rev(i) *
                            (cappedTime(i + 1, t) - time2(i)))));
            res += res2;
        }

        cache4_.put(key, res);
        return res;
    }

    // G(t,w)
    public double G(double t, double w) {
        Point<Double, Double> key = new Point<>(w, t);
        if (cache5_.containsKey(key)) {
            return cache5_.get(key);
        }

        double res = 0.0;
        for (int i = lowerIndex(t); i <= upperIndex(w) - 1; i++) {
            double res2 = 1.0;
            for (int j = lowerIndex(t); j <= i - 1; j++) {
                res2 *= Math.exp(-rev(j) * (time2(j + 1) - flooredTime(j, t)));
            }
            res2 *= revZero(i) ? (cappedTime(i + 1, w) - flooredTime(i, t))
                    : (1.0 - Math.exp(-rev(i) * (cappedTime(i + 1, w) -
                    flooredTime(i, t)))) /
                    rev(i);
            res += res2;
        }

        cache5_.put(key, res);
        return res;
    }

    // sigma
    public double sigma(double t) {
        return vol(lowerIndex(t));
    }

    // reversion
    public double reversion(double t) {
        return rev(lowerIndex(t));
    }

    // reset cache
    public void flushCache() {
        for (int i = 0; i < reversions_.size(); i++)
            // small reversions cause numerical problems, so we keep them
            // away from zero
            if (Math.abs(reversions_.get(i)) < 1E-4)
                revZero_.set(i, true);
            else
                revZero_.set(i, false);
        cache1_.clear();
        cache2a_.clear();
        cache2b_.clear();
        cache3_.clear();
        cache4_.clear();
        cache5_.clear();
    }

    private int lowerIndex(double t) {
        return times_.upperIndex(t);
    }

    private int upperIndex(double t) {
        if (t < QL_MIN_POSITIVE_REAL)
            return 0;
        return times_.upperIndex(t - QL_EPSILON) + 1;
    }

    public double time2(final int index) {
        if (index == 0)
            return 0.0;
        if (index > times_.size())
            return T_; // FIXME how to ensure that forward
        // measure time is geq all times
        // given
        return times_.get(index - 1);
    }

    // cap = NULL_REAL
    private double cappedTime(int index, double cap) {
        return !Double.isNaN(cap) ? Math.min(cap, time2(index)) : time2(index);
    }

    private double flooredTime(int index, double floor) {
        return !Double.isNaN(floor) ? Math.max(floor, time2(index)) : time2(index);
    }

    private double vol(final int index) {
        if (index >= vols_.size())
            return vols_.back();
        return vols_.get(index);
    }

    private double rev(final int index) {
        if (index >= reversions_.size())
            return reversions_.back();
        return reversions_.get(index);
    }

    private boolean revZero(final int index) {
        if (index >= revZero_.size())
            return revZero_.get(revZero_.size() - 1);
        return revZero_.get(index);
    }
}
