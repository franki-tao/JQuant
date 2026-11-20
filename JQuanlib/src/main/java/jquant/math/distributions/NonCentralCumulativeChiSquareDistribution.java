package jquant.math.distributions;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_INTEGER;
import static jquant.math.MathUtils.QL_MIN_POSITIVE_REAL;

/**
 * Non-central cumulative chi-square distribution.
 *
 * <p>Port of QuantLib {@code NonCentralCumulativeChiSquareDistribution} from
 * {@code ql/math/distributions/chisquaredistribution.{hpp,cpp}}.
 */
public class NonCentralCumulativeChiSquareDistribution {
    private double df_;
    private double ncp_;

    public NonCentralCumulativeChiSquareDistribution(double df, double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    public double value(double x) {
        if (x <= 0.0)
            return 0.0;

        final double errmax = 1e-12;
        final long itrmax = 1000000;
        final double minLog = Math.log(QL_MIN_POSITIVE_REAL);
        double lam = 0.5 * ncp_;
        double x2 = 0.5 * x;
        double f2 = 0.5 * df_;
        final double df = df_;
        final double ncp = ncp_;

        // For large ncp or x, the Poisson weights u and the first terms t
        // of the series underflow to zero, and so would the whole sum.
        // We use their logarithms (which increase with n up to lam and
        // x2-f2, respectively) to find the first representable ones and
        // skip the corresponding (negligible) terms.
        final double logLam = Math.log(lam);
        final double logX2 = Math.log(x2);
        final GammaFunction gamma = new GammaFunction();

        // log of the n-th Poisson weight
        java.util.function.LongToDoubleFunction logU = n ->
                n * logLam - lam - gamma.logValue(n + 1.0);
        // log of the n-th term of the series
        java.util.function.LongToDoubleFunction logT = n ->
                (f2 + n) * logX2 - x2 - gamma.logValue(f2 + n + 1.0);

        final long never = Long.MAX_VALUE;
        java.util.function.LongUnaryOperator firstT = from -> {
            long peak = (long) Math.min(Math.floor(x2 - f2),
                    (double) (from + itrmax));
            if (peak < from)
                return logT.applyAsDouble(from) >= minLog ? from : never;
            long lo = from;
            long hi = peak;
            if (logT.applyAsDouble(hi) < minLog)
                return never;
            while (lo < hi) {
                long mid = lo + (hi - lo) / 2;
                if (logT.applyAsDouble(mid) >= minLog)
                    hi = mid;
                else
                    lo = mid + 1;
            }
            return lo;
        };

        boolean uSmall = -lam < minLog;
        double u = uSmall ? 0.0 : Math.exp(-lam);
        double v = u;

        double t;
        boolean tSmall = false;
        if (f2 * QL_EPSILON > 0.125
                && Math.abs(x2 - f2) < Math.sqrt(QL_EPSILON) * f2) {
            t = Math.exp((1 - 0.0) * (2 - 0.0 / (f2 + 1)))
                    / Math.sqrt(2.0 * M_PI * (f2 + 1.0));
        } else {
            double lt = f2 * logX2 - x2 - gamma.logValue(f2 + 1);
            tSmall = lt < minLog;
            t = tSmall ? 0.0 : Math.exp(lt);
        }

        if (tSmall && x > df + ncp) {
            // Chernoff bound on the upper tail. If it's negligible,
            // return 1 instead of summing about x/2 terms to get there.
            double w = (df + Math.sqrt(df * df + 4.0 * x * ncp)) / (2.0 * x);
            double logBound = -0.5 * (1.0 - w) * x - 0.5 * df * Math.log(w)
                    + 0.5 * ncp * (1.0 - w) / w;
            if (logBound < Math.log(errmax))
                return 1.0;
        }

        double ans = v * t;

        long n = 1;
        long nT = tSmall ? firstT.applyAsLong(n) : 0;
        if (uSmall) {
            // jump to the first representable Poisson weight
            QL_REQUIRE(lam < QL_MAX_INTEGER,
                    "non-centrality parameter too large");
            long lo = n;
            long hi = (long) lam;
            while (lo < hi) {
                long mid = lo + (hi - lo) / 2;
                if (logU.applyAsDouble(mid) >= minLog)
                    hi = mid;
                else
                    lo = mid + 1;
            }
            n = lo;
            u = v = Math.exp(logU.applyAsDouble(n));
            double lt = logT.applyAsDouble(n);
            tSmall = lt < minLog;
            t = tSmall ? 0.0 : Math.exp(lt);
            nT = tSmall ? firstT.applyAsLong(n + 1) : 0;
            ans = v * t;
            n++;
        }
        final long nMax = n + itrmax;

        boolean flag = false;
        double f_2n = df + 2.0 * n;
        double f_x_2n = df - x + 2.0 * n;

        double bound = 0.0;
        while (true) {
            if (f_x_2n > 0) {
                flag = true;
                // L10: error bound, no update on the first pass
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > nMax)
                    break;
            }

            u *= lam / n;
            v += u;
            if (tSmall) {
                if (n >= nT) {
                    t = Math.exp(logT.applyAsDouble(n));
                    tSmall = false;
                }
            } else {
                t *= x / f_2n;
            }
            ans += v * t;
            n++;
            f_2n += 2.0;
            f_x_2n += 2.0;
            if (!flag && n <= nMax)
                continue;

            // L10
            bound = t * x / f_x_2n;
            if (bound <= errmax || n > nMax)
                break;
        }

        // f_x_2n <= 0 means that we ran out of iterations before
        // the error bound could even be computed
        if (f_x_2n <= 0 || bound > errmax)
            QL_FAIL("didn't converge");
        return ans;
    }
}
