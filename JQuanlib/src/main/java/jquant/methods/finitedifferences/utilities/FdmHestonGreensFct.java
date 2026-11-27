package jquant.methods.finitedifferences.utilities;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmSquareRootFwdOp;
import jquant.processes.HestonProcess;

import static jquant.Compounding.Continuous;
import static jquant.math.MathUtils.M_TWOPI;
import static jquant.time.Frequency.ANNUAL;

/**
 * Heston Green's function on a finite-difference mesher.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/fdmhestongreensfct.{hpp,cpp}}.
 */
public class FdmHestonGreensFct {

    public enum Algorithm { ZeroCorrelation, Gaussian, SemiAnalytical }

    private final double l0_;
    private final FdmMesher mesher_;
    private final HestonProcess process_;
    private final FdmSquareRootFwdOp.TransformationType trafoType_;

    public FdmHestonGreensFct(FdmMesher mesher,
                              HestonProcess process,
                              FdmSquareRootFwdOp.TransformationType trafoType) {
        this(mesher, process, trafoType, 1.0);
    }

    public FdmHestonGreensFct(FdmMesher mesher,
                              HestonProcess process,
                              FdmSquareRootFwdOp.TransformationType trafoType,
                              double l0) {
        l0_ = l0;
        mesher_ = mesher;
        process_ = process;
        trafoType_ = trafoType;
    }

    public Array get(double t, Algorithm algorithm) {
        final double r = process_.riskFreeRate().getValue()
                .forwardRate(0.0, t, Continuous, ANNUAL, false).rate();
        final double q = process_.dividendYield().getValue()
                .forwardRate(0.0, t, Continuous, ANNUAL, false).rate();

        final double s0 = process_.s0().getValue().value();
        final double v0 = process_.v0();
        final double x0 = Math.log(s0) + (r - q - 0.5 * v0 * l0_ * l0_) * t;

        final double rho = process_.rho();
        final double theta = process_.theta();
        final double kappa = process_.kappa();
        final double sigma = process_.sigma();

        final Array p = new Array(mesher_.layout().size());
        for (FdmLinearOpIterator iter : mesher_.layout()) {
            final double x = mesher_.location(iter, 0);
            final double v = (trafoType_ != FdmSquareRootFwdOp.TransformationType.Log)
                    ? mesher_.location(iter, 1)
                    : Math.exp(mesher_.location(iter, 1));

            double retVal;
            switch (algorithm) {
                case ZeroCorrelation: {
                    final double sd_x = l0_ * Math.sqrt(v0 * t);
                    final double p_x = 1.0 / Math.sqrt(2.0 * Math.PI) / sd_x
                            * Math.exp(-0.5 * squared((x - x0) / sd_x));
                    final double p_v = new SquareRootProcessRNDCalculator(
                            v0, kappa, theta, sigma).pdf(v, t);
                    retVal = p_v * p_x;
                    break;
                }
                case SemiAnalytical:
                    retVal = process_.pdf(x, v, t, 1e-4);
                    break;
                case Gaussian: {
                    final double sd_x = l0_ * Math.sqrt(v0 * t);
                    final double sd_v = sigma * Math.sqrt(v0 * t);
                    final double z0 = v0 + kappa * (theta - v0) * t;
                    retVal = 1.0 / (M_TWOPI * sd_x * sd_v * Math.sqrt(1 - rho * rho))
                            * Math.exp(-(squared((x - x0) / sd_x)
                            + squared((v - z0) / sd_v)
                            - 2 * rho * (x - x0) * (v - z0) / (sd_x * sd_v))
                            / (2 * (1 - rho * rho)));
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown algorithm");
            }

            switch (trafoType_) {
                case Log:
                    retVal *= v;
                    break;
                case Plain:
                    break;
                case Power:
                    retVal *= Math.pow(v, 1.0 - 2 * kappa * theta / (sigma * sigma));
                    break;
                default:
                    throw new IllegalArgumentException("unknown transformation type");
            }

            p.set(iter.index(), retVal);
        }

        return p;
    }

    private static double squared(double x) {
        return x * x;
    }
}
