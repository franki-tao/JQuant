package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.ReferencePkg;
import jquant.math.randomnumbers.impl.RngImpl;

import java.util.List;

import static jquant.math.CommonUtil.Norm2;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.optimization.SimulatedAnnealing.Scheme.ConstantFactor;

/**
 * ! Class RNG must implement the following interface:
 * \code
 * RNG::sample_type RNG::next() const;
 * \endcode
 * <p>
 * \ingroup optimizers
 */

//! Simulated Annealing
public class SimulatedAnnealing extends OptimizationMethod {
    public enum Scheme {
        ConstantFactor,
        ConstantBudget
    }

    private Scheme scheme_;
    private double lambda_, T0_, epsilon_, alpha_;
    private int K_;
    private RngImpl rng_;
    private double T_;
    private List<Array> vertices_;
    private Array values_, sum_;
    private int i_, ihi_, ilo_, j_, m_, n_;
    private double fac1_, fac2_, yflu_;
    private double rtol_, swap_, yhi_, ylo_, ynhi_, ysave_, yt_, yb_, tt_, ytry_;
    private Array pb_, ptry_;
    private int iteration_, iterationT_;

    /**
     * ! reduce temperature T by a factor of \f$ (1-\epsilon) \f$ after m moves
     */
    public SimulatedAnnealing(final double lambda, final double T0,
                              final double epsilon, final int m,
                              final RngImpl rng) {
        scheme_ = ConstantFactor;
        lambda_ = lambda;
        T0_ = T0;
        epsilon_ = epsilon;
        alpha_ = 0.0;
        K_ = 0;
        rng_ = rng;
        m_ = m;
        ytry_ = Double.NaN;
    }

    /**
     * ! budget a total of K moves, set temperature T to the initial
     * temperature times \f$ ( 1 - k/K )^\alpha \f$ with k being the total number
     * of moves so far. After K moves the temperature is guaranteed to be
     * zero, after that the optimization runs like a deterministic simplex
     * algorithm.
     */
    public SimulatedAnnealing(final double lambda, final double T0, final int K,
                              final double alpha, final RngImpl rng) {
        scheme_ = Scheme.ConstantBudget;
        lambda_ = lambda;
        T0_ = T0;
        epsilon_ = 0.0;
        alpha_ = alpha;
        K_ = K;
        rng_ = rng;
        ytry_ = Double.NaN;
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria ec) {

        ReferencePkg<Integer> stationaryStateIterations_ = new ReferencePkg<>(0);
        ReferencePkg<EndCriteria.Type> ecType = new ReferencePkg<>(EndCriteria.Type.None);
        P.reset();
        Array x = P.currentValue();
        iteration_ = 0;
        n_ = x.size();
        ptry_ = new Array(n_, 0.0);

        // build vertices

        vertices_ = CommonUtil.ArrayInit(n_ + 1, x);
        for (i_ = 0; i_ < n_; i_++) {
            Array direction = new Array(n_, 0.0);
            direction.set(i_, 1.0);
            P.constraint().update(vertices_.get(i_ + 1), direction, lambda_);
        }
        values_ = new Array(n_ + 1, 0.0);
        for (i_ = 0; i_ <= n_; i_++) {
            if (!P.constraint().test(vertices_.get(i_)))
                values_.set(i_, QL_MAX_REAL);
            else
                values_.set(i_, P.value(vertices_.get(i_)));
            if (ytry_ == Double.NaN) { // handle NAN
                values_.set(i_, QL_MAX_REAL);
            }
        }

        // minimize

        T_ = T0_;
        yb_ = QL_MAX_REAL;
        pb_ = new Array(n_, 0.0);
        do {
            iterationT_ = iteration_;
            do {
                sum_ = new Array(n_, 0.0);
                for (i_ = 0; i_ <= n_; i_++)
                    sum_ = sum_.add(vertices_.get(i_));
                tt_ = -T_;
                ilo_ = 0;
                ihi_ = 1;
                ynhi_ = values_.get(0) + tt_ * Math.log(rng_.next().value);
                ylo_ = ynhi_;
                yhi_ = values_.get(1) + tt_ * Math.log(rng_.next().value);
                if (ylo_ > yhi_) {
                    ihi_ = 0;
                    ilo_ = 1;
                    ynhi_ = yhi_;
                    yhi_ = ylo_;
                    ylo_ = ynhi_;
                }
                for (i_ = 2; i_ < n_ + 1; i_++) {
                    yt_ = values_.get(i_) + tt_ * Math.log(rng_.next().value);
                    if (yt_ <= ylo_) {
                        ilo_ = i_;
                        ylo_ = yt_;
                    }
                    if (yt_ > yhi_) {
                        ynhi_ = yhi_;
                        ihi_ = i_;
                        yhi_ = yt_;
                    } else {
                        if (yt_ > ynhi_) {
                            ynhi_ = yt_;
                        }
                    }
                }

                // rtol_ = 2.0 * std::fabs(yhi_ - ylo_) /
                //         (std::fabs(yhi_) + std::fabs(ylo_));
                // check rtol against some ftol... // NR end criterion in f(x)

                // GSL end criterion in x (cf. above)
                if (ec.checkStationaryPoint(simplexSize(), 0.0,
                        stationaryStateIterations_,
                        ecType) ||
                        ec.checkMaxIterations(iteration_, ecType)) {
                    // no matter what, we return the best ever point !
                    P.setCurrentValue(pb_);
                    P.setFunctionValue(yb_);
                    return ecType.getT();
                }

                iteration_ += 2;
                amotsa(P, -1.0);
                if (ytry_ <= ylo_) {
                    amotsa(P, 2.0);
                } else {
                    if (ytry_ >= ynhi_) {
                        ysave_ = yhi_;
                        amotsa(P, 0.5);
                        if (ytry_ >= ysave_) {
                            for (i_ = 0; i_ < n_ + 1; i_++) {
                                if (i_ != ilo_) {
                                    for (j_ = 0; j_ < n_; j_++) {
                                        sum_.set(j_, 0.5 * (vertices_.get(i_).get(j_) +
                                                vertices_.get(ilo_).get(j_)));
                                        vertices_.get(i_).set(j_, sum_.get(j_));
                                    }
                                    values_.set(i_, P.value(sum_));
                                }
                            }
                            iteration_ += n_;
                            for (i_ = 0; i_ < n_; i_++)
                                sum_.set(i_, 0.0);
                            for (i_ = 0; i_ <= n_; i_++)
                                sum_ = sum_.add(vertices_.get(i_));
                        }
                    } else {
                        iteration_ += 1;
                    }
                }
            } while (iteration_ <
                    iterationT_ + (scheme_ == ConstantFactor ? m_ : 1));

            switch (scheme_) {
                case ConstantFactor:
                    T_ *= (1.0 - epsilon_);
                    break;
                case ConstantBudget:
                    if (iteration_ <= K_)
                        T_ = T0_ *
                                Math.pow(1.0 - (double) iteration_ / (double) K_, alpha_);
                    else
                        T_ = 0.0;
                    break;
            }

        } while (true);
    }

    private double simplexSize() {
        Array center = new Array(vertices_.get(0).size(), 0);
        for (Array vertice : vertices_)
            center = center.add(vertice);
        center = center.mutiply(1d / (vertices_.size()));
        double result = 0;
        for (Array vertice : vertices_) {
            Array temp = vertice.subtract(center);
            result += Norm2(temp);
        }
        return result / (vertices_.size());
    }

    private void amotsa(Problem P, double fac) {
        fac1_ = (1.0 - fac) / ((double) n_);
        fac2_ = fac1_ - fac;
        for (j_ = 0; j_ < n_; j_++) {
            ptry_.set(j_, sum_.get(j_) * fac1_ - vertices_.get(ihi_).get(j_) * fac2_);
        }
        if (!P.constraint().test(ptry_))
            ytry_ = QL_MAX_REAL;
        else
            ytry_ = P.value(ptry_);
        if (ytry_ == Double.NaN) {
            ytry_ = QL_MAX_REAL;
        }
        if (ytry_ <= yb_) {
            yb_ = ytry_;
            pb_ = ptry_;
        }
        yflu_ = ytry_ - tt_ * Math.log(rng_.next().value);
        if (yflu_ < yhi_) {
            values_.set(ihi_, ytry_);
            yhi_ = yflu_;
            for (j_ = 0; j_ < n_; j_++) {
                sum_.addEq(j_, ptry_.get(j_) - vertices_.get(ihi_).get(j_));
                vertices_.get(ihi_).set(j_, ptry_.get(j_));
            }
        }
        ytry_ = yflu_;
    }
}
