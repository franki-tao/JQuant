package jquant.math.distributions;

import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.M_TWOPI;

public class BivariateCumulativeStudentDistribution {
    private final double epsilon = 1.0e-8;
    private int n_;
    private double rho_;


    public BivariateCumulativeStudentDistribution(int n, double rho) {
        n_ = n;
        rho_ = rho;
    }

    public double value(double x, double y) {
        return P_n(x, y, n_, rho_);
    }

    private double sign(double val) {
        return val == 0.0 ? 0.0
                : (val < 0.0 ? -1.0 : 1.0);
    }

    /* unlike the atan2 function in C++ that gives results in
           [-pi,pi], this returns a value in [0, 2*pi]
        */
    private double arctan(double x, double y) {
        double res = Math.atan2(x, y);
        return res >= 0.0 ? res : res + 2 * M_PI;
    }

    private double f_x(double m, double h, double k, double rho) {
        double unCor = 1 - rho * rho;
        double sub = Math.pow(h - rho * k, 2);
        double denom = sub + unCor * (m + k * k);
        if (denom < epsilon)
            return 0.0; // limit case for rho = +/-1.0
        return sub / (sub + unCor * (m + k * k));
    }

    private double P_n(double h, double k, int n, double rho) {
        double unCor = 1.0 - rho * rho;

        double div = 4 * Math.sqrt(n * M_PI);
        double xHK = f_x(n, h, k, rho);
        double xKH = f_x(n, k, h, rho);
        double divH = 1 + h * h / n;
        double divK = 1 + k * k / n;
        double sgnHK = sign(h - rho * k);
        double sgnKH = sign(k - rho * h);

        if (n % 2 == 0) { // n is even, equation (10)
            // first line of (10)
            double res = arctan(Math.sqrt(unCor), -rho) / M_TWOPI;

            // second line of (10)
            double dgM = 2 * (1 - xHK);  // multiplier for dgj
            double gjM = sgnHK * 2 / M_PI; // multiplier for g_j
            // initializations for j = 1:
            double f_j = Math.sqrt(M_PI / divK);
            double g_j = 1 + gjM * arctan(Math.sqrt(xHK), Math.sqrt(1 - xHK));
            double sum = f_j * g_j;
            if (n >= 4) {
                // different formulas for j = 2:
                f_j *= 0.5 / divK; // (2 - 1.5) / (double) (2 - 1) / divK;
                double dgj = gjM * Math.sqrt(xHK * (1 - xHK));
                g_j += dgj;
                sum += f_j * g_j;
                // and then the loop for the rest of the j's:
                for (int j = 3; j <= n / 2; ++j) {
                    f_j *= (j - 1.5) / (double) (j - 1) / divK;
                    dgj *= (double) (j - 2) / (2 * j - 3) * dgM;
                    g_j += dgj;
                    sum += f_j * g_j;
                }
            }
            res += k / div * sum;

            // third line of (10)
            dgM = 2 * (1 - xKH);
            gjM = sgnKH * 2 / M_PI;
            // initializations for j = 1:
            f_j = Math.sqrt(M_PI / divH);
            g_j = 1 + gjM * arctan(Math.sqrt(xKH), Math.sqrt(1 - xKH));
            sum = f_j * g_j;
            if (n >= 4) {
                // different formulas for j = 2:
                f_j *= 0.5 / divH; // (2 - 1.5) / (double) (2 - 1) / divK;
                double dgj = gjM * Math.sqrt(xKH * (1 - xKH));
                g_j += dgj;
                sum += f_j * g_j;
                // and then the loop for the rest of the j's:
                for (int j = 3; j <= n / 2; ++j) {
                    f_j *= (j - 1.5) / (double) (j - 1) / divH;
                    dgj *= (double) (j - 2) / (2 * j - 3) * dgM;
                    g_j += dgj;
                    sum += f_j * g_j;
                }
            }
            res += h / div * sum;
            return res;

        } else { // n is odd, equation (11)
            // first line of (11)
            double hk = h * k;
            double hkcn = hk + rho * n;
            double sqrtExpr = Math.sqrt(h * h - 2 * rho * hk + k * k + n * unCor);
            double res = arctan(Math.sqrt((double) (n)) * (-(h + k) * hkcn - (hk - n) * sqrtExpr),
                    (hk - n) * hkcn - n * (h + k) * sqrtExpr) / M_TWOPI;

            if (n > 1) {
                // second line of (11)
                double mult = (1 - xHK) / 2;
                // initializations for j = 1:
                double f_j = 2 / Math.sqrt(M_PI) / divK;
                double dgj = sgnHK * Math.sqrt(xHK);
                double g_j = 1 + dgj;
                double sum = f_j * g_j;
                // and then the loop for the rest of the j's:
                for (int j = 2; j <= (n - 1) / 2; ++j) {
                    f_j *= (double) (j - 1) / (j - 0.5) / divK;
                    dgj *= (double) (2 * j - 3) / (j - 1) * mult;
                    g_j += dgj;
                    sum += f_j * g_j;
                }
                res += k / div * sum;

                // third line of (11)
                mult = (1 - xKH) / 2;
                // initializations for j = 1:
                f_j = 2 / Math.sqrt(M_PI) / divH;
                dgj = sgnKH * Math.sqrt(xKH);
                g_j = 1 + dgj;
                sum = f_j * g_j;
                // and then the loop for the rest of the j's:
                for (int j = 2; j <= (n - 1) / 2; ++j) {
                    f_j *= (double) (j - 1) / (j - 0.5) / divH;
                    dgj *= (double) (2 * j - 3) / (j - 1) * mult;
                    g_j += dgj;
                    sum += f_j * g_j;
                }
                res += h / div * sum;
            }
            return res;
        }
    }
}
