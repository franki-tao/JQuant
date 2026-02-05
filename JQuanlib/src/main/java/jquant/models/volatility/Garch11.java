package jquant.models.volatility;

import jquant.TimeSeries;
import jquant.VolatilityCompositor;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.ReferencePkg;
import jquant.math.optimization.*;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.autocovariances;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.models.volatility.Garch11.Mode.MomentMatchingGuess;
import static jquant.models.volatility.impl.GarchImpl.initialGuess1;
import static jquant.models.volatility.impl.GarchImpl.initialGuess2;

//! GARCH volatility model
/*! Volatilities are assumed to be expressed on an annual basis.
 */
public class Garch11 implements VolatilityCompositor {
    public enum Mode {
        MomentMatchingGuess,   /*!< The initial guess is a moment
                                matching estimates for
                                mean(r2), acf(0), and acf(1). */
        GammaGuess,            /*!< The initial guess is an
                                estimate of gamma based on the
                                property:
                                acf(i+1) = gamma*acf(i) for i > 1. */
        BestOfTwo,             /*!< The best of the two above modes */
        DoubleOptimization     /*!< Double optimization */
    }

    private static final double tol_level = 1.0e-8;
    private double alpha_;
    private double beta_;
    private double gamma_;
    private double v1_;
    private double logLikelihood_;
    private Mode mode_;

    public Garch11(double a, double b, double v1) {
        alpha_ = a;
        beta_ = b;
        gamma_ = 1 - a - b;
        v1_ = v1;
        logLikelihood_ = 0d;
        mode_ = Mode.BestOfTwo;
    }

    //mode = BestOfTwo
    public Garch11(final TimeSeries<Double> qs, Mode mode) {
        alpha_ = 0d;
        beta_ = 0d;
        v1_ = 0d;
        logLikelihood_ = 0d;
        mode_ = mode;
        calibrate(qs);
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double omega() {
        return v1_ * gamma_;
    }

    public double ltVol() {
        return v1_;
    }

    public double logLikelihood() {
        return logLikelihood_;
    }

    public Mode mode() {
        return mode_;
    }

    @Override
    public TimeSeries<Double> calculate(final TimeSeries<Double> quoteSeries) {
        return calculate(quoteSeries, alpha(), beta(), omega());
    }

    public void calibrate(final TimeSeries<Double> quoteSeries) {
        List<Double> values = quoteSeries.values();
        calibrate(values);
    }

    public static TimeSeries<Double> calculate(final TimeSeries<Double> quoteSeries,
                                               double alpha, double beta, double omega) {
        TimeSeries<Double> retval = new TimeSeries<>();
        Iterator<Map.Entry<Date, Double>> cur = quoteSeries.cbegin();
        double u = cur.next().getValue();
        double sigma2 = u * u;
        while (cur.hasNext()) {
            sigma2 = omega + alpha * u * u + beta * sigma2;
            Map.Entry<Date, Double> next = cur.next();
            retval.insert(next.getKey(), Math.sqrt(sigma2));
            u = next.getValue();
        }
        sigma2 = omega + alpha * u * u + beta * sigma2;
        Map.Entry<Date, Double> last = quoteSeries.last();
        Map.Entry<Date, Double> secondLast = quoteSeries.lowerEntry(last.getKey());
        retval.insert(last.getKey().add(TimeUtils.substract(last.getKey(), secondLast.getKey())), Math.sqrt(sigma2));
        return retval;
    }

    public void calibrate(final TimeSeries<Double> quoteSeries, OptimizationMethod method,
                          final EndCriteria endCriteria) {
        List<Double> values = quoteSeries.values();
        calibrate(values, method, endCriteria);
    }

    public void calibrate(final TimeSeries<Double> quoteSeries, OptimizationMethod method,
                          final EndCriteria endCriteria, final Array initialGuess) {
        List<Double> values = quoteSeries.values();
        calibrate(values, method, endCriteria, initialGuess);
    }

    public void calibrate(List<Double> val) {
        List<Double> r2 = new ArrayList<>();
        double mean_r2 = to_r2(val, r2);
        ReferencePkg<Double> alpha = new ReferencePkg<>(alpha_);
        ReferencePkg<Double> beta = new ReferencePkg<>(beta_);
        ReferencePkg<Double> omega = new ReferencePkg<>(v1_);
        Problem p = calibrate_r2(mode_, r2, mean_r2, alpha, beta, omega);
        alpha_ = alpha.getT();
        beta_ = beta.getT();
        v1_ = omega.getT();
        gamma_ = 1 - alpha_ - beta_;
        v1_ /= gamma_;
        logLikelihood_ = p != null ? -p.functionValue() :
                -costFunction(val);
    }

    public void calibrate(List<Double> val,
                          OptimizationMethod method,
                          EndCriteria endCriteria) {
        List<Double> r2 = new ArrayList<>();
        double mean_r2 = to_r2(val, r2);
        ReferencePkg<Double> alpha = new ReferencePkg<>(alpha_);
        ReferencePkg<Double> beta = new ReferencePkg<>(beta_);
        ReferencePkg<Double> omega = new ReferencePkg<>(v1_);
        Problem p = calibrate_r2(mode_, r2, mean_r2, method,
                endCriteria, alpha, beta, omega);
        alpha_ = alpha.getT();
        beta_ = beta.getT();
        v1_ = omega.getT();
        gamma_ = 1 - alpha_ - beta_;
        v1_ /= gamma_;
        logLikelihood_ = p != null ? -p.functionValue() :
                -costFunction(val);
    }

    public void calibrate(List<Double> val,
                          OptimizationMethod method,
                          EndCriteria endCriteria,
                          final Array initialGuess) {
        List<Double> r2 = new ArrayList<>();
        to_r2(val, r2);
        ReferencePkg<Double> alpha = new ReferencePkg<>(alpha_);
        ReferencePkg<Double> beta = new ReferencePkg<>(beta_);
        ReferencePkg<Double> omega = new ReferencePkg<>(v1_);
        Problem p =
                calibrate_r2(r2, method, endCriteria, initialGuess,
                        alpha, beta, omega);
        alpha_ = alpha.getT();
        beta_ = beta.getT();
        v1_ = omega.getT();
        gamma_ = 1 - alpha_ - beta_;
        v1_ /= gamma_;
        logLikelihood_ = p != null ? -p.functionValue() :
                -costFunction(val);
    }

    public double forecast(double r, double sigma2) {
        return gamma_ * v1_ + alpha_ * r * r + beta_ * sigma2;
    }

    public static double to_r2(List<Double> val, List<Double> r2) {
        double u2 = 0.0;
        double mean_r2 = 0.0;
        double w = 1.0;
        for (Double aDouble : val) {
            u2 = aDouble;
            u2 *= u2;
            mean_r2 = (1.0 - w) * mean_r2 + w * u2;
            r2.add(u2);
            w /= (w + 1.0);
        }
        return mean_r2;
    }

    /*! calibrates GARCH for r^2 */
    public static Problem calibrate_r2(
            final List<Double> r2,
            OptimizationMethod method,
            Constraint constraints,
            final EndCriteria endCriteria,
            final Array initGuess, ReferencePkg<Double> alpha,
            ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        Garch11CostFunction cost = new Garch11CostFunction(r2);
        Problem problem =
                new Problem(cost, constraints, initGuess);
        // TODO: check return value from minimize()
        /* EndCriteria::Type ret = */
        method.minimize(problem, endCriteria);
        final Array optimum = problem.currentValue();
        alpha.setT(optimum.get(1));
        beta.setT(optimum.get(2));
        omega.setT(optimum.get(0));
        return problem;
    }

    public static Problem calibrate_r2(
            final List<Double> r2,
            double mean_r2,
            OptimizationMethod method,
            Constraint constraints,
            final EndCriteria endCriteria,
            final Array initGuess, ReferencePkg<Double> alpha,
            ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        List<Double> tmp = CommonUtil.ArrayInit(r2.size());
        for (int i = 0; i < r2.size(); i++) {
            tmp.set(i, r2.get(i) - mean_r2);
        }
        return calibrate_r2(tmp, method, constraints, endCriteria,
                initGuess, alpha, beta, omega);
    }

    public static Problem calibrate_r2(
            Mode mode, final List<Double> r2, double mean_r2,
            OptimizationMethod method, final EndCriteria endCriteria,
            ReferencePkg<Double> alpha,
            ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        double dataSize = (r2.size());
        alpha.setT(0.0);
        beta.setT(0.0);
        omega.setT(0.0);
        QL_REQUIRE(dataSize >= 4,
                "Data series is too short to fit GARCH model");
        QL_REQUIRE(mean_r2 > 0, "Data series is constant");
        omega.setT(mean_r2 * dataSize / (dataSize - 1));

        // ACF
        int maxLag = (int) Math.sqrt(dataSize);
        Array acf = new Array(maxLag + 1);
        List<Double> tmp = CommonUtil.ArrayInit(r2.size());
        for (int i = 0; i < r2.size(); i++) {
            tmp.set(i, r2.get(i) - mean_r2);
        }
        autocovariances(tmp, acf, maxLag);
        QL_REQUIRE(acf.get(0) > 0, "Data series is constant");

        Garch11CostFunction cost = new Garch11CostFunction(r2);

        // two initial guesses based on fitting ACF
        double gammaLower = 0.0;
        Array opt1 = new Array(3);
        double fCost1 = QL_MAX_REAL;
        if (mode != Mode.GammaGuess) {
            ReferencePkg<Double> Alpha = new ReferencePkg<>(opt1.get(1));
            ReferencePkg<Double> Beta = new ReferencePkg<>(opt1.get(2));
            ReferencePkg<Double> Omega = new ReferencePkg<>(opt1.get(0));
            gammaLower = initialGuess1(acf, mean_r2, Alpha, Beta, Omega);
            // 装箱
            opt1.set(1, Alpha.getT());
            opt1.set(2, Beta.getT());
            opt1.set(0, Omega.getT());
            fCost1 = cost.value(opt1);
        }

        Array opt2 = new Array(3);
        double fCost2 = QL_MAX_REAL;
        if (mode != MomentMatchingGuess) {
            ReferencePkg<Double> Alpha = new ReferencePkg<>(opt2.get(1));
            ReferencePkg<Double> Beta = new ReferencePkg<>(opt2.get(2));
            ReferencePkg<Double> Omega = new ReferencePkg<>(opt2.get(0));
            gammaLower = initialGuess2(acf, mean_r2, Alpha, Beta, Omega);
            // 装箱
            opt2.set(1, Alpha.getT());
            opt2.set(2, Beta.getT());
            opt2.set(0, Omega.getT());
            fCost2 = cost.value(opt2);
        }

        Garch11Constraint constraints = new Garch11Constraint(gammaLower, 1.0 - tol_level);

        Problem ret = null;
        if (mode != Mode.DoubleOptimization) {
            try {

                ret = calibrate_r2(r2, method, constraints, endCriteria,
                        fCost1 <= fCost2 ? opt1 : opt2,
                        alpha, beta, omega);
            } catch (Exception e) {
                if (fCost1 <= fCost2) {
                    alpha.setT(opt1.get(1));
                    beta.setT(opt1.get(2));
                    omega.setT(opt1.get(0));
                } else {
                    alpha.setT(opt2.get(1));
                    beta.setT(opt2.get(2));
                    omega.setT(opt2.get(0));
                }
            }
        } else {
            Problem ret1 = null, ret2 = null;
            try {
                ret1 = calibrate_r2(r2, method, constraints, endCriteria,
                        opt1, alpha, beta, omega);
                opt1.set(1, alpha.getT());
                opt1.set(2, beta.getT());
                opt1.set(0, omega.getT());
                if (constraints.test(opt1))
                    fCost1 = Math.min(fCost1, cost.value(opt1));
            } catch (Exception e) {
                fCost1 = QL_MAX_REAL;
            }

            try {
                ret2 = calibrate_r2(r2, method, constraints, endCriteria,
                        opt2, alpha, beta, omega);
                opt2.set(1, alpha.getT());
                opt2.set(2, beta.getT());
                opt2.set(0, omega.getT());
                if (constraints.test(opt2))
                    fCost2 = Math.min(fCost2, cost.value(opt2));
            } catch (Exception e) {
                fCost2 = QL_MAX_REAL;
            }

            if (fCost1 <= fCost2) {
                alpha.setT(opt1.get(1));
                beta.setT(opt1.get(2));
                omega.setT(opt1.get(0));
                ret = ret1;
            } else {
                alpha.setT(opt2.get(1));
                beta.setT(opt2.get(2));
                omega.setT(opt2.get(0));
                ret = ret2;
            }
        }
        return ret;
    }

    public static Problem calibrate_r2(
            Mode mode, final List<Double> r2, double mean_r2,
            ReferencePkg<Double> alpha,
            ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        EndCriteria endCriteria = new EndCriteria(10000, 500, tol_level, tol_level, tol_level);
        Simplex method = new Simplex(0.001);
        return calibrate_r2(mode, r2, mean_r2, method, endCriteria,
                alpha, beta, omega);
    }

    public static Problem calibrate_r2(
            final List<Double> r2,
            OptimizationMethod method,
            final EndCriteria endCriteria,
            final Array initGuess, ReferencePkg<Double> alpha,
            ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        Garch11Constraint constraints = new Garch11Constraint(0.0, 1.0 - tol_level);
        return calibrate_r2(r2, method, constraints, endCriteria,
                initGuess, alpha, beta, omega);
    }

    public static double costFunction(List<Double> val, double alpha, double beta, double omega) {
        double retval = (0.0);
        double u2 = (0.0), sigma2 = (0.0);
        int N = 0;
        for (; N < val.size(); ++N) {
            sigma2 = omega + alpha * u2 + beta * sigma2;
            u2 = val.get(N);
            u2 *= u2;
            retval += Math.log(sigma2) + u2 / sigma2;
        }
        return N > 0 ? (retval / (2 * N)) : 0.0;
    }

    private double costFunction(List<Double> val) {
        return costFunction(val, alpha(), beta(), omega());
    }
}
