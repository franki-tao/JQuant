package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.ReferencePkg;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;

import java.util.Arrays;
import java.util.Comparator;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.CommonUtil.Norm2;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.squared;
import static jquant.math.matrixutilities.MatrixUtil.CholeskyDecomposition;

//! Covariance Matrix Adaptation Evolution Strategy (CMA-ES)
/*! Derivative-free global optimizer. Standard (mu/mu_w, lambda)-CMA-ES with
    positive recombination weights.

    Reference: N. Hansen, "The CMA Evolution Strategy: A Tutorial", 2016,
    arXiv:1604.00772.
*/
public class Cmaes extends OptimizationMethod {

    public static class Configuration {
        //! lambda; 0 => default 4 + floor(3 ln n)
        public int populationSize = 0;
        //! initial step size, in x-space units
        public double sigma = 0.3;
        public long seed = 0;
        public Array lowerBound = new Array(0);
        public Array upperBound = new Array(0);
        public Array initialMean = new Array(0);

        public Configuration() {
        }

        // value semantics, matching the by-value C++ constructor argument
        public Configuration(final Configuration other) {
            populationSize = other.populationSize;
            sigma = other.sigma;
            seed = other.seed;
            lowerBound = new Array(other.lowerBound);
            upperBound = new Array(other.upperBound);
            initialMean = new Array(other.initialMean);
        }

        public Configuration withPopulationSize(int n) {
            populationSize = n;
            return this;
        }

        public Configuration withSigma(double s) {
            QL_REQUIRE(s > 0.0, "sigma must be positive");
            sigma = s;
            return this;
        }

        public Configuration withSeed(long s) {
            seed = s;
            return this;
        }

        public Configuration withBounds(Array lo, Array up) {
            lowerBound = new Array(lo);
            upperBound = new Array(up);
            return this;
        }

        public Configuration withInitialMean(Array m) {
            initialMean = new Array(m);
            return this;
        }
    }

    private final Configuration configuration_;
    private final MersenneTwisterUniformRng rng_;

    public Cmaes() {
        this(new Configuration());
    }

    public Cmaes(Configuration configuration) {
        configuration_ = new Configuration(configuration);
        rng_ = new MersenneTwisterUniformRng(configuration_.seed);
    }

    public Configuration configuration() {
        return configuration_;
    }

    //! solves the lower-triangular system L t = b for t
    private static Array forwardSubstitution(final Matrix L, final Array b) {
        final int n = b.size();
        Array t = new Array(n);

        for (int i = 0; i < n; ++i) {
            double dot = 0.0;
            for (int j = 0; j < i; ++j)
                dot += L.get(i, j) * t.get(j);

            t.set(i, (b.get(i) - dot) / L.get(i, i));
        }

        return t;
    }

    private static Matrix copyMatrix(final Matrix source) {
        final int rows = source.rows();
        final int cols = source.cols();
        Matrix result = new Matrix(rows, cols, 0.0);
        for (int i = 0; i < rows; ++i)
            for (int j = 0; j < cols; ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }

    //! L * z
    private static Array multiply(final Matrix L, final Array z) {
        final int n = z.size();
        Array result = new Array(n);
        for (int i = 0; i < n; ++i) {
            double sum = 0.0;
            for (int j = 0; j < n; ++j)
                sum += L.get(i, j) * z.get(j);
            result.set(i, sum);
        }
        return result;
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        ReferencePkg<EndCriteria.Type> ecType =
                new ReferencePkg<>(EndCriteria.Type.None);
        P.reset();

        final int n = P.currentValue().size();
        QL_REQUIRE(n >= 1, "CMA-ES needs at least one dimension");

        final int lambda = configuration_.populationSize != 0
                ? configuration_.populationSize
                : (int) (4 + Math.floor(3.0 * Math.log((double) n)));
        QL_REQUIRE(lambda >= 2, "CMA-ES population size must be at least 2");

        final int mu = lambda / 2;

        Array weights = new Array(mu);
        for (int i = 0; i < mu; ++i)
            weights.set(i, Math.log((lambda + 1.0) / 2.0) - Math.log(i + 1.0));
        double weightSum = 0.0;
        for (int i = 0; i < mu; ++i)
            weightSum += weights.get(i);
        weights = weights.div(weightSum);

        final double muEff = 1.0 / DotProduct(weights, weights);
        final double cSigma = (muEff + 2.0) / (n + muEff + 5.0);
        final double dSigma = 1.0
                + 2.0 * Math.max(0.0, Math.sqrt((muEff - 1.0) / (n + 1.0)) - 1.0)
                + cSigma;
        final double cc = (4.0 + muEff / n) / (n + 4.0 + 2.0 * muEff / n);
        final double c1 = 2.0 / (squared(n + 1.3) + muEff);
        final double cMu = Math.min(1.0 - c1,
                2.0 * (muEff - 2.0 + 1.0 / muEff) / (squared(n + 2.0) + muEff));
        final double chiN = Math.sqrt((double) n)
                * (1.0 - 1.0 / (4.0 * n) + 1.0 / (21.0 * n * n));

        final double pSigmaGain = Math.sqrt(cSigma * (2.0 - cSigma) * muEff);
        final double pCovGain = Math.sqrt(cc * (2.0 - cc) * muEff);

        Array mean = new Array(configuration_.initialMean.empty()
                ? P.currentValue()
                : configuration_.initialMean);
        QL_REQUIRE(mean.size() == n,
                "initial mean size does not match problem dimension");

        double sigma = configuration_.sigma;

        // C = L L^T, initialised to the identity
        Matrix C = new Matrix(n, n, 0.0);
        for (int i = 0; i < n; ++i)
            C.set(i, i, 1.0);
        Matrix L = copyMatrix(C);

        Array pSigma = new Array(n, 0.0);
        Array pCov = new Array(n, 0.0);

        // Box bounds: configuration overrides the constraint
        final Array lo = configuration_.lowerBound.empty()
                ? P.constraint().lowerBound(P.currentValue())
                : new Array(configuration_.lowerBound);
        final Array up = configuration_.upperBound.empty()
                ? P.constraint().upperBound(P.currentValue())
                : new Array(configuration_.upperBound);
        QL_REQUIRE(lo.size() == n && up.size() == n,
                "bound size does not match problem dimension");

        Array bestX = new Array(mean);
        double bestF = QL_MAX_REAL;

        final InverseCumulativeNormal icn = new InverseCumulativeNormal();

        final int maxResample = 10;      // resample attempts before clamping
        final double gamma = 1.0;        // out-of-bounds penalty weight
        final double degenerateScale = 1e-12;
        final double pivotFloor = Math.sqrt(QL_EPSILON);

        Array[] xSamples = new Array[lambda];
        Array[] ySamples = new Array[lambda];
        Array fSamples = new Array(lambda);
        Integer[] order = new Integer[lambda];

        int g = 0;
        ReferencePkg<Integer> statState = new ReferencePkg<>(0);
        double fOld = QL_MAX_REAL;

        while (!endCriteria.checkMaxIterations(g, ecType)) {

            // Cholesky absorbs a semi-definite C by zeroing the pivot. Every
            // term of the update below is elementwise symmetric.
            L = CholeskyDecomposition(C, true);

            /*
                A pivot at or below the floor means that direction has
                collapsed. Flooring the diagonal alone is not sufficient as the
                subsequent column was already divided by the pre-floor pivot.
                This means L would factor nothing like C. Thus we zero it,
                leaving the direction contributing only its floored variance.
            */
            for (int i = 0; i < n; ++i) {
                if (!(L.get(i, i) > pivotFloor)) {
                    L.set(i, i, pivotFloor);

                    for (int j = i + 1; j < n; ++j)
                        L.set(j, i, 0.0);
                }
            }

            for (int k = 0; k < lambda; ++k) {
                boolean isFeasible = false;
                Array z = new Array(n);
                Array y = new Array(n);
                Array x = new Array(n);

                for (int attempt = 0; attempt < maxResample && !isFeasible; ++attempt) {
                    for (int i = 0; i < n; ++i)
                        z.set(i, icn.value(rng_.next().value));

                    y = multiply(L, z);
                    x = mean.add(y.mutiply(sigma));

                    isFeasible = P.constraint().test(x);
                    for (int i = 0; i < n && isFeasible; ++i)
                        isFeasible = x.get(i) >= lo.get(i) && x.get(i) <= up.get(i);
                }

                // unclamped y (not the clamped x) feeds the updates
                ySamples[k] = new Array(y);

                if (isFeasible) {
                    xSamples[k] = new Array(x);
                    fSamples.set(k, P.value(x));
                } else {
                    Array xClamped = new Array(n);
                    for (int i = 0; i < n; ++i)
                        xClamped.set(i,
                                Math.min(Math.max(x.get(i), lo.get(i)), up.get(i)));

                    final Array excess = x.subtract(xClamped);
                    final double penalty = gamma * DotProduct(excess, excess);

                    xSamples[k] = new Array(xClamped);
                    fSamples.set(k, P.value(xClamped) + penalty);
                }
            }

            for (int i = 0; i < lambda; ++i)
                order[i] = i;
            Arrays.sort(order, Comparator.comparingDouble(fSamples::get));

            Array yw = new Array(n, 0.0);
            for (int i = 0; i < mu; ++i) {
                final Array yi = ySamples[order[i]];
                final double wi = weights.get(i);
                for (int j = 0; j < n; ++j)
                    yw.set(j, yw.get(j) + wi * yi.get(j));
            }

            mean = mean.add(yw.mutiply(sigma));

            if (fSamples.get(order[0]) < bestF) {
                bestF = fSamples.get(order[0]);
                bestX = new Array(xSamples[order[0]]);
            }

            final Array cInvSqrtYw = forwardSubstitution(L, yw);

            pSigma = pSigma.mutiply(1.0 - cSigma)
                    .add(cInvSqrtYw.mutiply(pSigmaGain));

            final double pSigmaNorm = Norm2(pSigma);

            final double hSigmaLHS = pSigmaNorm
                    / Math.sqrt(1.0 - Math.pow(1.0 - cSigma, 2.0 * (g + 1)));
            final double hSigma = hSigmaLHS < (1.4 + 2.0 / (n + 1.0)) * chiN
                    ? 1.0
                    : 0.0;

            pCov = pCov.mutiply(1.0 - cc)
                    .add(yw.mutiply(hSigma * pCovGain));

            final double deltaH = (1.0 - hSigma) * cc * (2.0 - cc);

            Matrix newC = new Matrix(n, n, 0.0);
            for (int i = 0; i < n; ++i) {
                for (int j = i; j < n; ++j) {
                    double rankMu = 0.0;
                    for (int t = 0; t < mu; ++t) {
                        final Array yt = ySamples[order[t]];
                        rankMu += weights.get(t) * (yt.get(i) * yt.get(j));
                    }

                    final double cij = C.get(i, j);
                    final double newCij = (1.0 - c1 - cMu) * cij
                            + c1 * (pCov.get(i) * pCov.get(j) + deltaH * cij)
                            + cMu * rankMu;

                    newC.set(i, j, newCij);
                    if (i != j)
                        newC.set(j, i, newCij);
                }
            }
            C = newC;

            sigma = sigma
                    * Math.exp((cSigma / dSigma) * (pSigmaNorm / chiN - 1.0));

            // largest per-coordinate standard deviation of the search
            // distribution, standing in for the largest eigenvalue's root
            double maxVariance = 0.0;
            for (int i = 0; i < n; ++i)
                maxVariance = Math.max(maxVariance, C.get(i, i));

            if (sigma * Math.sqrt(maxVariance) < degenerateScale) {
                ecType.setT(EndCriteria.Type.StationaryFunctionValue);
                break;
            }

            if (endCriteria.checkStationaryFunctionValue(fOld, bestF, statState, ecType))
                break;

            fOld = bestF;
            ++g;
        }

        P.setCurrentValue(bestX);
        P.setFunctionValue(bestF);
        return ecType.getT();
    }

}
