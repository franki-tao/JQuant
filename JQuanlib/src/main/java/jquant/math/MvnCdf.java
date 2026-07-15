package jquant.math;

import java.util.Random;

/**
 * 多元正态分布累积概率 P(a < X < b), X ~ N(mu, Sigma).
 * <p>
 * 算法（对齐 MATLAB mvncdf 的思路）：
 * n == 1        : 一维 normcdf
 * n == 2        : Genz 的确定性 bvnu（Gauss 求积，~1e-15，可复现）
 * n >= 3        : Genz-Bretz 分离变量法(SOV) + 变量重排序(cholperm)
 * + 随机化格点(Richtmyer) + 多组随机平移 -> 均值/误差估计
 * <p>
 * 参考: A. Genz, F. Bretz, "Computation of Multivariate Normal and t
 * Probabilities", Lecture Notes in Statistics 195, Springer (2009).
 */
public final class MvnCdf {

    /**
     * 计算结果：概率值与误差估计（高维为 QMC 标准误，低维 error=0）。
     */
    public static final class Result {
        public final double value;   // 概率估计
        public final double error;   // 误差估计（1 个标准误；低维为 0）

        Result(double v, double e) {
            this.value = v;
            this.error = e;
        }

        @Override
        public String toString() {
            return String.format("P = %.10f  (err ~ %.2e)", value, error);
        }
    }

    // ================= 公共入口 =================

    /**
     * 零均值：P(a < X < b), X ~ N(0, Sigma)。上下限可为 ±Infinity。
     */
    public static Result cdf(double[] a, double[] b, double[][] sigma) {
        return cdf(a, b, null, sigma, 12, 4000, 0L);
    }

    /**
     * CDF: P(X < x)，等价于下限全为 -Inf。
     */
    public static Result cdf(double[] x, double[][] sigma) {
        int n = x.length;
        double[] a = new double[n];
        java.util.Arrays.fill(a, Double.NEGATIVE_INFINITY);
        return cdf(a, x, null, sigma, 12, 4000, 0L);
    }

    /**
     * 完整入口。
     *
     * @param a,b     积分下/上限（原始坐标），可含 ±Infinity
     * @param mu      均值向量，null 表示零均值
     * @param sigma   协方差矩阵
     * @param batches 高维分支的随机平移组数（误差估计的样本数）
     * @param nPoints 每组格点数
     * @param seed    随机种子（保证可复现）
     */
    public static Result cdf(double[] a, double[] b, double[] mu,
                             double[][] sigma, int batches, int nPoints, long seed) {
        int n = a.length;
        // 标准化到零均值
        double[] al = a.clone(), bu = b.clone();
        if (mu != null) {
            for (int i = 0; i < n; i++) {
                al[i] -= mu[i];
                bu[i] -= mu[i];
            }
        }
        if (n == 1) {
            double s = Math.sqrt(sigma[0][0]);
            double p = Phi(bu[0] / s) - Phi(al[0] / s);
            return new Result(clamp01(p), 0.0);
        }
        if (n == 2) {
            double s1 = Math.sqrt(sigma[0][0]), s2 = Math.sqrt(sigma[1][1]);
            double r = sigma[0][1] / (s1 * s2);
            // 标准化限
            double l1 = al[0] / s1, u1 = bu[0] / s1, l2 = al[1] / s2, u2 = bu[1] / s2;
            double p = bvn(l1, u1, l2, u2, r);
            return new Result(clamp01(p), 0.0);
        }
        return genzBretz(al, bu, sigma, batches, nPoints, seed);
    }

    // ================= 高维: Genz-Bretz SOV =================

    private static Result genzBretz(double[] a, double[] b, double[][] sigma,
                                    int batches, int nPoints, long seed) {
        int n = a.length;
        // 1) 带变量重排序的 Cholesky（就地排列 a,b）
        double[][] L = new double[n][n];
        double[] la = a.clone(), lu = b.clone();
        cholperm(deepCopy(sigma), la, lu, L);      // L 下三角，la/lu 被重排

        // 2) 积分维数 = n-1；构造 Richtmyer 生成向量
        int q = n - 1;
        double[] gv = richtmyer(q);

        Random rng = new Random(seed);
        double[] est = new double[batches];
        for (int m = 0; m < batches; m++) {
            double[] shift = new double[q];
            for (int j = 0; j < q; j++) shift[j] = rng.nextDouble();
            double sum = 0.0;
            for (int k = 1; k <= nPoints; k++) {
                double[] w = new double[q];
                for (int j = 0; j < q; j++) {
                    double t = k * gv[j] + shift[j];
                    t -= Math.floor(t);
                    // baker/tent 变换：对光滑非周期被积函数降方差
                    w[j] = (t < 0.5) ? 2.0 * t : 2.0 - 2.0 * t;
                }
                sum += sovIntegrand(w, L, la, lu);
            }
            est[m] = sum / nPoints;
        }
        double mean = 0;
        for (double e : est) mean += e;
        mean /= batches;
        double var = 0;
        for (double e : est) var += (e - mean) * (e - mean);
        double stderr = batches > 1 ? Math.sqrt(var / (batches - 1) / batches) : 0.0;
        return new Result(clamp01(mean), stderr);
    }

    /**
     * SOV 被积函数：单点 w in [0,1]^(n-1) -> 概率贡献。
     */
    private static double sovIntegrand(double[] w, double[][] L, double[] a, double[] b) {
        int n = a.length;
        double[] y = new double[n];
        double di = Phi(a[0] / L[0][0]);
        double ei = Phi(b[0] / L[0][0]);
        double f = ei - di;
        for (int i = 1; i < n; i++) {
            double z = di + w[i - 1] * (ei - di);
            z = Math.max(1e-16, Math.min(1.0 - 1e-16, z));
            y[i - 1] = PhiInv(z);
            double s = 0.0;
            for (int kk = 0; kk < i; kk++) s += L[i][kk] * y[kk];
            di = Phi((a[i] - s) / L[i][i]);
            ei = Phi((b[i] - s) / L[i][i]);
            f *= (ei - di);
        }
        return f;
    }

    /**
     * Genz 变量重排序 + Cholesky（cholperm）。
     * 每步选出"期望区间概率最小"的变量优先积分，显著降方差。
     * 就地修改 a,b（重排），并写出下三角 L。sig 会被破坏。
     */
    private static void cholperm(double[][] sig, double[] a, double[] b, double[][] L) {
        int d = a.length;
        double[] z = new double[d];
        for (int j = 0; j < d; j++) {
            // 在 j..d-1 中选择期望区间概率最小的变量
            int kBest = j;
            double prBest = Double.POSITIVE_INFINITY;
            for (int i = j; i < d; i++) {
                double s = sig[i][i];
                for (int c = 0; c < j; c++) s -= L[i][c] * L[i][c];
                s = Math.sqrt(Math.max(s, 1e-300));
                double m = 0.0;
                for (int c = 0; c < j; c++) m += L[i][c] * z[c];
                double tl = (a[i] - m) / s, tu = (b[i] - m) / s;
                double pr = Phi(tu) - Phi(tl);
                if (pr < prBest) {
                    prBest = pr;
                    kBest = i;
                }
            }
            // 交换变量 j <-> kBest（sig 的行列、a、b、L 的行）
            if (kBest != j) {
                swapRowsCols(sig, j, kBest);
                double t;
                t = a[j];
                a[j] = a[kBest];
                a[kBest] = t;
                t = b[j];
                b[j] = b[kBest];
                b[kBest] = t;
                double[] tr = L[j];
                L[j] = L[kBest];
                L[kBest] = tr;
            }
            // 计算 L 的第 j 列
            double s = sig[j][j];
            for (int c = 0; c < j; c++) s -= L[j][c] * L[j][c];
            s = Math.sqrt(Math.max(s, 1e-300));
            L[j][j] = s;
            for (int i = j + 1; i < d; i++) {
                double v = sig[i][j];
                for (int c = 0; c < j; c++) v -= L[i][c] * L[j][c];
                L[i][j] = v / s;
            }
            // 更新 z[j] = 截断正态在 [tl,tu] 上的均值
            double m = 0.0;
            for (int c = 0; c < j; c++) m += L[j][c] * z[c];
            double tl = (a[j] - m) / s, tu = (b[j] - m) / s;
            double pr = Phi(tu) - Phi(tl);
            z[j] = (pr > 1e-300) ? (phi(tl) - phi(tu)) / pr : 0.5 * (tl + tu);
        }
    }

    // ================= 低维: 二维 Genz bvnu =================

    /**
     * 矩形概率 P(l1<X<u1, l2<Y<u2)，标准双变量正态、相关系数 r。
     */
    private static double bvn(double l1, double u1, double l2, double u2, double r) {
        // Phi2(x,y)=P(X<x,Y<y)=bvnu(-x,-y,r)
        double p = phi2(u1, u2, r) - phi2(l1, u2, r) - phi2(u1, l2, r) + phi2(l1, l2, r);
        return clamp01(p);
    }

    private static double phi2(double x, double y, double r) {
        return bvnu(-x, -y, r);
    }

    /**
     * P(X>dh, Y>dk)，标准双变量正态、相关系数 r。Genz(2004) 高斯求积。
     */
    private static double bvnu(double dh, double dk, double r) {
        if (dh == Double.POSITIVE_INFINITY || dk == Double.POSITIVE_INFINITY) return 0.0;
        if (dh == Double.NEGATIVE_INFINITY)
            return (dk == Double.NEGATIVE_INFINITY) ? 1.0 : Phi(-dk);
        if (dk == Double.NEGATIVE_INFINITY) return Phi(-dh);
        if (r == 0.0) return Phi(-dh) * Phi(-dk);

        double tp = 2.0 * Math.PI;
        double h = dh, k = dk, hk = h * k, bvn = 0.0;
        double ar = Math.abs(r);
        double[] w, x;
        if (ar < 0.3) {          // 6-point
            w = new double[]{0.1713244923791705, 0.3607615730481384, 0.4679139345726904};
            x = new double[]{0.9324695142031522, 0.6612093864662647, 0.2386191860831970};
        } else if (ar < 0.75) {  // 12-point
            w = new double[]{0.04717533638651177, 0.1069393259953183, 0.1600783285433464,
                    0.2031674267230659, 0.2334925365383547, 0.2491470458134029};
            x = new double[]{0.9815606342467191, 0.9041172563704750, 0.7699026741943050,
                    0.5873179542866171, 0.3678314989981802, 0.1252334085114689};
        } else {                 // 20-point
            w = new double[]{0.01761400713915212, 0.04060142980038694, 0.06267204833410906,
                    0.08327674157670475, 0.1019301198172404, 0.1181945319615184,
                    0.1316886384491766, 0.1420961093183821, 0.1491729864726037,
                    0.1527533871307259};
            x = new double[]{0.9931285991850949, 0.9639719272779138, 0.9122344282513259,
                    0.8391169718222188, 0.7463319064601508, 0.6360536807265150,
                    0.5108670019508271, 0.3737060887154196, 0.2277858511416451,
                    0.07652652113349733};
        }

        if (ar < 0.925) {
            double hs = (h * h + k * k) / 2.0, asr = Math.asin(r) / 2.0;
            double sum = 0.0;
            for (int i = 0; i < w.length; i++) {
                for (int is = -1; is <= 1; is += 2) {
                    double sn = Math.sin(asr * (is * x[i] + 1.0));
                    sum += w[i] * Math.exp((sn * hk - hs) / (1.0 - sn * sn));
                }
            }
            bvn = sum * asr / tp + Phi(-h) * Phi(-k);
        } else {
            if (r < 0) {
                k = -k;
                hk = -hk;
            }
            if (ar < 1.0) {
                double as = (1.0 - r) * (1.0 + r), aa = Math.sqrt(as);
                double bs = (h - k) * (h - k);
                double c = (4.0 - hk) / 8.0, dd = (12.0 - hk) / 16.0;
                double asr = -(bs / as + hk) / 2.0;
                if (asr > -100.0)
                    bvn = aa * Math.exp(asr) *
                            (1.0 - c * (bs - as) * (1.0 - dd * bs / 5.0) / 3.0 + c * dd * as * as / 5.0);
                if (hk > -100.0) {
                    double bb = Math.sqrt(bs);
                    double sp = Math.sqrt(tp) * Phi(-bb / aa);
                    bvn -= Math.exp(-hk / 2.0) * sp * bb * (1.0 - c * bs * (1.0 - dd * bs / 5.0) / 3.0);
                }
                double a2 = aa / 2.0;
                for (int i = 0; i < w.length; i++) {
                    for (int is = -1; is <= 1; is += 2) {
                        double xs = a2 * (is * x[i] + 1.0);
                        xs = xs * xs;
                        double rs = Math.sqrt(1.0 - xs);
                        double asr1 = -(bs / xs + hk) / 2.0;
                        if (asr1 > -100.0) {
                            double sp = 1.0 + c * xs * (1.0 + dd * xs);
                            double ep = Math.exp(-hk * xs / (2.0 * (1.0 + rs) * (1.0 + rs))) / rs;
                            bvn += a2 * w[i] * Math.exp(asr1) * (ep - sp);
                        }
                    }
                }
                bvn = -bvn / tp;
            }
            if (r > 0) {
                bvn += Phi(-Math.max(h, k));
            } else if (h >= k) {
                bvn = -bvn;
            } else {
                double lPart = (h < 0) ? (Phi(k) - Phi(h)) : (Phi(-h) - Phi(-k));
                bvn = lPart - bvn;
            }
        }
        return clamp01(bvn);
    }

    // ================= 数值原语 =================

    /**
     * 标准正态密度。
     */
    private static double phi(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI);
    }

    /**
     * 标准正态 CDF（West 2009，双精度精度）。
     */
    static double Phi(double x) {
        if (x == Double.POSITIVE_INFINITY) return 1.0;
        if (x == Double.NEGATIVE_INFINITY) return 0.0;
        double xa = Math.abs(x), c;
        if (xa > 37.0) {
            c = 0.0;
        } else {
            double e = Math.exp(-xa * xa / 2.0);
            if (xa < 7.07106781186547) {
                double b = 3.52624965998911e-02 * xa + 0.700383064443688;
                b = b * xa + 6.37396220353165;
                b = b * xa + 33.912866078383;
                b = b * xa + 112.079291497871;
                b = b * xa + 221.213596169931;
                b = b * xa + 220.206867912376;
                c = e * b;
                double d = 8.83883476483184e-02 * xa + 1.75566716318264;
                d = d * xa + 16.064177579207;
                d = d * xa + 86.7807322029461;
                d = d * xa + 296.564248779674;
                d = d * xa + 637.333633378831;
                d = d * xa + 793.826512519948;
                d = d * xa + 440.413735824752;
                c = c / d;
            } else {
                double b = xa + 0.65;
                b = xa + 4.0 / b;
                b = xa + 3.0 / b;
                b = xa + 2.0 / b;
                b = xa + 1.0 / b;
                c = e / b / 2.506628274631;
            }
        }
        return x > 0 ? 1.0 - c : c;
    }

    /**
     * 标准正态逆 CDF（Acklam 初值 + 一步 Halley 精化 -> 双精度）。
     */
    static double PhiInv(double p) {
        if (p <= 0.0) return Double.NEGATIVE_INFINITY;
        if (p >= 1.0) return Double.POSITIVE_INFINITY;
        final double[] A = {-3.969683028665376e+01, 2.209460984245205e+02,
                -2.759285104469687e+02, 1.383577518672690e+02,
                -3.066479806614716e+01, 2.506628277459239e+00};
        final double[] B = {-5.447609879822406e+01, 1.615858368580409e+02,
                -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01};
        final double[] C = {-7.784894002430293e-03, -3.223964580411365e-01,
                -2.400758277161838e+00, -2.549732539343734e+00,
                4.374664141464968e+00, 2.938163982698783e+00};
        final double[] D = {7.784695709041462e-03, 3.224671290700398e-01,
                2.445134137142996e+00, 3.754408661907416e+00};
        final double pl = 0.02425, ph = 1.0 - pl;
        double x;
        if (p < pl) {
            double q = Math.sqrt(-2.0 * Math.log(p));
            x = (((((C[0] * q + C[1]) * q + C[2]) * q + C[3]) * q + C[4]) * q + C[5]) /
                    ((((D[0] * q + D[1]) * q + D[2]) * q + D[3]) * q + 1.0);
        } else if (p <= ph) {
            double q = p - 0.5, r = q * q;
            x = (((((A[0] * r + A[1]) * r + A[2]) * r + A[3]) * r + A[4]) * r + A[5]) * q /
                    (((((B[0] * r + B[1]) * r + B[2]) * r + B[3]) * r + B[4]) * r + 1.0);
        } else {
            double q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            x = -(((((C[0] * q + C[1]) * q + C[2]) * q + C[3]) * q + C[4]) * q + C[5]) /
                    ((((D[0] * q + D[1]) * q + D[2]) * q + D[3]) * q + 1.0);
        }
        // Halley 精化一步
        double e = Phi(x) - p;
        double u = e / phi(x);
        x -= u / (1.0 + x * u / 2.0);
        return x;
    }

    /**
     * Richtmyer 生成向量: gv[j] = frac(sqrt(prime_{j+1}))。
     */
    private static double[] richtmyer(int q) {
        int[] primes = firstPrimes(q);
        double[] gv = new double[q];
        for (int j = 0; j < q; j++) {
            double s = Math.sqrt(primes[j]);
            gv[j] = s - Math.floor(s);
        }
        return gv;
    }

    private static int[] firstPrimes(int n) {
        int[] p = new int[Math.max(n, 1)];
        int cnt = 0, cand = 2;
        while (cnt < n) {
            boolean prime = true;
            for (int d = 2; d * d <= cand; d++)
                if (cand % d == 0) {
                    prime = false;
                    break;
                }
            if (prime) p[cnt++] = cand;
            cand++;
        }
        return p;
    }

    private static double[][] deepCopy(double[][] m) {
        double[][] c = new double[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    private static void swapRowsCols(double[][] m, int i, int j) {
        double[] tr = m[i];
        m[i] = m[j];
        m[j] = tr;      // 交换行
        for (double[] row : m) {
            double t = row[i];
            row[i] = row[j];
            row[j] = t;
        } // 交换列
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ================= 自检 =================
    public static void main(String[] args) {
        final double INF = Double.POSITIVE_INFINITY, NINF = Double.NEGATIVE_INFINITY;

        System.out.println("== 数值原语 ==");
        chk("Phi(0)", Phi(0.0), 0.5);
        chk("Phi(1.96)", Phi(1.96), 0.9750021048);
        chk("Phi(-3)", Phi(-3.0), 0.0013498980);
        chk("PhiInv(.975)", PhiInv(0.975), 1.9599639845);
        chk("PhiInv(.5)", PhiInv(0.5), 0.0);

        System.out.println("\n== n=2: 解析对照 P(X<0,Y<0)=1/4+asin(r)/2pi ==");
        for (double r : new double[]{-0.7, -0.3, 0.0, 0.5, 0.9}) {
            double[][] S = {{1, r}, {r, 1}};
            double got = cdf(new double[]{0, 0}, S).value;
            double exact = 0.25 + Math.asin(r) / (2 * Math.PI);
            chk("r=" + r, got, exact);
        }

        System.out.println("\n== n=3: SOV vs 蛮力 MC (r=0.5, 全 <0) ==");
        double[][] S3 = {{1, .5, .5}, {.5, 1, .5}, {.5, .5, 1}};
        Result r3 = cdf(new double[]{0, 0, 0}, S3);
        double mc3 = bruteMC(new double[]{NINF, NINF, NINF},
                new double[]{0, 0, 0}, S3, 4_000_000, 1);
        System.out.printf("  SOV = %.8f (err %.1e)   MC = %.8f%n", r3.value, r3.error, mc3);

        System.out.println("\n== n=5: 矩形区域 SOV vs MC ==");
        double[][] S5 = new double[5][5];
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++) S5[i][j] = (i == j) ? 1.0 : 0.4;
        double[] a5 = {-1, -0.5, NINF, -2, -1};
        double[] b5 = {1.5, 2, 1, INF, 0.5};
        Result r5 = cdf(a5, b5, null, S5, 20, 8000, 7L);
        double mc5 = bruteMC(a5, b5, S5, 8_000_000, 2);
        System.out.printf("  SOV = %.8f (err %.1e)   MC = %.8f   |diff|=%.2e%n",
                r5.value, r5.error, mc5, Math.abs(r5.value - mc5));

        System.out.println("\n== 可复现性（同 seed 两次调用）==");
        double x1 = cdf(a5, b5, null, S5, 20, 8000, 7L).value;
        double x2 = cdf(a5, b5, null, S5, 20, 8000, 7L).value;
        System.out.printf("  %.12f == %.12f  -> %s%n", x1, x2, (x1 == x2 ? "OK" : "FAIL"));
    }

    private static void chk(String name, double got, double exp) {
        double err = Math.abs(got - exp);
        System.out.printf("  %-14s got=%.10f exp=%.10f  |err|=%.2e %s%n",
                name, got, exp, err, err < 1e-6 ? "OK" : "*** FAIL ***");
    }

    /**
     * 独立蛮力 Monte Carlo，用于交叉验证。
     */
    private static double bruteMC(double[] a, double[] b, double[][] sigma, int N, long seed) {
        int n = a.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j <= i; j++) {
                double s = sigma[i][j];
                for (int k = 0; k < j; k++) s -= L[i][k] * L[j][k];
                L[i][j] = (i == j) ? Math.sqrt(s) : s / L[j][j];
            }
        Random rng = new Random(seed);
        long hit = 0;
        double[] z = new double[n], x = new double[n];
        for (int t = 0; t < N; t++) {
            for (int i = 0; i < n; i++) z[i] = rng.nextGaussian();
            boolean in = true;
            for (int i = 0; i < n; i++) {
                double v = 0;
                for (int k = 0; k <= i; k++) v += L[i][k] * z[k];
                x[i] = v;
                if (v <= a[i] || v >= b[i]) {
                    in = false;
                    break;
                }
            }
            if (in) hit++;
        }
        return (double) hit / N;
    }
}