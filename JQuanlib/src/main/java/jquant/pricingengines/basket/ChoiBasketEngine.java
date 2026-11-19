package jquant.pricingengines.basket;

import jquant.AverageBasketPayoff;
import jquant.BasketOption;
import jquant.BasketPayoff;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.SpreadBasketPayoff;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.integrals.GaussHermiteIntegration;
import jquant.math.integrals.GaussianQuadrature;
import jquant.math.integrals.MultiDimGaussianIntegration;
import jquant.math.matrixutilities.HouseholderReflection;
import jquant.math.matrixutilities.HouseholderTransformation;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.SVD;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.BlackProcess;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Date;
import jquant.utilities.SharePtr;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.CommonUtil.Norm2;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.M_SQRT2;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Pricing engine for basket options on multiple underlyings.
 *
 * <p>Jaehyuk Choi, "Sum of all Black-Scholes-Merton Models: An efficient
 * Pricing Method for Spread, Basket and Asian Options".
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/basket/choibasketengine.{hpp,cpp}}.
 */
public class ChoiBasketEngine extends BasketOption.engine {
    private final int n_;
    private final List<GeneralizedBlackScholesProcess> processes_;
    private final Matrix rho_;
    private final double lambda_;
    private final int maxNrIntegrationSteps_;
    private final boolean calcFwdDelta_;
    private final boolean controlVariate_;

    public ChoiBasketEngine(
            List<GeneralizedBlackScholesProcess> processes,
            Matrix rho) {
        this(processes, rho, 10.0, Integer.MAX_VALUE, false, false);
    }

    public ChoiBasketEngine(
            List<GeneralizedBlackScholesProcess> processes,
            Matrix rho, double lambda, int maxNrIntegrationSteps,
            boolean calcfwdDelta, boolean controlVariate) {
        n_ = processes.size();
        processes_ = new ArrayList<>(processes);
        rho_ = rho;
        lambda_ = lambda;
        maxNrIntegrationSteps_ = maxNrIntegrationSteps;
        calcFwdDelta_ = calcfwdDelta || controlVariate;
        controlVariate_ = controlVariate;

        QL_REQUIRE(n_ > 0, "No Black-Scholes process is given.");
        QL_REQUIRE(n_ == rho_.rows() && rho_.rows() == rho_.cols(),
                "process and correlation matrix must have the same size.");
        QL_REQUIRE(lambda_ > 0.0, "lambda must be positive");

        for (GeneralizedBlackScholesProcess process : processes_) {
            registerWith(process);
        }
    }

    private static double matrixVectorRow(Matrix m, int row, Array v) {
        double s = 0.0;
        for (int j = 0; j < v.size(); ++j) {
            s += m.get(row, j) * v.get(j);
        }
        return s;
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise instanceof jquant.EuropeanExercise,
                "not an European exercise");
        Date maturityDate = arguments_.exercise.lastDate();

        VectorBsmProcessExtractor pExtractor =
                new VectorBsmProcessExtractor(processes_);
        Array s = pExtractor.getSpot();
        Array dq = pExtractor.getDividendYieldDf(maturityDate);
        double dr0 = pExtractor.getInterestRateDf(maturityDate);

        Array stdDev = new Array(s.size());
        Array variance = pExtractor.getBlackVariance(maturityDate);
        double minStdDev = QL_EPSILON * QL_EPSILON;
        for (int i = 0; i < s.size(); ++i) {
            stdDev.set(i, Math.max(minStdDev, Math.sqrt(variance.get(i))));
        }

        Array fwd = new Array(n_);
        for (int i = 0; i < n_; ++i) {
            fwd.set(i, s.get(i) * dq.get(i) / dr0);
        }

        AverageBasketPayoff avgPayoff;
        if (arguments_.payoff instanceof AverageBasketPayoff) {
            avgPayoff = (AverageBasketPayoff) arguments_.payoff;
        } else if (arguments_.payoff instanceof SpreadBasketPayoff) {
            avgPayoff = new AverageBasketPayoff(
                    ((SpreadBasketPayoff) arguments_.payoff).basePayoff(),
                    new Array(new double[]{1.0, -1.0}));
        } else {
            avgPayoff = null;
        }
        QL_REQUIRE(avgPayoff != null,
                "average or spread basket payoff expected");

        Array weights = avgPayoff.weights();
        QL_REQUIRE(n_ == weights.size() && n_ > 1,
                "wrong number of weights arguments in payoff");

        Array weightedFwd = new Array(n_);
        for (int i = 0; i < n_; ++i) {
            weightedFwd.set(i, weights.get(i) * fwd.get(i));
        }
        double weightedFwdNorm = Norm2(weightedFwd);
        Array g = new Array(n_);
        for (int i = 0; i < n_; ++i) {
            g.set(i, weightedFwd.get(i) / weightedFwdNorm);
        }

        Matrix sigma = MatrixUtil.getCovariance(
                stdDev.getList(), rho_, 1.0e-12);
        Array vStar1 = new Array(n_);
        for (int i = 0; i < n_; ++i) {
            vStar1.set(i, matrixVectorRow(sigma, i, g));
        }
        double vStar1Scale = Math.sqrt(DotProduct(g, vStar1));
        for (int i = 0; i < n_; ++i) {
            vStar1.set(i, vStar1.get(i) / vStar1Scale);
        }

        Matrix C = MatrixUtil.CholeskyDecomposition(sigma, false);

        double eps = 100.0 * Math.sqrt(QL_EPSILON);
        // publication sets tol=0, pyfeng implementation sets tol=0.01
        double tol = 100.0 * Math.sqrt(QL_EPSILON);

        boolean flip = false;
        for (int i = 0; i < n_; ++i) {
            if (Math.signum(g.get(i)) * vStar1.get(i) < tol * stdDev.get(i)) {
                flip = true;
                vStar1.set(i, eps * Math.signum(g.get(i)) * stdDev.get(i));
            }
        }

        Array q1 = new Array(n_);
        if (flip) {
            // q1 = inverse(C)*vStar1;
            for (int i = 0; i < n_; ++i) {
                double sum = 0.0;
                for (int j = 0; j < i; ++j) {
                    sum += C.get(i, j) * q1.get(j);
                }
                q1.set(i, (vStar1.get(i) - sum) / C.get(i, i));
            }
            double q1Norm = Norm2(q1);
            for (int i = 0; i < n_; ++i) {
                vStar1.set(i, vStar1.get(i) / q1Norm);
            }
        } else {
            // q1 = transpose(C)*g;
            for (int i = 0; i < n_; ++i) {
                double sum = 0.0;
                for (int j = 0; j < n_; ++j) {
                    sum += C.get(j, i) * g.get(j);
                }
                q1.set(i, sum);
            }
        }
        double q1Norm = Norm2(q1);
        for (int i = 0; i < n_; ++i) {
            q1.set(i, q1.get(i) / q1Norm);
        }

        Array e1 = new Array(n_, 0.0);
        e1.set(0, 1.0);

        Matrix R = new HouseholderTransformation(
                new HouseholderReflection(e1).reflectionVector(q1))
                .getMatrix();
        Matrix R2n = new Matrix(n_, n_ - 1, 0.0);
        for (int i = 0; i < n_; ++i) {
            for (int j = 1; j < n_; ++j) {
                R2n.set(i, j - 1, R.get(i, j));
            }
        }

        SVD svd = new SVD(C.multipy(R2n));
        Matrix U = svd.U();
        Array sv = svd.singularValues();

        Matrix v = new Matrix(n_, n_ - 1, 0.0);
        for (int j = 0; j < n_ - 1; ++j) {
            for (int i = 0; i < n_; ++i) {
                v.set(i, j, sv.get(j) * U.get(i, j));
            }
        }

        List<Integer> nIntOrder = new ArrayList<>(n_ - 1);
        for (int i = 0; i < n_ - 1; ++i) {
            nIntOrder.add(0);
        }
        double lambda = lambda_;
        double alpha = 1.0 / Math.abs(DotProduct(g, vStar1));
        boolean rescale;
        do {
            double intScale = lambda * alpha;
            for (int i = 0; i < n_ - 1; ++i) {
                nIntOrder.set(i, (int) Math.round(1.0 + intScale * sv.get(i)));
            }

            lambda *= 0.9;

            QL_REQUIRE(lambda / lambda_ > 1e-10,
                    "can not rescale lambda to fit max integration order");

            double orderProduct = 1.0;
            for (int order : nIntOrder) {
                orderProduct *= order;
            }
            rescale = orderProduct > maxNrIntegrationSteps_;
        } while (rescale);

        List<SimpleQuote> quotes = new ArrayList<>(n_);
        List<GeneralizedBlackScholesProcess> p = new ArrayList<>(n_);
        for (int i = 0; i < n_; ++i) {
            SimpleQuote quote = new SimpleQuote(fwd.get(i));
            quotes.add(quote);

            BlackVolTermStructure bv =
                    processes_.get(i).blackVolatility().getValue();
            double vol = vStar1.get(i) / Math.sqrt(
                    bv.dayCounter().yearFraction(bv.referenceDate(),
                            maturityDate));

            p.add(new BlackProcess(
                    new Handle<Quote>(quote, true),
                    processes_.get(i).riskFreeRate(),
                    new Handle<BlackVolTermStructure>(
                            new BlackConstantVol(bv.referenceDate(),
                                    bv.calendar(),
                                    new Handle<Quote>(
                                            new SimpleQuote(vol), true),
                                    bv.dayCounter()), true),
                    new EulerDiscretization(), false));
        }

        BasketOption option = new BasketOption(avgPayoff, arguments_.exercise);
        option.setPricingEngine(new SingleFactorBsmBasketEngine(p));

        Array vq = new Array(n_);
        for (int i = 0; i < n_; ++i) {
            double sum = 0.0;
            for (int j = 0; j < n_ - 1; ++j) {
                double x = v.get(i, j);
                sum += x * x;
            }
            vq.set(i, 0.5 * sum);
        }

        MultiDimGaussianIntegration ghq = new MultiDimGaussianIntegration(
                nIntOrder,
                new SharePtr<GaussianQuadrature, Integer>() {
                    @Override
                    public GaussianQuadrature value(Integer n) {
                        return new GaussHermiteIntegration(n);
                    }
                });
        final double normFactor = Math.pow(M_PI, -0.5 * nIntOrder.size());

        final List<Double> dStore =
                new ArrayList<>(ghq.weights().size());
        final Array fwdFinal = fwd;
        final Matrix vFinal = v;
        final Array vqFinal = vq;
        final List<SimpleQuote> quotesFinal = quotes;

        SharePtr<Double, Array> bsm1dPricer = new SharePtr<>() {
            @Override
            public Double value(Array z) {
                Array f = new Array(n_);
                for (int i = 0; i < n_; ++i) {
                    f.set(i, Math.exp(-M_SQRT2 * matrixVectorRow(vFinal, i, z)
                            - vqFinal.get(i)) * fwdFinal.get(i));
                }

                for (int i = 0; i < f.size(); ++i) {
                    quotesFinal.get(i).setValue(f.get(i));
                }

                dStore.add(((Number) option.additionalResults().get("d"))
                        .doubleValue());
                return Math.exp(-DotProduct(z, z)) * option.NPV();
            }
        };

        results_.value = ghq.value(bsm1dPricer) * normFactor;

        if (calcFwdDelta_) {
            QL_REQUIRE(avgPayoff.basePayoff() instanceof PlainVanillaPayoff,
                    "non-plain vanilla payoff given");
            PlainVanillaPayoff payoff =
                    (PlainVanillaPayoff) avgPayoff.basePayoff();
            double putIndicator =
                    payoff.optionType() == Option.Type.Call ? 0.0 : -1.0;

            CumulativeNormalDistribution N = new CumulativeNormalDistribution();

            Array fwdDelta = new Array(n_);
            Array fHat = new Array(n_);
            for (int k = 0; k < n_; ++k) {
                final int[] dStoreCounter = {0};
                final int kFinal = k;

                SharePtr<Double, Array> deltaPricer = new SharePtr<>() {
                    @Override
                    public Double value(Array z) {
                        double d = dStore.get(dStoreCounter[0]++);
                        double vz = matrixVectorRow(vFinal, kFinal, z);
                        double f = Math.exp(-M_SQRT2 * vz - vqFinal.get(kFinal));

                        return Math.exp(-DotProduct(z, z)) * f
                                * N.value(d + vStar1.get(kFinal));
                    }
                };

                fwdDelta.set(k, dr0 * weights.get(k)
                        * (ghq.value(deltaPricer) * normFactor + putIndicator));

                results_.additionalResults.put("forwardDelta " + k,
                        fwdDelta.get(k));
            }

            if (controlVariate_) {
                for (int k = 0; k < n_; ++k) {
                    final int kFinal = k;
                    SharePtr<Double, Array> fHatPricer = new SharePtr<>() {
                        @Override
                        public Double value(Array z) {
                            double vz = matrixVectorRow(vFinal, kFinal, z);
                            double f = Math.exp(-M_SQRT2 * vz
                                    - vqFinal.get(kFinal));

                            return Math.exp(-DotProduct(z, z)) * f;
                        }
                    };
                    fHat.set(k, ghq.value(fHatPricer) * normFactor);
                }
                double cv = 0.0;
                for (int k = 0; k < n_; ++k) {
                    cv += fwdDelta.get(k) * fwd.get(k) * (fHat.get(k) - 1.0);
                }
                results_.value -= cv;
            }
        }
    }
}
