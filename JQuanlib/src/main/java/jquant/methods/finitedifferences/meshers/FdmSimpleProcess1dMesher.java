package jquant.methods.finitedifferences.meshers;

import jquant.StochasticProcess1D;
import jquant.math.distributions.InverseCumulativeNormal;

import static jquant.math.MathUtils.NULL_REAL;

/**
 * One-dimensional grid mesher for a simple stochastic process.
 *
 * <p>Port of {@code ql/methods/finitedifferences/meshers/fdmsimpleprocess1dmesher.{hpp,cpp}}.
 */
public class FdmSimpleProcess1dMesher extends Fdm1dMesher {
    public FdmSimpleProcess1dMesher(int size,
                                    final StochasticProcess1D process,
                                    double maturity,
                                    int tAvgSteps,
                                    double epsilon,
                                    double mandatoryPoint) {
        super(size);

        for (int i = 0; i < size; ++i) {
            locations_.set(i, 0.0);
        }

        for (int l = 1; l <= tAvgSteps; ++l) {
            final double t = (maturity * l) / tAvgSteps;

            final double mp = (mandatoryPoint != NULL_REAL)
                    ? mandatoryPoint : process.x0();

            final double qMin = Math.min(mp, Math.min(process.x0(),
                    process.evolve(0, process.x0(), t,
                            new InverseCumulativeNormal().value(epsilon))));
            final double qMax = Math.max(mp, Math.max(process.x0(),
                    process.evolve(0, process.x0(), t,
                            new InverseCumulativeNormal().value(1 - epsilon))));

            final double dp = (1 - 2 * epsilon) / (size - 1);
            double p = epsilon;
            locations_.set(0, locations_.get(0) + qMin);

            for (int i = 1; i < size - 1; ++i) {
                p += dp;
                locations_.set(i, locations_.get(i)
                        + process.evolve(0, process.x0(), t,
                                new InverseCumulativeNormal().value(p)));
            }
            locations_.set(size - 1, locations_.get(size - 1) + qMax);
        }

        for (int i = 0; i < size; ++i) {
            locations_.set(i, locations_.get(i) / tAvgSteps);
        }

        for (int i = 0; i < size - 1; ++i) {
            final double d = locations_.get(i + 1) - locations_.get(i);
            dminus_.set(i + 1, d);
            dplus_.set(i, d);
        }
        dplus_.set(size - 1, NULL_REAL);
        dminus_.set(0, NULL_REAL);
    }

    public FdmSimpleProcess1dMesher(int size,
                                    final StochasticProcess1D process,
                                    double maturity,
                                    int tAvgSteps) {
        this(size, process, maturity, tAvgSteps, 0.0001, NULL_REAL);
    }

    public FdmSimpleProcess1dMesher(int size,
                                    final StochasticProcess1D process,
                                    double maturity) {
        this(size, process, maturity, 10, 0.0001, NULL_REAL);
    }
}
