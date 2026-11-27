package jquant.methods.finitedifferences.meshers;

import jquant.math.Point;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.processes.GeneralizedBlackScholesProcess;

import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * One-dimensional mesher for the Black-Scholes process (in log-spot) that
 * concentrates around several strikes.
 *
 * <p>Port of QuantLib
 * {@code ql/methods/finitedifferences/meshers/fdmblackscholesmultistrikemesher.{hpp,cpp}}.
 */
public class FdmBlackScholesMultiStrikeMesher extends Fdm1dMesher {

    public FdmBlackScholesMultiStrikeMesher(
            int size,
            final GeneralizedBlackScholesProcess process,
            double maturity,
            final List<Double> strikes,
            double eps,
            double scaleFactor,
            final Point<Double, Double> cPoint) {
        super(size);

        final double spot = process.x0();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        final double d = process.dividendYield().getValue()
                .discount(maturity, false)
                / process.riskFreeRate().getValue().discount(maturity, false);
        final double minStrike = Collections.min(strikes);
        final double maxStrike = Collections.max(strikes);

        final double Fmin = spot * spot / maxStrike * d;
        final double Fmax = spot * spot / minStrike * d;

        QL_REQUIRE(Fmin > 0.0, "negative forward given");

        // Set the grid boundaries
        final double normInvEps =
                new InverseCumulativeNormal().value(1 - eps);
        final double sigmaSqrtTmin =
                process.blackVolatility().getValue()
                        .blackVol(maturity, minStrike, false)
                        * Math.sqrt(maturity);
        final double sigmaSqrtTmax =
                process.blackVolatility().getValue()
                        .blackVol(maturity, maxStrike, false)
                        * Math.sqrt(maturity);

        final double xMin = Math.min(
                0.8 * Math.log(0.8 * spot * spot / maxStrike),
                Math.log(Fmin) - sigmaSqrtTmin * normInvEps * scaleFactor
                        - sigmaSqrtTmin * sigmaSqrtTmin / 2.0);
        final double xMax = Math.max(
                1.2 * Math.log(0.8 * spot * spot / minStrike),
                Math.log(Fmax) + sigmaSqrtTmax * normInvEps * scaleFactor
                        - sigmaSqrtTmax * sigmaSqrtTmax / 2.0);

        Fdm1dMesher helper;
        if (cPoint != null
                && !Double.isNaN(cPoint.getFirst())
                && Math.log(cPoint.getFirst()) >= xMin
                && Math.log(cPoint.getFirst()) <= xMax) {
            helper = new Concentrating1dMesher(
                    xMin, xMax, size,
                    new Point<>(Math.log(cPoint.getFirst()),
                            cPoint.getSecond()), false);
        } else {
            helper = new Uniform1dMesher(xMin, xMax, size);
        }

        locations_ = helper.locations();
        for (int i = 0; i < locations_.size(); ++i) {
            dplus_.set(i, helper.dplus(i));
            dminus_.set(i, helper.dminus(i));
        }
    }
}
