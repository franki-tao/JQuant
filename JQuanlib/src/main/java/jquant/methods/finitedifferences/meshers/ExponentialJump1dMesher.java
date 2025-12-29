package jquant.methods.finitedifferences.meshers;
/*! Mesher for a exponential jump process with high
    mean reversion rate and low jump intensity
    \f[
    \begin{array}{rcl}
    dY_t  &=& -\beta Y_{t-}dt + J_tdN_t \\
    \omega(J)&=&\frac{1}{\eta_u}e^{-\frac{1}{\eta_u}J}
    \end{array}
    \f]
*/
import jquant.math.GammaFunction;
import jquant.math.integrals.GaussLobattoIntegral;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.incompleteGammaFunction;

/*! References:
    B. Hambly, S. Howison, T. Kluge, Modelling spikes and pricing
    swing options in electricity markets,
    http://people.maths.ox.ac.uk/hambly/PDF/Papers/elec.pdf
*/
public class ExponentialJump1dMesher extends Fdm1dMesher {
    private double beta_, jumpIntensity_, eta_;

    public ExponentialJump1dMesher(int steps, double beta, double jumpIntensity, double eta, double eps) {
        super(steps);
        beta_ = beta;
        jumpIntensity_ = jumpIntensity;
        eta_ = eta;
        QL_REQUIRE(eps > 0.0 && eps < 1.0, "eps > 0.0 and eps < 1.0");
        QL_REQUIRE(steps > 1, "minimum number of steps is two");

        final double start = 0.0;
        final double end = 1.0 - eps;
        final double dx = (end - start) / (steps - 1);
        final double scale = 1 / (1 - Math.exp(-beta / jumpIntensity));

        for (int i = 0; i < steps; ++i) {
            final double p = start + i * dx;
            locations_.set(i, scale * (-1.0 / eta * Math.log(1.0 - p)));
        }

        for (int i = 0; i < steps - 1; ++i) {
            double t = locations_.get(i + 1) - locations_.get(i);
            dplus_.set(i, t);
            dminus_.set(i + 1, t);
//            dminus_[i + 1] = dplus_[i] = locations_[i + 1] - locations_[i];
        }
        dplus_.set(size() - 1, Double.NaN);
        dminus_.set(0, Double.NaN);
    }

    // approximation. see Hambly et.al.
    public double jumpSizeDensity(double x) {
        final double a = 1.0 - jumpIntensity_ / beta_;
        final double gammaValue = Math.exp(new GammaFunction().logValue(jumpIntensity_ / beta_));
        return Math.exp(-x * eta_) * Math.pow(x, -a) * Math.pow(eta_, 1.0 - a) / gammaValue;
    }

    public double jumpSizeDensity(double x, double t) {
        final double a = 1.0 - jumpIntensity_ / beta_;
        final double norm = 1.0 - Math.exp(-jumpIntensity_ * t);
        final double gammaValue
                = Math.exp(new GammaFunction().logValue(1.0 - jumpIntensity_ / beta_));
        return jumpIntensity_ * gammaValue / norm
                * (incompleteGammaFunction(a, x * Math.exp(beta_ * t) * eta_)
                - incompleteGammaFunction(a, x * eta_))
                * Math.pow(eta_, jumpIntensity_ / beta_)
                / (beta_ * Math.pow(x, a));
    }

    public double jumpSizeDistribution(double x) {
        final double a = jumpIntensity_ / beta_;
        final double xmin = Math.min(x, QL_EPSILON);
        final double gammaValue
                = Math.exp(new GammaFunction().logValue(jumpIntensity_ / beta_));

        final double lowerEps =
                (Math.pow(xmin, a) / a - Math.pow(xmin, a + 1) / (a + 1)) / gammaValue;

        return lowerEps + new GaussLobattoIntegral(10000, 1e-12).value(this::jumpSizeDensity, xmin / eta_, Math.max(x, xmin / eta_));
    }

    public double jumpSizeDistribution(double x, double t) {
        final double xmin = Math.min(x, 1.0e-100);
        return new GaussLobattoIntegral(1000000, 1e-12).value(x1 -> jumpSizeDensity(x1, t), xmin, Math.max(x, xmin));
    }
}
