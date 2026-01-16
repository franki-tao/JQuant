package jquant.processes.impl;

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

public class ManualNonCentralChiSquared {
    private static final double EPSILON = Precision.EPSILON;

    /**
     * 计算非中心卡方分布的 PDF: pdf(x; df, ncp)
     * 对应 C++ 中的 boost::math::pdf
     */
    public static double density(double x, double df, double ncp) {
        if (x <= 0) return 0.0;
        if (ncp <= 0) {
            // 当 ncp 为 0 时，退化为普通卡方分布
            return FastMath.exp((df / 2.0 - 1.0) * FastMath.log(x) - x / 2.0
                    - (df / 2.0) * FastMath.log(2.0) - Gamma.logGamma(df / 2.0));
        }

        double halfDf = df / 2.0;
        double nu = halfDf - 1.0;
        double sqrtXNcp = FastMath.sqrt(ncp * x);

        // 使用对数空间计算前面的系数，防止大数值溢出
        // term = -0.5 * (x + ncp) + (nu / 2.0) * log(x / ncp)
        double logCoef = -0.5 * (x + ncp) + (nu / 2.0) * FastMath.log(x / ncp);

        // 计算修正贝塞尔函数 I_nu(sqrt(ncp * x))
        double besselI = modifiedBesselI(nu, sqrtXNcp);

        return 0.5 * FastMath.exp(logCoef) * besselI;
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
     * 实现非中心卡方分布的 PDF
     */
    private static double nonCentralChiSquaredPdf(double x, double df, double ncp) {
        if (x <= 0) return 0.0;

        double nu = (df / 2.0) - 1.0;
        double sqrtXNcp = FastMath.sqrt(ncp * x);

        // 使用对数空间防止中间值溢出 (Log-space calculation)
        // 对应公式: 0.5 * exp(-(x+ncp)/2) * (x/ncp)^(nu/2) * I_nu(sqrt(x*ncp))
        double logCoef = -0.5 * (x + ncp) + (nu / 2.0) * FastMath.log(x / ncp);

        // 调用修正贝塞尔函数
        double besselI = modifiedBesselI(nu, sqrtXNcp);

        return 0.5 * FastMath.exp(logCoef) * besselI;
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
            return FastMath.exp(x) * s / FastMath.sqrt(2.0 * FastMath.PI * x);
        }
    }
}
