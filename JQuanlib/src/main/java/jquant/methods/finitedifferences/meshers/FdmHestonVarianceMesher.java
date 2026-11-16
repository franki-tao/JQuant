package jquant.methods.finitedifferences.meshers;

import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.Point;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.distributions.InverseNonCentralCumulativeChiSquareDistribution;
import jquant.math.distributions.NonCentralCumulativeChiSquareDistribution;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.math.interpolations.LinearInterpolation;
import jquant.processes.HestonProcess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.squared;

/**
 * One-dimensional grid mesher for the variance part of the Heston model.
 *
 * <p>Port of {@code ql/methods/finitedifferences/meshers/fdmhestonvariancemesher.{hpp,cpp}}.
 */
public class FdmHestonVarianceMesher extends Fdm1dMesher {
    private double volaEstimate_;

    public FdmHestonVarianceMesher(int size,
                                   final HestonProcess process,
                                   double maturity) {
        this(size, process, maturity, 10, 0.0001, 1.0);
    }

    public FdmHestonVarianceMesher(int size,
                                   final HestonProcess process,
                                   double maturity, int tAvgSteps, double epsilon,
                                   double mixingFactor) {
        super(size);

        List<Double> vGrid = CommonUtil.ArrayInit(size, 0.0);
        List<Double> pGrid = CommonUtil.ArrayInit(size, 0.0);
        final double mixedSigma = process.sigma() * mixingFactor;
        final double df = 4 * process.theta() * process.kappa() / squared(mixedSigma);
        try {
            List<Point<Double, Double>> grid = new ArrayList<>();

            final double qMin = 0.0; // v_min = 0.0;
            double[] ncp = new double[tAvgSteps + 1];
            double[] k = new double[tAvgSteps + 1];
            double[] qMax = new double[tAvgSteps + 1];
            final double z = new InverseCumulativeNormal().value(1 - epsilon);
            boolean narrow = true;
            for (int l = 1; l <= tAvgSteps; ++l) {
                final double t = (maturity * l) / tAvgSteps;
                ncp[l] = 4 * process.kappa() * Math.exp(-process.kappa() * t)
                        / (squared(mixedSigma)
                        * (1 - Math.exp(-process.kappa() * t))) * process.v0();
                k[l] = squared(mixedSigma)
                        * (1 - Math.exp(-process.kappa() * t)) / (4 * process.kappa());

                // normal approximation of the quantiles, compared below
                // with the (size-1) minimum steps of the grid
                final double sd = Math.sqrt(2 * (df + 2 * ncp[l]));
                final double q = Math.max(process.v0(), k[l] * (df + ncp[l] + z * sd));
                if (2 * z * sd * k[l] >= (size - 1) * (q - qMin) / (50 * size))
                    narrow = false;
            }
            // with a small vol of vol, the distribution can be so narrow
            // that the minimum steps take most points beyond its quantiles
            // and the grid is degenerate; use the default mesh
            QL_REQUIRE(!narrow, "variance distribution too narrow");

            boolean containsV0 = false;
            for (int l = 1; l <= tAvgSteps; ++l) {
                final double q = k[l] * new InverseNonCentralCumulativeChiSquareDistribution(
                        df, ncp[l], 100, 1e-8).value(1 - epsilon);
                qMax[l] = Math.max(process.v0(), q);

                // the points built below for this l are at most
                // q + (size-1)*minVStep
                if (q + (size - 1) * (qMax[l] - qMin) / (50 * size) >= process.v0())
                    containsV0 = true;
            }
            // with a small vol of vol, the variance can drift away from
            // v0 and leave it outside the quantiles; use the default mesh
            QL_REQUIRE(containsV0, "variance grid doesn't contain v0");

            for (int l = 1; l <= tAvgSteps; ++l) {
                final double minVStep = (qMax[l] - qMin) / (50 * size);
                double ps, p = 0.0;

                double vTmp = qMin;
                grid.add(new Point<>(qMin, epsilon));

                for (int i = 1; i < size; ++i) {
                    double vx = vTmp + minVStep;
                    // beyond the 1-epsilon quantile, p can only decrease
                    // and its quantile is below vTmp; no need to compute it
                    if (p < 1 - epsilon) {
                        ps = (1 - epsilon - p) / (size - i);
                        p += ps;
                        final double tmp = k[l] * new InverseNonCentralCumulativeChiSquareDistribution(
                                df, ncp[l], 100, 1e-8).value(p);

                        vx = Math.max(vx, tmp);
                    }
                    p = new NonCentralCumulativeChiSquareDistribution(df, ncp[l]).value(vx / k[l]);
                    vTmp = vx;
                    grid.add(new Point<>(vx, p));
                }
            }
            QL_REQUIRE(grid.size() == size * tAvgSteps,
                    "something wrong with the grid size");

            Collections.sort(grid);

            for (int i = 0; i < size; ++i) {
                final int b = (i * grid.size()) / size;
                final int e = ((i + 1) * grid.size()) / size;
                for (int j = b; j < e; ++j) {
                    vGrid.set(i, vGrid.get(i) + grid.get(j).getFirst() / (e - b));
                    pGrid.set(i, pGrid.get(i) + grid.get(j).getSecond() / (e - b));
                }
            }

            // the check above can't rule out all such cases
            QL_REQUIRE(vGrid.get(size - 1) >= process.v0(),
                    "variance grid doesn't contain v0");

            // with a very narrow distribution, most points are beyond
            // the 1-epsilon quantile and have the same probability
            Collections.sort(pGrid);
            for (int i = 1; i < pGrid.size(); ++i) {
                QL_REQUIRE(!pGrid.get(i).equals(pGrid.get(i - 1)),
                        "degenerate variance grid");
            }
        } catch (IllegalArgumentException ex) {
            // use default mesh
            final double vol = mixedSigma
                    * Math.sqrt(process.theta() / (2 * process.kappa()));

            final double mean = process.theta();
            final double upperBound = Math.max(process.v0() + 4 * vol, mean + 4 * vol);
            final double lowerBound
                    = Math.max(0.0, Math.min(process.v0() - 4 * vol, mean - 4 * vol));

            for (int i = 0; i < size; ++i) {
                pGrid.set(i, i / (size - 1.0));
                vGrid.set(i, lowerBound + i * (upperBound - lowerBound) / (size - 1.0));
            }
        }

        final double skewHint = (process.kappa() != 0.0)
                ? Math.max(1.0, mixedSigma / process.kappa()) : 1.0;

        Collections.sort(pGrid);

        final double[] pGridArray = pGrid.stream().mapToDouble(Double::doubleValue).toArray();
        final double[] vGridArray = vGrid.stream().mapToDouble(Double::doubleValue).toArray();
        final LinearInterpolation variance = new LinearInterpolation(pGridArray, vGridArray);
        final Function interpolatedVolatility = x -> Math.sqrt(variance.value(x, true));

        volaEstimate_ = new GaussLobattoIntegral(100000, 1e-4).value(
                interpolatedVolatility, pGrid.get(0), pGrid.get(pGrid.size() - 1))
                * Math.pow(skewHint, 1.5);

        final double v0 = process.v0();
        for (int i = 1; i < vGrid.size(); ++i) {
            if (vGrid.get(i - 1) <= v0 && vGrid.get(i) >= v0) {
                if (Math.abs(vGrid.get(i - 1) - v0) < Math.abs(vGrid.get(i) - v0))
                    vGrid.set(i - 1, v0);
                else
                    vGrid.set(i, v0);
            }
        }

        for (int i = 0; i < size; ++i) {
            locations_.set(i, vGrid.get(i));
        }

        for (int i = 0; i < size - 1; ++i) {
            dminus_.set(i + 1, vGrid.get(i + 1) - vGrid.get(i));
            dplus_.set(i, vGrid.get(i + 1) - vGrid.get(i));
        }
        dplus_.set(size - 1, NULL_REAL);
        dminus_.set(0, NULL_REAL);
    }

    public double volaEstimate() {
        return volaEstimate_;
    }
}
