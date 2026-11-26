package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.optimization.CostFunction;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.optimization.Problem;
import jquant.time.Period;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.squared;

/**
 * CMS market calibration.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/cmsmarketcalibration.hpp} and
 * {@code cmsmarketcalibration.cpp} at SHA {@code cf74c9f}. The three official
 * calibration types ({@link CalibrationType#OnSpread}, {@link CalibrationType#OnPrice},
 * {@link CalibrationType#OnForwardCmsPrice}), the six objective functions, the
 * beta/reversion transforms and the array/matrix/parametric compute overloads
 * are those of the official class.
 */
public class CmsMarketCalibration {

    public enum CalibrationType {
        OnSpread, OnPrice, OnForwardCmsPrice
    }

    // Parametric beta term structure: betaInf, beta0, and decay.
    private static final int nParametricParameters = 3;
    private static final int betaInfIndex = 0;
    private static final int beta0Index = 1;
    private static final int decayIndex = 2;

    public CmsMarketCalibration(Handle<SwaptionVolatilityStructure> volCube,
                                CmsMarket cmsMarket,
                                Matrix weights,
                                CalibrationType calibrationType) {
        volCube_ = volCube;
        cmsMarket_ = cmsMarket;
        weights_ = weights;
        calibrationType_ = calibrationType;

        QL_REQUIRE(weights.rows() == cmsMarket_.swapLengths().size(),
                "weights number of rows ("
                        + weights.rows()
                        + ") must be equal to number of swap lengths ("
                        + cmsMarket_.swapLengths().size() + ")");
        QL_REQUIRE(weights.cols() == cmsMarket_.swapTenors().size(),
                "weights number of columns ("
                        + weights.cols()
                        + ") must be equal to number of swap indexes ("
                        + cmsMarket_.swapTenors().size() + ")");
    }

    public Array compute(EndCriteria endCriteria,
                         OptimizationMethod method,
                         Array guess,
                         boolean isMeanReversionFixed) {
        int nSwapTenors = cmsMarket_.swapTenors().size();
        QL_REQUIRE(isMeanReversionFixed || guess.size() == nSwapTenors + 1,
                "if mean reversion is not fixed, a guess must be provided");
        QL_REQUIRE(nSwapTenors == guess.size()
                        || nSwapTenors == guess.size() - 1,
                "guess size (" + guess.size()
                        + ") must be equal to swap tenors size ("
                        + nSwapTenors
                        + ") or greater by one if mean reversion is "
                        + "given as last element");
        boolean isMeanReversionGiven = (nSwapTenors == guess.size() - 1);
        int nBeta = guess.size() - (isMeanReversionGiven ? 1 : 0);
        Array result;
        if (isMeanReversionFixed) {
            NoConstraint constraint = new NoConstraint();
            double fixedMeanReversion =
                    isMeanReversionGiven ? guess.get(nBeta) : NULL_REAL;
            Array betasGuess = new Array(nBeta);
            for (int i = 0; i < nBeta; ++i)
                betasGuess.set(i, guess.get(i));
            ObjectiveFunction2 costFunction = new ObjectiveFunction2(
                    this, fixedMeanReversion == NULL_REAL
                    ? NULL_REAL
                    : reversionTransformInverse(fixedMeanReversion));
            Problem problem = new Problem(costFunction, constraint, betasGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Array(nBeta + (isMeanReversionGiven ? 1 : 0));
            for (int i = 0; i < nBeta; ++i)
                result.set(i, betaTransformDirect(tmp.get(i)));
            if (isMeanReversionGiven)
                result.set(nBeta, fixedMeanReversion);
        } else {
            NoConstraint constraint = new NoConstraint();
            ObjectiveFunction costFunction = new ObjectiveFunction(this);
            Array betaReversionGuess = new Array(nBeta + 1);
            for (int i = 0; i < nBeta; ++i)
                betaReversionGuess.set(i, betaTransformInverse(guess.get(i)));
            betaReversionGuess.set(nBeta, reversionTransformInverse(guess.get(nBeta)));
            Problem problem = new Problem(costFunction, constraint, betaReversionGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            result = new Array(problem.currentValue());
            error_ = costFunction.value(result);
            for (int i = 0; i < nBeta; ++i)
                result.set(i, betaTransformDirect(result.get(i)));
            result.set(nBeta, reversionTransformDirect(result.get(nBeta)));
        }
        updateAfterRecalibration();
        return result;
    }

    public Matrix compute(EndCriteria endCriteria,
                          OptimizationMethod method,
                          Matrix guess,
                          boolean isMeanReversionFixed) {
        return compute(endCriteria, method, guess, isMeanReversionFixed, NULL_REAL);
    }

    public Matrix compute(EndCriteria endCriteria,
                          OptimizationMethod method,
                          Matrix guess,
                          boolean isMeanReversionFixed,
                          double meanReversionGuess) {
        int nSwapTenors = cmsMarket_.swapTenors().size();
        int nSwapLengths = cmsMarket_.swapLengths().size();
        QL_REQUIRE(isMeanReversionFixed || meanReversionGuess != NULL_REAL,
                "if mean reversion is not fixed, a guess must be provided");
        QL_REQUIRE(nSwapTenors == guess.cols(),
                "number of swap tenors ("
                        + nSwapTenors
                        + ") must be equal to number of guess columns ("
                        + guess.cols() + ")");
        QL_REQUIRE(nSwapLengths == guess.rows(),
                "number of swap lengths ("
                        + nSwapLengths
                        + ") must be equal to number of guess rows ("
                        + guess.rows() + ")");
        Matrix result;
        int nBeta = nSwapTenors * nSwapLengths;
        if (isMeanReversionFixed) {
            NoConstraint constraint = new NoConstraint();
            Array betasGuess = new Array(nBeta);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nSwapLengths; ++j) {
                    betasGuess.set(i * nSwapLengths + j,
                            betaTransformInverse(guess.get(j, i)));
                }
            }
            ObjectiveFunction4 costFunction = new ObjectiveFunction4(
                    this, meanReversionGuess == NULL_REAL
                    ? meanReversionGuess
                    : reversionTransformInverse(meanReversionGuess));
            Problem problem = new Problem(costFunction, constraint, betasGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(nSwapLengths,
                    nSwapTenors + (meanReversionGuess != NULL_REAL ? 1 : 0), 0.0);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nSwapLengths; ++j) {
                    result.set(j, i,
                            betaTransformDirect(tmp.get(i * nSwapLengths + j)));
                }
            }
            if (meanReversionGuess != NULL_REAL) {
                for (int j = 0; j < nSwapLengths; ++j) {
                    result.set(j, nSwapTenors, meanReversionGuess);
                }
            }
        } else {
            NoConstraint constraint = new NoConstraint();
            Array betasReversionGuess = new Array(nBeta + 1);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nSwapLengths; ++j) {
                    betasReversionGuess.set(i * nSwapLengths + j,
                            betaTransformInverse(guess.get(j, i)));
                }
            }
            betasReversionGuess.set(nBeta,
                    reversionTransformInverse(meanReversionGuess));
            ObjectiveFunction3 costFunction = new ObjectiveFunction3(this);
            Problem problem = new Problem(costFunction, constraint, betasReversionGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(nSwapLengths, nSwapTenors + 1, 0.0);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nSwapLengths; ++j) {
                    result.set(j, i,
                            betaTransformDirect(tmp.get(i * nSwapLengths + j)));
                }
            }
            for (int j = 0; j < nSwapLengths; ++j) {
                result.set(j, nSwapTenors, reversionTransformDirect(tmp.get(nBeta)));
            }
        }
        updateAfterRecalibration();
        return result;
    }

    public Matrix computeParametric(EndCriteria endCriteria,
                                    OptimizationMethod method,
                                    Matrix guess,
                                    boolean isMeanReversionFixed) {
        return computeParametric(endCriteria, method, guess,
                isMeanReversionFixed, NULL_REAL);
    }

    public Matrix computeParametric(EndCriteria endCriteria,
                                    OptimizationMethod method,
                                    Matrix guess,
                                    boolean isMeanReversionFixed,
                                    double meanReversionGuess) {
        int nSwapTenors = cmsMarket_.swapTenors().size();
        QL_REQUIRE(isMeanReversionFixed || meanReversionGuess != NULL_REAL,
                "if mean reversion is not fixed, a guess must be provided");
        QL_REQUIRE(nSwapTenors == guess.cols(),
                "number of swap tenors ("
                        + nSwapTenors
                        + ") must be equal to number of guess columns ("
                        + guess.cols() + ")");
        QL_REQUIRE(nParametricParameters == guess.rows(),
                "number of parameters ("
                        + nParametricParameters + ") must be equal to number of guess rows ("
                        + guess.rows() + ")");

        Matrix result;
        int nParams = nSwapTenors * nParametricParameters;
        if (isMeanReversionFixed) {
            NoConstraint constraint = new NoConstraint();
            Array betasGuess = new Array(nParams);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nParametricParameters; ++j) {
                    betasGuess.set(i * nParametricParameters + j,
                            (j == betaInfIndex || j == beta0Index)
                                    ? betaTransformInverse(guess.get(j, i))
                                    : Math.sqrt(guess.get(j, i)));
                }
            }
            ObjectiveFunction5 costFunction = new ObjectiveFunction5(
                    this, meanReversionGuess == NULL_REAL
                    ? meanReversionGuess
                    : reversionTransformInverse(meanReversionGuess));
            Problem problem = new Problem(costFunction, constraint, betasGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(nParametricParameters,
                    nSwapTenors + (meanReversionGuess != NULL_REAL ? 1 : 0), 0.0);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nParametricParameters; ++j) {
                    result.set(j, i,
                            (j == betaInfIndex || j == beta0Index)
                                    ? betaTransformDirect(
                                            tmp.get(i * nParametricParameters + j))
                                    : squared(tmp.get(i * nParametricParameters + j)));
                }
            }
            if (meanReversionGuess != NULL_REAL) {
                for (int j = 0; j < nParametricParameters; ++j) {
                    result.set(j, nSwapTenors, meanReversionGuess);
                }
            }
        } else {
            NoConstraint constraint = new NoConstraint();
            Array betasReversionGuess = new Array(nParams + 1);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nParametricParameters; ++j) {
                    betasReversionGuess.set(i * nParametricParameters + j,
                            (j == betaInfIndex || j == beta0Index)
                                    ? betaTransformInverse(guess.get(j, i))
                                    : Math.sqrt(guess.get(j, i)));
                }
            }
            betasReversionGuess.set(nParams,
                    reversionTransformInverse(meanReversionGuess));
            ObjectiveFunction6 costFunction = new ObjectiveFunction6(this);
            Problem problem = new Problem(costFunction, constraint, betasReversionGuess);
            endCriteria_ = method.minimize(problem, endCriteria);
            Array tmp = problem.currentValue();
            error_ = costFunction.value(tmp);
            result = new Matrix(nParametricParameters, nSwapTenors + 1, 0.0);
            for (int i = 0; i < nSwapTenors; ++i) {
                for (int j = 0; j < nParametricParameters; ++j) {
                    result.set(j, i,
                            (j == betaInfIndex || j == beta0Index)
                                    ? betaTransformDirect(
                                            tmp.get(i * nParametricParameters + j))
                                    : squared(tmp.get(i * nParametricParameters + j)));
                }
            }
            for (int j = 0; j < nParametricParameters; ++j) {
                result.set(j, nSwapTenors, reversionTransformDirect(tmp.get(nParams)));
            }
        }
        updateAfterRecalibration();
        return result;
    }

    public double error() {
        return error_;
    }

    public EndCriteria.Type endCriteria() {
        return endCriteria_;
    }

    public static double betaTransformInverse(double beta) {
        return Math.sqrt(-Math.log(beta));
    }

    public static double betaTransformDirect(double y) {
        return Math.max(
                Math.min(Math.abs(y) < 10.0 ? Math.exp(-(y * y)) : 0.0,
                        0.999999),
                0.000001);
    }

    public static double reversionTransformInverse(double reversion) {
        return reversion * reversion;
    }

    public static double reversionTransformDirect(double y) {
        return Math.sqrt(y);
    }

    private void updateAfterRecalibration() {
        SabrSwaptionVolatilityCube volCubeBySabr =
                (SabrSwaptionVolatilityCube) volCube_.currentLink();
        volCubeBySabr.updateAfterRecalibration();
        sparseSabrParameters_ = volCubeBySabr.sparseSabrParameters();
        denseSabrParameters_ = volCubeBySabr.denseSabrParameters();
        browseCmsMarket_ = cmsMarket_.browse();
    }

    private static SabrSwaptionVolatilityCube sabrCube(
            Handle<SwaptionVolatilityStructure> volCube) {
        return (SabrSwaptionVolatilityCube) volCube.currentLink();
    }

    public Handle<SwaptionVolatilityStructure> volCube_;
    public CmsMarket cmsMarket_;
    public Matrix weights_;
    public CalibrationType calibrationType_;
    public Matrix sparseSabrParameters_, denseSabrParameters_, browseCmsMarket_;

    private double error_;
    private EndCriteria.Type endCriteria_;

    //===========================================================================//
    //        ObjectiveFunction (constant beta, free mean reversion)             //
    //===========================================================================//

    private static class ObjectiveFunction extends CostFunction {
        ObjectiveFunction(CmsMarketCalibration smileAndCms) {
            smileAndCms_ = smileAndCms;
            volCube_ = smileAndCms.volCube_;
            cmsMarket_ = smileAndCms.cmsMarket_;
            weights_ = smileAndCms.weights_;
            calibrationType_ = smileAndCms.calibrationType_;
        }

        @Override
        public double value(Array x) {
            updateVolatilityCubeAndCmsMarket(x);
            return switchErrorFunctionOnCalibrationType();
        }

        @Override
        public Array values(Array x) {
            updateVolatilityCubeAndCmsMarket(x);
            return switchErrorsFunctionOnCalibrationType();
        }

        protected void updateVolatilityCubeAndCmsMarket(Array x) {
            List<Period> swapTenors = cmsMarket_.swapTenors();
            int nSwapTenors = swapTenors.size();
            QL_REQUIRE(nSwapTenors + 1 == x.size(),
                    "bad calibration guess nSwapTenors+1 != x.size()");
            SabrSwaptionVolatilityCube volCubeBySabr = sabrCube(volCube_);
            for (int i = 0; i < nSwapTenors; ++i)
                volCubeBySabr.recalibration(
                        CmsMarketCalibration.betaTransformDirect(x.get(i)),
                        swapTenors.get(i));
            double meanReversion =
                    CmsMarketCalibration.reversionTransformDirect(x.get(nSwapTenors));
            cmsMarket_.reprice(volCube_, meanReversion);
        }

        private double switchErrorFunctionOnCalibrationType() {
            switch (calibrationType_) {
                case OnSpread:
                    return cmsMarket_.weightedSpreadError(weights_);
                case OnPrice:
                    return cmsMarket_.weightedSpotNpvError(weights_);
                case OnForwardCmsPrice:
                    return cmsMarket_.weightedFwdNpvError(weights_);
                default:
                    QL_FAIL("unknown/illegal calibration type");
                    return Double.NaN;
            }
        }

        private Array switchErrorsFunctionOnCalibrationType() {
            switch (calibrationType_) {
                case OnSpread:
                    return cmsMarket_.weightedSpreadErrors(weights_);
                case OnPrice:
                    return cmsMarket_.weightedSpotNpvErrors(weights_);
                case OnForwardCmsPrice:
                    return cmsMarket_.weightedFwdNpvErrors(weights_);
                default:
                    QL_FAIL("unknown/illegal calibration type");
                    return null;
            }
        }

        protected final CmsMarketCalibration smileAndCms_;
        protected final Handle<SwaptionVolatilityStructure> volCube_;
        protected final CmsMarket cmsMarket_;
        protected final Matrix weights_;
        protected final CalibrationType calibrationType_;
    }

    //===========================================================================//
    //        ObjectiveFunction2 (constant beta, fixed mean reversion)           //
    //===========================================================================//

    private static class ObjectiveFunction2 extends ObjectiveFunction {
        ObjectiveFunction2(CmsMarketCalibration smileAndCms,
                           double fixedMeanReversion) {
            super(smileAndCms);
            fixedMeanReversion_ = fixedMeanReversion;
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(Array x) {
            List<Period> swapTenors = cmsMarket_.swapTenors();
            int nSwapTenors = swapTenors.size();
            QL_REQUIRE(nSwapTenors == x.size(),
                    "bad calibration guess nSwapTenors != x.size()");
            SabrSwaptionVolatilityCube volCubeBySabr = sabrCube(volCube_);
            for (int i = 0; i < nSwapTenors; ++i)
                volCubeBySabr.recalibration(
                        CmsMarketCalibration.betaTransformDirect(x.get(i)),
                        swapTenors.get(i));
            cmsMarket_.reprice(volCube_, fixedMeanReversion_ == NULL_REAL
                    ? NULL_REAL
                    : CmsMarketCalibration.reversionTransformDirect(
                            fixedMeanReversion_));
        }

        private final double fixedMeanReversion_;
    }

    //===========================================================================//
    //        ObjectiveFunction3 (beta termstructure, free mean reversion)       //
    //===========================================================================//

    private static class ObjectiveFunction3 extends ObjectiveFunction {
        ObjectiveFunction3(CmsMarketCalibration smileAndCms) {
            super(smileAndCms);
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(Array x) {
            List<Period> swapTenors = cmsMarket_.swapTenors();
            List<Period> swapLengths = cmsMarket_.swapLengths();
            int nSwapTenors = swapTenors.size();
            int nSwapLengths = swapLengths.size();
            QL_REQUIRE(
                    (nSwapLengths * nSwapTenors) + 1 == x.size(),
                    "bad calibration guess (nSwapLengths*nSwapTenors)+1 != x.size()");
            SabrSwaptionVolatilityCube volCubeBySabr = sabrCube(volCube_);
            for (int i = 0; i < nSwapTenors; ++i) {
                double[] beta = new double[nSwapLengths];
                for (int j = 0; j < nSwapLengths; ++j)
                    beta[j] = CmsMarketCalibration.betaTransformDirect(
                            x.get(i * nSwapLengths + j));
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            double meanReversion =
                    CmsMarketCalibration.reversionTransformDirect(
                            x.get(nSwapLengths * nSwapTenors));
            cmsMarket_.reprice(volCube_, meanReversion);
        }
    }

    //===========================================================================//
    //        ObjectiveFunction4 (beta termstructure, fixed mean reversion)      //
    //===========================================================================//

    private static class ObjectiveFunction4 extends ObjectiveFunction {
        ObjectiveFunction4(CmsMarketCalibration smileAndCms,
                           double fixedMeanReversion) {
            super(smileAndCms);
            fixedMeanReversion_ = fixedMeanReversion;
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(Array x) {
            List<Period> swapTenors = cmsMarket_.swapTenors();
            List<Period> swapLengths = cmsMarket_.swapLengths();
            int nSwapTenors = swapTenors.size();
            int nSwapLengths = swapLengths.size();
            QL_REQUIRE(
                    (nSwapLengths * nSwapTenors) == x.size(),
                    "bad calibration guess (nSwapLengths*nSwapTenors) != x.size()");
            SabrSwaptionVolatilityCube volCubeBySabr = sabrCube(volCube_);
            for (int i = 0; i < nSwapTenors; ++i) {
                double[] beta = new double[nSwapLengths];
                for (int j = 0; j < nSwapLengths; ++j)
                    beta[j] = CmsMarketCalibration.betaTransformDirect(
                            x.get(i * nSwapLengths + j));
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            cmsMarket_.reprice(volCube_, fixedMeanReversion_ == NULL_REAL
                    ? NULL_REAL
                    : CmsMarketCalibration.reversionTransformDirect(
                            fixedMeanReversion_));
        }

        private final double fixedMeanReversion_;
    }

    //=============================================================================//
    // ObjectiveFunction5 (beta parameteric termstructure, fixed mean reversion)   //
    //=============================================================================//

    private static class ObjectiveFunction5 extends ObjectiveFunction {
        ObjectiveFunction5(CmsMarketCalibration smileAndCms,
                           double fixedMeanReversion) {
            super(smileAndCms);
            fixedMeanReversion_ = fixedMeanReversion;
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(Array x) {
            List<Period> swapTenors = cmsMarket_.swapTenors();
            List<Period> swapLengths = cmsMarket_.swapLengths();
            int nSwapTenors = swapTenors.size();
            int nSwapLengths = swapLengths.size();
            QL_REQUIRE((nParametricParameters * nSwapTenors) == x.size(),
                    "bad calibration guess (nParametricParameters*nSwapTenors) != x.size()");
            SabrSwaptionVolatilityCube volCubeBySabr = sabrCube(volCube_);
            for (int i = 0; i < nSwapTenors; ++i) {
                double betaInf = CmsMarketCalibration.betaTransformDirect(
                        x.get(betaInfIndex + nParametricParameters * i));
                double beta0 = CmsMarketCalibration.betaTransformDirect(
                        x.get(beta0Index + nParametricParameters * i));
                double decay = squared(
                        x.get(decayIndex + nParametricParameters * i));
                double[] beta = new double[nSwapLengths];
                for (int j = 0; j < beta.length; ++j) {
                    double t = smileAndCms_.volCube_.currentLink()
                            .timeFromReference(
                                    smileAndCms_.volCube_.currentLink()
                                            .optionDateFromTenor(swapLengths.get(j)));
                    beta[j] = betaInf + (beta0 - betaInf) * Math.exp(-decay * t);
                }
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            cmsMarket_.reprice(volCube_, fixedMeanReversion_ == NULL_REAL
                    ? NULL_REAL
                    : CmsMarketCalibration.reversionTransformDirect(
                            fixedMeanReversion_));
        }

        private final double fixedMeanReversion_;
    }

    //===========================================================================//
    // ObjectiveFunction6 (beta parameteric termstructure, free mean reversion)  //
    //===========================================================================//

    private static class ObjectiveFunction6 extends ObjectiveFunction {
        ObjectiveFunction6(CmsMarketCalibration smileAndCms) {
            super(smileAndCms);
        }

        @Override
        protected void updateVolatilityCubeAndCmsMarket(Array x) {
            List<Period> swapTenors = cmsMarket_.swapTenors();
            List<Period> swapLengths = cmsMarket_.swapLengths();
            int nSwapTenors = swapTenors.size();
            int nSwapLengths = swapLengths.size();
            QL_REQUIRE((nParametricParameters * nSwapTenors + 1) == x.size(),
                    "bad calibration guess (nParametricParameters*nSwapTenors)+1 != x.size()");
            SabrSwaptionVolatilityCube volCubeBySabr = sabrCube(volCube_);
            for (int i = 0; i < nSwapTenors; ++i) {
                double betaInf = CmsMarketCalibration.betaTransformDirect(
                        x.get(betaInfIndex + nParametricParameters * i));
                double beta0 = CmsMarketCalibration.betaTransformDirect(
                        x.get(beta0Index + nParametricParameters * i));
                double decay = squared(
                        x.get(decayIndex + nParametricParameters * i));
                double[] beta = new double[nSwapLengths];
                for (int j = 0; j < beta.length; ++j) {
                    double t = smileAndCms_.volCube_.currentLink()
                            .timeFromReference(
                                    smileAndCms_.volCube_.currentLink()
                                            .optionDateFromTenor(swapLengths.get(j)));
                    beta[j] = betaInf + (beta0 - betaInf) * Math.exp(-decay * t);
                }
                volCubeBySabr.recalibration(swapLengths, beta, swapTenors.get(i));
            }
            double meanReversion =
                    CmsMarketCalibration.reversionTransformDirect(
                            x.get(nParametricParameters * nSwapTenors));
            cmsMarket_.reprice(volCube_, meanReversion);
        }
    }
}
