package jquant.math.distributions;

import jquant.math.Function;
import jquant.math.integrals.TabulatedGaussLegendre;

import static jquant.math.MathUtils.M_PI;

public class BivariateCumulativeNormalDistributionWe04DP {

    private double correlation_;
    private CumulativeNormalDistribution cumnorm_;

    public BivariateCumulativeNormalDistributionWe04DP(double rho) {
        if (rho < -1 || rho > 1) {
            throw new IllegalArgumentException("rho must be in [-1,1]");
        }
        cumnorm_ = new CumulativeNormalDistribution();
        correlation_ = rho;
    }

    private class eqn3 implements Function {

        private double hk_;
        private double asr_;
        private double hs_;

        public eqn3(double h, double k, double asr) {
            // hk_(h * k), asr_(asr), hs_((h * h + k * k) / 2)
            this.hk_ = h * k;
            this.asr_ = asr;
            this.hs_ = (h * h + k * k) / 2;
        }

        @Override
        public double value(double x) {
            double sn = Math.sin(asr_ * (-x + 1) * 0.5);
            return Math.exp((sn * hk_ - hs_) / (1.0 - sn * sn));
        }
    }

    private class eqn6 implements Function {
        private double a_;
        private double c_;
        private double d_;
        private double bs_;
        private double hk_;

        public eqn6(double a_, double c_, double d_, double bs_, double hk_) {
            this.a_ = a_;
            this.c_ = c_;
            this.d_ = d_;
            this.bs_ = bs_;
            this.hk_ = hk_;
        }

        @Override
        public double value(double x) {
            double xs = a_ * (-x + 1);
            xs = Math.abs(xs*xs);
            double rs = Math.sqrt(1 - xs);
            double asr = -(bs_ / xs + hk_) / 2;
            if (asr > -100.0) {
                return (a_ * Math.exp(asr) *
                        (Math.exp(-hk_ * (1 - rs) / (2 * (1 + rs))) / rs -
                        (1 + c_ * xs * (1 + d_ * xs))));
            } else {
                return 0.0;
            }
        }
    }

    public double value(double x, double y) {
        /* The implementation is described at section 2.4 "Hybrid
           Numerical Integration Algorithms" of "Numerical Computation
           of Rectangular Bivariate an Trivariate Normal and t
           Probabilities", Genz (2004), Statistics and Computing 14,
           151-160. (available at
           www.sci.wsu.edu/jquant.math/faculty/henz/homepage)

           The Gauss-Legendre quadrature have been extracted to
           TabulatedGaussLegendre (x,w zero-based)

           Tthe functions ot be integrated numerically have been moved
           to classes eqn3 and eqn6

           Change some magic numbers to M_PI */

        TabulatedGaussLegendre gaussLegendreQuad = new TabulatedGaussLegendre(20);
        if (Math.abs(correlation_) < 0.3) {
            gaussLegendreQuad.order(6);
        } else if (Math.abs(correlation_) < 0.75) {
            gaussLegendreQuad.order(12);
        }

        double h = -x;
        double k = -y;
        double hk = h * k;
        double BVN = 0.0;

        if (Math.abs(correlation_) < 0.925)
        {
            if (Math.abs(correlation_) > 0)
            {
                double asr = Math.asin(correlation_);
                eqn3 f = new eqn3(h,k,asr);
                BVN = gaussLegendreQuad.value(f);
                BVN *= asr * (0.25 / M_PI);
            }
            BVN += cumnorm_.value(-h) * cumnorm_.value(-k);
        }
        else
        {
            if (correlation_ < 0)
            {
                k *= -1;
                hk *= -1;
            }
            if (Math.abs(correlation_) < 1)
            {
                double Ass = (1 - correlation_) * (1 + correlation_);
                double a = Math.sqrt(Ass);
                double bs = (h-k)*(h-k);
                double c = (4 - hk) / 8;
                double d = (12 - hk) / 16;
                double asr = -(bs / Ass + hk) / 2;
                if (asr > -100)
                {
                    BVN = a * Math.exp(asr) *
                        (1 - c * (bs - Ass) * (1 - d * bs / 5) / 3 +
                                c * d * Ass * Ass / 5);
                }
                if (-hk < 100)
                {
                    double B = Math.sqrt(bs);
                    BVN -= Math.exp(-hk / 2) * 2.506628274631 *
                        cumnorm_.value(-B / a) * B *
                        (1 - c * bs * (1 - d * bs / 5) / 3);
                }
                a /= 2;
                eqn6 f = new eqn6(a,c,d,bs,hk);
                BVN += gaussLegendreQuad.value(f);
                BVN /= (-2.0 * M_PI);
            }

            if (correlation_ > 0) {
                BVN += cumnorm_.value(-Math.max(h, k));
            } else {
                BVN *= -1;
                if (k > h) {
                    // evaluate cumnorm where it is most precise, that
                    // is in the lower tail because of double accuracy
                    // around 0.0 vs around 1.0
                    if (h >= 0) {
                        BVN += cumnorm_.value(-h) - cumnorm_.value(-k);
                    } else {
                        BVN += cumnorm_.value(k) - cumnorm_.value(h);
                    }
                }
            }
        }
        return BVN;
    }
}
