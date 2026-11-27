package jquant.processes.impl;

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

public class ManualNonCentralChiSquared {
    private static final double EPSILON = Precision.EPSILON;
    private static final long MAX_SERIES_ITERATIONS = 1000000L;

    /**
     * 计算非中心卡方分布的 PDF: pdf(x; df, ncp)
     * 对应 C++ 中的 boost::math::pdf
     */
    public static double density(double x, double df, double ncp) {
        return nonCentralChiSquaredPdf(x, df, ncp);
    }

    /**
     * 封装你要求的具体 CIR 公式: pdf(v/k; df, ncp) / k
     */
    public static double calculateCirDensity(double theta, double kappa, double sigma, double t, double v0, double v) {
        double df = (4.0 * theta * kappa) / (sigma * sigma);
        double expTerm = FastMath.exp(-kappa * t);
        double ncp = (4.0 * kappa * expTerm * v0) / ((sigma * sigma) * (1.0 - expTerm));
        double k = (sigma * sigma * (1.0 - expTerm)) / (4.0 * kappa);

        return density(v / k, df, ncp) / k;
    }

    /**
     * Boost 实现逻辑
     * * @param theta  长期均值 (theta_)
     * @param kappa  回归速度 (kappa_)
     * @param sigma  波动率的波动 (sigma_)
     * @param v0     初始值 (v0_)
     * @param t      时间步长 (t)
     * @param v      目标值 (v)
     * @return       概率密度 pdf / k
     */
    public static double calculate(double theta, double kappa, double sigma, double t, double v0, double v) {
        double sigma2 = sigma * sigma;
        double expTerm = FastMath.exp(-kappa * t);
        double oneMinusExp = 1.0 - expTerm;

        // 1. 计算自由度 (df): 4 * theta * kappa / sigma^2
        double df = (4.0 * theta * kappa) / sigma2;

        // 2. 计算非中心参数 (ncp): 4 * kappa * exp(-kappa*t) * v0 / (sigma^2 * (1 - exp(-kappa*t)))
        double ncp = (4.0 * kappa * expTerm * v0) / (sigma2 * oneMinusExp);

        // 3. 计算缩放因子 k (这是 CIR 概率分布转换的关键)
        // 在金融公式中，k 通常等于 (sigma^2 * (1 - exp(-kappa*t))) / (4 * kappa)
        double k = (sigma2 * oneMinusExp) / (4.0 * kappa);

        // 4. 计算 pdf(x; df, ncp) 其中 x = v/k
        double x = v / k;
        double pdfValue = nonCentralChiSquaredPdf(x, df, ncp);

        // 5. 返回 pdf / k
        return pdfValue / k;
    }

    /**
     * 实现非中心卡方分布的 PDF。
     *
     * <p>Follows {@code boost::math::pdf(non_central_chi_squared_distribution)},
     * which for a non-centrality parameter above 50 evaluates the Poisson
     * weighted central chi-square series instead of the Bessel form.  The
     * Bessel form is numerically unusable for the very large degrees of
     * freedom and arguments produced by the CIR tails (the asymptotic series
     * for {@code I_nu} is outside its convergence region), whereas the series
     * is centred at its largest term and is stable.</p>
     */
    private static double nonCentralChiSquaredPdf(double x, double df, double ncp) {
        if (x <= 0) return 0.0;

        if (ncp <= 0) {
            // 当 ncp 为 0 时，退化为普通卡方分布
            return FastMath.exp((df / 2.0 - 1.0) * FastMath.log(x) - x / 2.0
                    - (df / 2.0) * FastMath.log(2.0) - Gamma.logGamma(df / 2.0));
        }

        final double nu = (df / 2.0) - 1.0;

        if (ncp > 50.0) {
            return nonCentralChiSquarePdfSeries(x, df, ncp);
        }

        // 使用对数空间防止中间值溢出 (Log-space calculation)
        // 对应公式: 0.5 * exp(-(x+ncp)/2) * (x/ncp)^(nu/2) * I_nu(sqrt(x*ncp))
        final double logCoef = -0.5 * (x + ncp)
                + (nu / 2.0) * FastMath.log(x / ncp);

        // Boost falls back to the series when the leading exponential of the
        // Bessel form would overflow even though the final result would not.
        if (FastMath.abs(logCoef) >= FastMath.log(Double.MAX_VALUE) / 4.0) {
            return nonCentralChiSquarePdfSeries(x, df, ncp);
        }

        // 调用修正贝塞尔函数（对数空间，避免大参数溢出）
        return FastMath.exp(FastMath.log(0.5) + logCoef
                + logModifiedBesselI(nu, FastMath.sqrt(ncp * x)));
    }

    /**
     * Boost's {@code non_central_chi_square_pdf}: the Poisson weighted sum of
     * central chi-square densities, started at the largest term and summed in
     * both directions.
     */
    private static double nonCentralChiSquarePdfSeries(double x, double df,
                                                       double ncp) {
        final double x2 = x / 2.0;
        final double n2 = df / 2.0;
        final double l2 = ncp / 2.0;
        final long k = (long) Math.floor(l2);

        double pois = gammaPDerivative(k + 1.0, l2)
                * gammaPDerivative(n2 + k, x2);
        if (pois == 0.0) {
            return 0.0;
        }

        double sum = 0.0;
        double poisb = pois;
        long i = k;
        for (;; ++i) {
            sum += pois;
            if (pois / sum < EPSILON) {
                break;
            }
            if (i - k >= MAX_SERIES_ITERATIONS) {
                throw new ArithmeticException(
                        "non-central chi-square pdf series did not converge");
            }
            pois *= l2 * x2 / ((i + 1.0) * (n2 + i));
        }
        for (i = k - 1; i >= 0; --i) {
            poisb *= (i + 1.0) * (n2 + i) / (l2 * x2);
            sum += poisb;
            if (poisb / sum < EPSILON) {
                break;
            }
        }
        return sum / 2.0;
    }

    /** Boost {@code gamma_p_derivative(a, x)}: x^(a-1) exp(-x) / Gamma(a). */
    private static double gammaPDerivative(double a, double x) {
        return FastMath.exp((a - 1.0) * FastMath.log(x) - x
                - Gamma.logGamma(a));
    }

    /**
     * Logarithm of the modified Bessel function I_nu(x).
     */
    private static double logModifiedBesselI(double nu, double x) {
        if (x < 13.0) {
            return FastMath.log(modifiedBesselI(nu, x));
        } else {
            return x - 0.5 * FastMath.log(2.0 * Math.PI * x)
                    + FastMath.log(asymptoticSeries(nu, x));
        }
    }

    /**
     * 修正贝塞尔函数 I_nu(x)
     * 对应你最初提供的 C++ 模板实现逻辑
     */
    private static double modifiedBesselI(double nu, double x) {
        if (x < 13.0) {
            // 小值使用级数展开
            double alpha = FastMath.pow(0.5 * x, nu) / FastMath.exp(Gamma.logGamma(1.0 + nu));
            double y = 0.25 * x * x;
            double sum = alpha;
            double bk = alpha;
            for (int k = 1; k < 1000; k++) {
                bk *= y / (k * (k + nu));
                sum += bk;
                if (FastMath.abs(bk) < FastMath.abs(sum) * Precision.EPSILON) break;
            }
            return sum;
        } else {
            // 大值使用渐近展开 (Asymptotic Expansion)
            return FastMath.exp(x) * asymptoticSeries(nu, x)
                    / FastMath.sqrt(2.0 * FastMath.PI * x);
        }
    }

    /**
     * Asymptotic series factor for I_nu(x), x >= 13.
     */
    private static double asymptoticSeries(double nu, double x) {
        double s = 1.0;
        double na_k = 1.0;
        double da_k = 1.0;
        double nu2 = 4.0 * nu * nu;
        for (int k = 1; k < 30; k++) {
            na_k *= (nu2 - FastMath.pow(2.0 * k - 1.0, 2));
            da_k *= (8.0 * k * x);
            double ak = na_k / da_k;
            s += ak;
            if (FastMath.abs(ak) < Precision.EPSILON) break;
        }
        return s;
    }
}
