package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.Quote;
import jquant.indexes.SwapIndex;
import jquant.math.Function;
import jquant.math.Matrix;
import jquant.math.interpolations.BackwardflatLinearInterpolation;
import jquant.math.interpolations.BilinearInterpolation;
import jquant.math.interpolations.FlatExtrapolator2D;
import jquant.math.interpolations.Interpolation2D;
import jquant.math.interpolations.LinearInterpolation;
import jquant.math.interpolations.SABRInterpolation;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.solvers1d.Brent;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.SabrSmileSection;
import jquant.termstructures.volatility.Sarb;
import jquant.time.Date;
import jquant.time.Period;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.Normal;

/**
 * SABR swaption volatility cube (fit-early-interpolate-later).
 *
 * <p>Port of the official concrete instantiation
 * {@code XabrSwaptionVolatilityCube<SwaptionVolCubeSabrModel>} from
 * {@code ql/termstructures/volatility/swaption/sabrswaptionvolatilitycube.hpp},
 * using the classic Hagan SABR formula ({@link SabrSmileSection}) and
 * {@link SABRInterpolation}. Only the SABR model is ported; ZABR / no-arb
 * models are outside this slice.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/sabrswaptionvolatilitycube.hpp
 */
public class SabrSwaptionVolatilityCube extends SwaptionVolatilityCube {

    /** Official tolerance used when the smile fit is not vega-weighted. */
    private static final double SWAPTIONVOLCUBE_TOL = 100.0e-4;
    /** Official tolerance used when the smile fit is vega-weighted. */
    private static final double SWAPTIONVOLCUBE_VEGAWEIGHTED_TOL = 15.0e-4;

    private Cube marketVolCube_;
    private Cube volCubeAtmCalibrated_;
    private Cube sparseParameters_;
    private Cube denseParameters_;
    private List<List<SmileSection>> sparseSmiles_;

    private final List<List<Handle<Quote>>> parametersGuessQuotes_;
    private Cube parametersGuess_;
    private final boolean[] isParameterFixed_;
    private final boolean isAtmCalibrated_;
    private final boolean singlePassCalibration_;
    private final EndCriteria endCriteria_;
    private final double maxErrorTolerance_;
    private final OptimizationMethod optMethod_;
    private final double errorAccept_;
    private final boolean useMaxError_;
    private final int maxGuesses_;
    private final boolean backwardFlat_;
    private final double cutoffStrike_;
    private final Sarb.VolatilityType volatilityType_;

    private final PrivateObserver privateObserver_;

    /** Official defaults for the optional arguments. */
    public SabrSwaptionVolatilityCube(
            Handle<SwaptionVolatilityStructure> atmVolStructure,
            List<Period> optionTenors,
            List<Period> swapTenors,
            double[] strikeSpreads,
            List<List<Handle<Quote>>> volSpreads,
            SwapIndex swapIndexBase,
            SwapIndex shortSwapIndexBase,
            boolean vegaWeightedSmileFit,
            List<List<Handle<Quote>>> parametersGuess,
            boolean[] isParameterFixed,
            boolean isAtmCalibrated) {
        this(atmVolStructure, optionTenors, swapTenors, strikeSpreads,
                volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit, parametersGuess, isParameterFixed,
                isAtmCalibrated, null, NULL_REAL, null, NULL_REAL,
                false, 50, false, 0.0001, false);
    }

    public SabrSwaptionVolatilityCube(
            Handle<SwaptionVolatilityStructure> atmVolStructure,
            List<Period> optionTenors,
            List<Period> swapTenors,
            double[] strikeSpreads,
            List<List<Handle<Quote>>> volSpreads,
            SwapIndex swapIndexBase,
            SwapIndex shortSwapIndexBase,
            boolean vegaWeightedSmileFit,
            List<List<Handle<Quote>>> parametersGuess,
            boolean[] isParameterFixed,
            boolean isAtmCalibrated,
            EndCriteria endCriteria,
            double maxErrorTolerance,
            OptimizationMethod optMethod,
            double errorAccept,
            boolean useMaxError,
            int maxGuesses,
            boolean backwardFlat,
            double cutoffStrike,
            boolean singlePassCalibration) {
        super(atmVolStructure, optionTenors, swapTenors, strikeSpreads,
                volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit);

        parametersGuessQuotes_ = copy(parametersGuess);
        isParameterFixed_ = isParameterFixed.clone();
        isAtmCalibrated_ = isAtmCalibrated;
        singlePassCalibration_ = singlePassCalibration;
        endCriteria_ = endCriteria;
        optMethod_ = optMethod;
        useMaxError_ = useMaxError;
        maxGuesses_ = maxGuesses;
        backwardFlat_ = backwardFlat;
        cutoffStrike_ = cutoffStrike;
        volatilityType_ = atmVolStructure.currentLink().volatilityType();

        // Assign each final field once: official Null defaults, then
        // vega-weighted official tolerance when the smile fit is weighted.
        double resolvedMaxErrorTolerance;
        if (maxErrorTolerance != NULL_REAL) {
            resolvedMaxErrorTolerance = maxErrorTolerance;
        } else if (vegaWeightedSmileFit_) {
            resolvedMaxErrorTolerance = SWAPTIONVOLCUBE_VEGAWEIGHTED_TOL;
        } else {
            resolvedMaxErrorTolerance = SWAPTIONVOLCUBE_TOL;
        }
        maxErrorTolerance_ = resolvedMaxErrorTolerance;
        errorAccept_ = (errorAccept != NULL_REAL)
                ? errorAccept
                : maxErrorTolerance_ / 5.0;

        privateObserver_ = new PrivateObserver();
        registerWithParametersGuess();
        setParameterGuess();
    }

    // ------------------------------------------------------------------
    // LazyObject interface
    // ------------------------------------------------------------------

    @Override
    protected void performCalculations() {
        super.performCalculations();

        //! set marketVolCube_ by volSpreads_ quotes
        marketVolCube_ = newCube(optionDates(), swapTenors(), optionTimes(),
                swapLengths(), nStrikes_);
        for (int j = 0; j < nOptionTenors_; ++j) {
            for (int k = 0; k < nSwapTenors_; ++k) {
                double atmForward = atmStrike(optionDates_.get(j),
                        swapTenors_.get(k));
                double atmVol = atmVol_.currentLink().volatility(
                        optionDates_.get(j), swapTenors_.get(k), atmForward);
                for (int i = 0; i < nStrikes_; ++i) {
                    double vol = atmVol + volSpreads_
                            .get(j * nSwapTenors_ + k).get(i)
                            .currentLink().value();
                    marketVolCube_.setElement(i, j, k, vol);
                }
            }
        }
        marketVolCube_.updateInterpolators();

        volCubeAtmCalibrated_ = marketVolCube_.copy();

        if (singlePassCalibration_) {
            fillVolatilityCube(true);
            denseParameters_ = sabrCalibration(volCubeAtmCalibrated_);
            denseParameters_.updateInterpolators();
            sparseParameters_ = denseParameters_.copy();
        } else {
            sparseParameters_ = sabrCalibration(marketVolCube_);
            sparseParameters_.updateInterpolators();
            if (isAtmCalibrated_) {
                fillVolatilityCube();
                denseParameters_ = sabrCalibration(volCubeAtmCalibrated_);
                denseParameters_.updateInterpolators();
            }
        }
    }

    public void updateAfterRecalibration() {
        volCubeAtmCalibrated_ = marketVolCube_.copy();
        if (singlePassCalibration_) {
            fillVolatilityCube(true);
            denseParameters_ = sabrCalibration(volCubeAtmCalibrated_);
            denseParameters_.updateInterpolators();
            sparseParameters_ = denseParameters_.copy();
        } else if (isAtmCalibrated_) {
            fillVolatilityCube();
            denseParameters_ = sabrCalibration(volCubeAtmCalibrated_);
            denseParameters_.updateInterpolators();
        }
        notifyObservers();
    }

    @Override
    protected int requiredNumberOfStrikes() {
        return 1;
    }

    // ------------------------------------------------------------------
    // Smiles
    // ------------------------------------------------------------------

    @Override
    protected SmileSection smileSectionImpl(double optionTime,
                                            double swapLength) {
        // Official smileSection(const Cube&) takes a reference to the
        // member and calls calculate() inside; after that the same
        // object is populated. Java passes the reference by value, so
        // calculate first, then pass the now-initialized cube.
        calculate();
        if (isAtmCalibrated_ || singlePassCalibration_)
            return smileSection(optionTime, swapLength, denseParameters_);
        else
            return smileSection(optionTime, swapLength, sparseParameters_);
    }

    protected SmileSection smileSection(double optionTime,
                                        double swapLength,
                                        Cube sabrParametersCube) {
        calculate();
        double[] allParameters = sabrParametersCube.evaluate(optionTime,
                swapLength);
        double forward = allParameters[4];
        double[] modelParams = Arrays.copyOf(allParameters, 4);
        double shift = atmVol_.currentLink().shift(optionTime, swapLength);
        return new SabrSmileSection(optionTime, forward, modelParams, shift,
                volatilityType_);
    }

    // ------------------------------------------------------------------
    // Inspectors
    // ------------------------------------------------------------------

    /** Market volatility cube for one strike. */
    public Matrix marketVolCube(int i) {
        calculate();
        return marketVolCube_.layerMatrix(i);
    }

    public Matrix sparseSabrParameters() {
        calculate();
        return sparseParameters_.browse();
    }

    public Matrix denseSabrParameters() {
        calculate();
        return denseParameters_.browse();
    }

    public Matrix marketVolCube() {
        calculate();
        return marketVolCube_.browse();
    }

    public Matrix volCubeAtmCalibrated() {
        calculate();
        return volCubeAtmCalibrated_.browse();
    }

    // ------------------------------------------------------------------
    // Calibration
    // ------------------------------------------------------------------

    protected Cube sabrCalibration(Cube marketVolCube) {
        double[] optionTimes = marketVolCube.optionTimes();
        double[] swapLengths = marketVolCube.swapLengths();
        Date[] optionDates = marketVolCube.optionDates();
        Period[] swapTenors = marketVolCube.swapTenors();

        double[][] alphas = new double[optionTimes.length][swapLengths.length];
        double[][] betas = new double[optionTimes.length][swapLengths.length];
        double[][] nus = new double[optionTimes.length][swapLengths.length];
        double[][] rhos = new double[optionTimes.length][swapLengths.length];
        double[][] forwards = new double[optionTimes.length][swapLengths.length];
        double[][] errors = new double[optionTimes.length][swapLengths.length];
        double[][] maxErrors = new double[optionTimes.length][swapLengths.length];
        double[][] endCriteria = new double[optionTimes.length][swapLengths.length];

        double[] strikes = new double[nStrikes_];
        double[] volatilities = new double[nStrikes_];

        for (int j = 0; j < optionTimes.length; ++j) {
            for (int k = 0; k < swapLengths.length; ++k) {
                double atmForward = atmStrike(optionDates[j], swapTenors[k]);
                double shiftTmp = atmVol_.currentLink().shift(optionTimes[j],
                        swapLengths[k]);
                int n = 0;
                for (int i = 0; i < nStrikes_; ++i) {
                    double strike = atmForward + strikeSpreads_[i];
                    if (strike + shiftTmp >= cutoffStrike_) {
                        strikes[n] = strike;
                        volatilities[n] = marketVolCube.point(i, j, k);
                        ++n;
                    }
                }
                double[] usedStrikes = Arrays.copyOf(strikes, n);
                double[] usedVols = Arrays.copyOf(volatilities, n);

                double[] guess = parametersGuess_.evaluate(optionTimes[j],
                        swapLengths[k]);

                SABRInterpolation sabrInterpolation = new SABRInterpolation(
                        usedStrikes, usedVols, optionTimes[j], atmForward,
                        guess[0], guess[1], guess[2], guess[3],
                        isParameterFixed_[0], isParameterFixed_[1],
                        isParameterFixed_[2], isParameterFixed_[3],
                        vegaWeightedSmileFit_, endCriteria_, optMethod_,
                        errorAccept_, useMaxError_, maxGuesses_, shiftTmp,
                        volatilityType_);
                sabrInterpolation.update();

                double rmsError = sabrInterpolation.rmsError();
                double maxError = sabrInterpolation.maxError();
                double calibrationRmsError = rmsError;
                double calibrationMaxError = maxError;
                alphas[j][k] = sabrInterpolation.alpha();
                betas[j][k] = sabrInterpolation.beta();
                nus[j][k] = sabrInterpolation.nu();
                rhos[j][k] = sabrInterpolation.rho();
                if (singlePassCalibration_ && isAtmCalibrated_) {
                    double[] parameters = {alphas[j][k], betas[j][k],
                            nus[j][k], rhos[j][k]};
                    double atmVol = atmVol_.currentLink().volatility(
                            optionDates[j], swapTenors[k], atmForward, true);
                    alphas[j][k] = calibratedAtmAlpha(optionTimes[j],
                            atmForward, atmVol, parameters, shiftTmp);
                    parameters[0] = alphas[j][k];
                    double[] e = smileErrors(optionTimes[j], atmForward,
                            parameters, shiftTmp, usedStrikes, usedVols,
                            toArray(sabrInterpolation.interpolationWeights()));
                    rmsError = e[0];
                    maxError = e[1];
                }
                forwards[j][k] = atmForward;
                errors[j][k] = rmsError;
                maxErrors[j][k] = maxError;
                endCriteria[j][k] = toDouble(sabrInterpolation.endCriteria());

                QL_REQUIRE(sabrInterpolation.endCriteria()
                                != EndCriteria.Type.MaxIterations,
                        "global swaptions calibration failed: "
                                + "MaxIterations reached: "
                                + "\noption maturity = " + optionDates[j]
                                + ", \nswap tenor = " + swapTenors[k]
                                + ", \nrms error = " + calibrationRmsError
                                + ", \nmax error = " + calibrationMaxError
                                + ", \n   alpha = " + alphas[j][k]
                                + "\n   beta = " + betas[j][k]
                                + "\n   nu = " + nus[j][k]
                                + "\n   rho = " + rhos[j][k] + "\n");

                QL_REQUIRE((useMaxError_ ? calibrationMaxError
                                : calibrationRmsError) < maxErrorTolerance_,
                        "global swaptions calibration failed: "
                                + "error tolerance exceeded: "
                                + "\nusing " + (useMaxError_ ? "maxError"
                                : "rmsError")
                                + " tolerance " + maxErrorTolerance_
                                + ", \noption maturity = " + optionDates[j]
                                + ", \nswap tenor = " + swapTenors[k]
                                + ", \nrms error = " + calibrationRmsError
                                + ", \nmax error = " + calibrationMaxError
                                + ", \n   alpha = " + alphas[j][k]
                                + "\n   beta = " + betas[j][k]
                                + "\n   nu = " + nus[j][k]
                                + "\n   rho = " + rhos[j][k] + "\n");
            }
        }

        Cube sabrParametersCube = newCube(optionDates, swapTenors,
                optionTimes, swapLengths, 8);
        sabrParametersCube.setLayer(0, alphas);
        sabrParametersCube.setLayer(1, betas);
        sabrParametersCube.setLayer(2, nus);
        sabrParametersCube.setLayer(3, rhos);
        sabrParametersCube.setLayer(4, forwards);
        sabrParametersCube.setLayer(5, errors);
        sabrParametersCube.setLayer(6, maxErrors);
        sabrParametersCube.setLayer(7, endCriteria);
        return sabrParametersCube;
    }

    public void sabrCalibrationSection(Cube marketVolCube,
                                       Cube parametersCube,
                                       Period swapTenor) {
        double[] optionTimes = marketVolCube.optionTimes();
        double[] swapLengths = marketVolCube.swapLengths();
        Date[] optionDates = marketVolCube.optionDates();
        Period[] swapTenors = marketVolCube.swapTenors();

        int k = indexOf(swapTenors, swapTenor);
        QL_REQUIRE(k != swapTenors.length, "swap tenor not found");

        double[] calibrationResult = new double[8];
        double[] strikes = new double[nStrikes_];
        double[] volatilities = new double[nStrikes_];

        for (int j = 0; j < optionTimes.length; ++j) {
            double atmForward = atmStrike(optionDates[j], swapTenors[k]);
            double shiftTmp = atmVol_.currentLink().shift(optionTimes[j],
                    swapLengths[k]);
            int n = 0;
            for (int i = 0; i < nStrikes_; ++i) {
                double strike = atmForward + strikeSpreads_[i];
                if (strike + shiftTmp >= cutoffStrike_) {
                    strikes[n] = strike;
                    volatilities[n] = marketVolCube.point(i, j, k);
                    ++n;
                }
            }
            double[] usedStrikes = Arrays.copyOf(strikes, n);
            double[] usedVols = Arrays.copyOf(volatilities, n);

            double[] guess = parametersGuess_.evaluate(optionTimes[j],
                    swapLengths[k]);

            SABRInterpolation sabrInterpolation = new SABRInterpolation(
                    usedStrikes, usedVols, optionTimes[j], atmForward,
                    guess[0], guess[1], guess[2], guess[3],
                    isParameterFixed_[0], isParameterFixed_[1],
                    isParameterFixed_[2], isParameterFixed_[3],
                    vegaWeightedSmileFit_, endCriteria_, optMethod_,
                    errorAccept_, useMaxError_, maxGuesses_, shiftTmp,
                    volatilityType_);
            sabrInterpolation.update();

            double interpolationError = sabrInterpolation.rmsError();
            double maxError = sabrInterpolation.maxError();
            double calibrationError = interpolationError;
            double calibrationMaxError = maxError;
            calibrationResult[0] = sabrInterpolation.alpha();
            calibrationResult[1] = sabrInterpolation.beta();
            calibrationResult[2] = sabrInterpolation.nu();
            calibrationResult[3] = sabrInterpolation.rho();
            if (singlePassCalibration_ && isAtmCalibrated_) {
                double[] parameters = Arrays.copyOf(calibrationResult, 4);
                double atmVol = atmVol_.currentLink().volatility(
                        optionDates[j], swapTenors[k], atmForward, true);
                calibrationResult[0] = calibratedAtmAlpha(optionTimes[j],
                        atmForward, atmVol, parameters, shiftTmp);
                parameters[0] = calibrationResult[0];
                double[] e = smileErrors(optionTimes[j], atmForward,
                        parameters, shiftTmp, usedStrikes, usedVols,
                        toArray(sabrInterpolation.interpolationWeights()));
                interpolationError = e[0];
                maxError = e[1];
            }
            calibrationResult[4] = atmForward;
            calibrationResult[5] = interpolationError;
            calibrationResult[6] = maxError;
            calibrationResult[7] = toDouble(sabrInterpolation.endCriteria());

            QL_REQUIRE(sabrInterpolation.endCriteria()
                            != EndCriteria.Type.MaxIterations,
                    "section calibration failed: option tenor "
                            + optionDates[j] + ", swap tenor "
                            + swapTenors[k] + ": max iteration ("
                            + (endCriteria_ == null ? "default"
                            : endCriteria_.maxIterations()) + ")"
                            + ", alpha " + calibrationResult[0]
                            + ", beta " + calibrationResult[1]
                            + ", nu " + calibrationResult[2]
                            + ", rho " + calibrationResult[3]
                            + ", max error " + calibrationMaxError
                            + ", error " + calibrationError);

            QL_REQUIRE((useMaxError_ ? calibrationMaxError
                            : calibrationError) < maxErrorTolerance_,
                    "section calibration failed: option tenor "
                            + optionDates[j] + ", swap tenor "
                            + swapTenors[k]
                            + (useMaxError_ ? ": max error " : ": error ")
                            + (useMaxError_ ? calibrationMaxError
                            : calibrationError)
                            + ", alpha " + calibrationResult[0]
                            + ", beta " + calibrationResult[1]
                            + ", nu " + calibrationResult[2]
                            + ", rho " + calibrationResult[3]
                            + (useMaxError_ ? ", error " : ", max error ")
                            + (useMaxError_ ? calibrationError
                            : calibrationMaxError));

            parametersCube.setPoint(optionDates[j], swapTenors[k],
                    optionTimes[j], swapLengths[k], calibrationResult);
            parametersCube.updateInterpolators();
        }
    }

    protected double calibratedAtmAlpha(double optionTime,
                                        double forward,
                                        double atmVol,
                                        double[] parameters,
                                        double shift) {
        double[] params = parameters.clone();
        Function atmError = alpha -> {
            params[0] = alpha;
            return new SabrSmileSection(optionTime, forward, params, shift,
                    volatilityType_).volatility(forward) - atmVol;
        };
        Brent solver = new Brent();
        solver.setLowerBound(QL_EPSILON);
        double guess = params[0];
        double step = Math.max(0.1 * guess, 1.0e-6);
        return solver.solve(atmError, 1.0e-12, guess, step);
    }

    protected double[] smileErrors(double optionTime,
                                   double forward,
                                   double[] parameters,
                                   double shift,
                                   double[] strikes,
                                   double[] volatilities,
                                   double[] weights) {
        SmileSection smile = new SabrSmileSection(optionTime, forward,
                parameters, shift, volatilityType_);
        double squaredError = 0.0;
        double maxError = QL_MIN_REAL;
        for (int i = 0; i < strikes.length; ++i) {
            double error = smile.volatility(strikes[i]) - volatilities[i];
            squaredError += error * error * weights[i];
            maxError = Math.max(maxError, Math.abs(error));
        }
        int n = strikes.length;
        return new double[]{Math.sqrt(n * squaredError / (n == 1 ? 1 : n - 1)),
                maxError};
    }

    // ------------------------------------------------------------------
    // Dense cube
    // ------------------------------------------------------------------

    protected void fillVolatilityCube() {
        fillVolatilityCube(false);
    }

    protected void fillVolatilityCube(boolean marketSpreads) {
        SwaptionVolatilityStructure atm = atmVol_.currentLink();
        QL_REQUIRE(atm instanceof SwaptionVolatilityDiscrete,
                "isAtmCalibrated requires an ATM volatility structure "
                        + "derived from SwaptionVolatilityDiscrete (e.g. "
                        + "SwaptionVolatilityMatrix), but the provided "
                        + "atmVolStructure is not");
        SwaptionVolatilityDiscrete atmVolStructure =
                (SwaptionVolatilityDiscrete) atm;

        double[] atmOptionTimes = sortedUnique(concat(
                atmVolStructure.optionTimes(),
                volCubeAtmCalibrated_.optionTimes()));
        double[] atmSwapLengths = sortedUnique(concat(
                atmVolStructure.swapLengths(),
                volCubeAtmCalibrated_.swapLengths()));
        Date[] atmOptionDates = sortedUniqueDates(
                concatDates(atmVolStructure.optionDates(),
                        Arrays.asList(volCubeAtmCalibrated_.optionDates())));
        Period[] atmSwapTenors = sortedUniquePeriods(
                concatPeriods(atmVolStructure.swapTenors(),
                        Arrays.asList(volCubeAtmCalibrated_.swapTenors())));

        if (!marketSpreads)
            createSparseSmiles();

        for (int j = 0; j < atmOptionTimes.length; ++j) {
            for (int k = 0; k < atmSwapLengths.length; ++k) {
                boolean expandOptionTimes = !contains(
                        volCubeAtmCalibrated_.optionTimes(),
                        atmOptionTimes[j]);
                boolean expandSwapLengths = !contains(
                        volCubeAtmCalibrated_.swapLengths(),
                        atmSwapLengths[k]);
                if (expandOptionTimes || expandSwapLengths) {
                    double atmForward = atmStrike(atmOptionDates[j],
                            atmSwapTenors[k]);
                    double atmVol = atmVol_.currentLink().volatility(
                            atmOptionDates[j], atmSwapTenors[k], atmForward);
                    double[] spreadVols = spreadVolInterpolation(
                            atmOptionDates[j], atmSwapTenors[k],
                            marketSpreads);
                    double[] volAtmCalibrated = new double[nStrikes_];
                    for (int i = 0; i < nStrikes_; ++i)
                        volAtmCalibrated[i] = atmVol + spreadVols[i];
                    volCubeAtmCalibrated_.setPoint(atmOptionDates[j],
                            atmSwapTenors[k], atmOptionTimes[j],
                            atmSwapLengths[k], volAtmCalibrated);
                }
            }
        }
        volCubeAtmCalibrated_.updateInterpolators();
    }

    protected void createSparseSmiles() {
        double[] optionTimes = sparseParameters_.optionTimes();
        double[] swapLengths = sparseParameters_.swapLengths();
        sparseSmiles_ = new ArrayList<>();

        for (double optionTime : optionTimes) {
            List<SmileSection> tmp = new ArrayList<>(swapLengths.length);
            for (double swapLength : swapLengths)
                tmp.add(smileSection(optionTime, swapLength,
                        sparseParameters_));
            sparseSmiles_.add(tmp);
        }
    }

    protected double[] spreadVolInterpolation(Date atmOptionDate,
                                              Period atmSwapTenor,
                                              boolean marketSpreads) {
        double atmOptionTime = timeFromReference(atmOptionDate);
        double atmTimeLength = swapLength(atmSwapTenor);

        Cube sourceCube = marketSpreads ? marketVolCube_ : sparseParameters_;
        double[] optionTimes = sourceCube.optionTimes();
        double[] swapLengths = sourceCube.swapLengths();
        Date[] optionDates = sourceCube.optionDates();
        Period[] swapTenors = sourceCube.swapTenors();

        int optionTimesPreviousIndex = lowerBound(optionTimes, atmOptionTime);
        if (optionTimesPreviousIndex > 0)
            --optionTimesPreviousIndex;

        int swapLengthsPreviousIndex = lowerBound(swapLengths, atmTimeLength);
        if (swapLengthsPreviousIndex > 0)
            --swapLengthsPreviousIndex;

        List<List<SmileSection>> smiles = new ArrayList<>();
        if (marketSpreads) {
            QL_REQUIRE(optionTimesPreviousIndex + 1 < optionTimes.length,
                    "cannot interpolate market spreads at option time "
                            + atmOptionTime);
            QL_REQUIRE(swapLengthsPreviousIndex + 1 < swapLengths.length,
                    "cannot interpolate market spreads at swap length "
                            + atmTimeLength);
        } else {
            QL_REQUIRE(optionTimesPreviousIndex + 1 < sparseSmiles_.size(),
                    "optionTimesPreviousIndex+1 >= sparseSmiles_.size()");
            QL_REQUIRE(swapLengthsPreviousIndex + 1
                            < sparseSmiles_.get(0).size(),
                    "swapLengthsPreviousIndex+1 >= sparseSmiles_[0].size()");
            List<SmileSection> smilesOnPreviousExpiry = new ArrayList<>(2);
            List<SmileSection> smilesOnNextExpiry = new ArrayList<>(2);
            smilesOnPreviousExpiry.add(sparseSmiles_
                    .get(optionTimesPreviousIndex)
                    .get(swapLengthsPreviousIndex));
            smilesOnPreviousExpiry.add(sparseSmiles_
                    .get(optionTimesPreviousIndex)
                    .get(swapLengthsPreviousIndex + 1));
            smilesOnNextExpiry.add(sparseSmiles_
                    .get(optionTimesPreviousIndex + 1)
                    .get(swapLengthsPreviousIndex));
            smilesOnNextExpiry.add(sparseSmiles_
                    .get(optionTimesPreviousIndex + 1)
                    .get(swapLengthsPreviousIndex + 1));
            smiles.add(smilesOnPreviousExpiry);
            smiles.add(smilesOnNextExpiry);
        }

        double[] optionsNodes = {optionTimes[optionTimesPreviousIndex],
                optionTimes[optionTimesPreviousIndex + 1]};
        Date[] optionsDateNodes = {optionDates[optionTimesPreviousIndex],
                optionDates[optionTimesPreviousIndex + 1]};
        double[] swapLengthsNodes = {swapLengths[swapLengthsPreviousIndex],
                swapLengths[swapLengthsPreviousIndex + 1]};
        Period[] swapTenorNodes = {swapTenors[swapLengthsPreviousIndex],
                swapTenors[swapLengthsPreviousIndex + 1]};

        double atmForward = atmStrike(atmOptionDate, atmSwapTenor);
        double shift = atmVol_.currentLink().shift(atmOptionTime,
                atmTimeLength);

        double[][] atmForwards = new double[2][2];
        double[][] atmShifts = new double[2][2];
        double[][] atmVols = new double[2][2];
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                atmForwards[i][j] = atmStrike(optionsDateNodes[i],
                        swapTenorNodes[j]);
                atmShifts[i][j] = atmVol_.currentLink().shift(
                        optionsNodes[i], swapLengthsNodes[j]);
                if (!marketSpreads)
                    atmVols[i][j] = atmVol_.currentLink().volatility(
                            optionsDateNodes[i], swapTenorNodes[j],
                            atmForwards[i][j]);
            }
        }

        double[] result = new double[nStrikes_];
        for (int k = 0; k < nStrikes_; ++k) {
            double strike = Math.max(atmForward + strikeSpreads_[k],
                    cutoffStrike_ - shift);
            double moneyness = (atmForward + shift) / (strike + shift);

            double[][] strikes = new double[2][2];
            double[][] spreadVols = new double[2][2];
            for (int i = 0; i < 2; ++i) {
                for (int j = 0; j < 2; ++j) {
                    if (volatilityType_ == Normal)
                        strikes[i][j] = atmForwards[i][j] + strike
                                - atmForward;
                    else
                        strikes[i][j] = (atmForwards[i][j]
                                + atmShifts[i][j]) / moneyness
                                - atmShifts[i][j];
                    if (marketSpreads) {
                        int section = (optionTimesPreviousIndex + i)
                                * nSwapTenors_
                                + swapLengthsPreviousIndex + j;
                        if (nStrikes_ == 1) {
                            spreadVols[i][j] = volSpreads_.get(section)
                                    .get(0).currentLink().value();
                        } else {
                            double[] quotedSpreads = new double[nStrikes_];
                            for (int n = 0; n < nStrikes_; ++n)
                                quotedSpreads[n] = volSpreads_.get(section)
                                        .get(n).currentLink().value();
                            LinearInterpolation strikeInterpolation =
                                    new LinearInterpolation(strikeSpreads_,
                                            quotedSpreads);
                            strikeInterpolation.enableExtrapolation();
                            spreadVols[i][j] = strikeInterpolation.value(
                                    strikes[i][j] - atmForwards[i][j], true);
                        }
                    } else {
                        spreadVols[i][j] = smiles.get(i).get(j)
                                .volatility(strikes[i][j]) - atmVols[i][j];
                    }
                }
            }
            Cube localInterpolator = new Cube(optionsDateNodes,
                    swapTenorNodes, optionsNodes, swapLengthsNodes, 1);
            localInterpolator.setLayer(0, spreadVols);
            localInterpolator.updateInterpolators();

            result[k] = localInterpolator.evaluate(atmOptionTime,
                    atmTimeLength)[0];
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Recalibration
    // ------------------------------------------------------------------

    public void recalibration(double beta, Period swapTenor) {
        double[] betaVector = new double[nOptionTenors_];
        Arrays.fill(betaVector, beta);
        recalibration(betaVector, swapTenor);
    }

    public void recalibration(double[] beta, Period swapTenor) {
        calculate();

        QL_REQUIRE(beta.length == nOptionTenors_,
                "beta size (" + beta.length
                        + ") must be equal to number of option tenors ("
                        + nOptionTenors_ + ")");

        Period[] swapTenors = marketVolCube_.swapTenors();
        int k = indexOf(swapTenors, swapTenor);
        QL_REQUIRE(k != swapTenors.length,
                "swap tenor (" + swapTenor + ") not found");

        for (int i = 0; i < nOptionTenors_; ++i)
            parametersGuess_.setElement(1, i, k, beta[i]);

        parametersGuess_.updateInterpolators();
        volCubeAtmCalibrated_ = marketVolCube_.copy();
        if (singlePassCalibration_) {
            fillVolatilityCube(true);
            sabrCalibrationSection(volCubeAtmCalibrated_, denseParameters_,
                    swapTenor);
            sparseParameters_ = denseParameters_.copy();
        } else {
            sabrCalibrationSection(marketVolCube_, sparseParameters_,
                    swapTenor);
        }
        if (isAtmCalibrated_ && !singlePassCalibration_) {
            fillVolatilityCube();
            sabrCalibrationSection(volCubeAtmCalibrated_, denseParameters_,
                    swapTenor);
        }
        notifyObservers();
    }

    public void recalibration(List<Period> swapLengths,
                              double[] beta,
                              Period swapTenor) {
        QL_REQUIRE(beta.length == swapLengths.size(),
                "beta size (" + beta.length
                        + ") must be equal to number of swap lengths ("
                        + swapLengths.size() + ")");

        double[] betaTimes = new double[beta.length];
        for (int i = 0; i < beta.length; ++i)
            betaTimes[i] = timeFromReference(
                    optionDateFromTenor(swapLengths.get(i)));

        LinearInterpolation betaInterpolation = new LinearInterpolation(
                betaTimes, beta.clone());

        double[] times = optionTimes();
        double[] cubeBeta = new double[times.length];
        for (int i = 0; i < times.length; ++i) {
            double t = times[i];
            // flat extrapolation ensures admissable values
            if (t < betaTimes[0])
                t = betaTimes[0];
            if (t > betaTimes[betaTimes.length - 1])
                t = betaTimes[betaTimes.length - 1];
            cubeBeta[i] = betaInterpolation.value(t, true);
        }

        recalibration(cubeBeta, swapTenor);
    }

    // ------------------------------------------------------------------
    // Observer handling
    // ------------------------------------------------------------------

    protected void registerWithParametersGuess() {
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < nOptionTenors_; ++j)
                for (int k = 0; k < nSwapTenors_; ++k)
                    privateObserver_.registerWith(parametersGuessQuotes_
                            .get(j * nSwapTenors_ + k).get(i).get());
    }

    protected void setParameterGuess() {
        // Use the underlying arrays directly: the public accessors call
        // calculate(), which would run the whole calibration before the
        // parameter guess exists.
        parametersGuess_ = new Cube(
                optionDates_.toArray(new Date[0]),
                swapTenors_.toArray(new Period[0]),
                optionTimes_, swapLengths_, 4, true, backwardFlat_);
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < nOptionTenors_; ++j)
                for (int k = 0; k < nSwapTenors_; ++k)
                    parametersGuess_.setElement(i, j, k,
                            parametersGuessQuotes_.get(
                                    j * nSwapTenors_ + k).get(i)
                                    .currentLink().value());
        parametersGuess_.updateInterpolators();
    }

    private final class PrivateObserver implements Observer {
        private final Set<Observable> registered = new HashSet<>();

        @Override
        public void registerWith(Observable observable) {
            if (observable != null) {
                observable.registerObserver(this);
                registered.add(observable);
            }
        }

        @Override
        public void unregisterWithAll() {
            for (Observable observable : registered)
                observable.unregisterObserver(this);
            registered.clear();
        }

        @Override
        public void unregisterWith(Observable observable) {
            if (observable != null) {
                observable.unregisterObserver(this);
                registered.remove(observable);
            }
        }

        @Override
        public void update() {
            setParameterGuess();
            SabrSwaptionVolatilityCube.this.update();
        }
    }

    // ------------------------------------------------------------------
    // Cube
    // ------------------------------------------------------------------

    /**
     * Cubic grid of values over (option time, swap length), with one
     * interpolator per layer. Ported from the official nested
     * {@code XabrSwaptionVolatilityCube::Cube}.
     */
    public static final class Cube {

        private double[] optionTimes_;
        private double[] swapLengths_;
        private Date[] optionDates_;
        private Period[] swapTenors_;
        private int nLayers_;
        private double[][][] points_;
        private double[][][] transposedPoints_;
        private final boolean extrapolation_;
        private final boolean backwardFlat_;
        private Interpolation2D[] interpolators_;

        public Cube(Date[] optionDates,
                    Period[] swapTenors,
                    double[] optionTimes,
                    double[] swapLengths,
                    int nLayers) {
            this(optionDates, swapTenors, optionTimes, swapLengths, nLayers,
                    true, false);
        }

        public Cube(Date[] optionDates,
                    Period[] swapTenors,
                    double[] optionTimes,
                    double[] swapLengths,
                    int nLayers,
                    boolean extrapolation,
                    boolean backwardFlat) {
            QL_REQUIRE(optionTimes.length > 1,
                    "Cube::Cube(...): optionTimes.size()<2");
            QL_REQUIRE(swapLengths.length > 1,
                    "Cube::Cube(...): swapLengths.size()<2");
            QL_REQUIRE(optionTimes.length == optionDates.length,
                    "Cube::Cube(...): optionTimes/optionDates mismatch");
            QL_REQUIRE(swapTenors.length == swapLengths.length,
                    "Cube::Cube(...): swapTenors/swapLengths mismatch");

            optionTimes_ = optionTimes.clone();
            swapLengths_ = swapLengths.clone();
            optionDates_ = copyDates(optionDates);
            swapTenors_ = copyPeriods(swapTenors);
            nLayers_ = nLayers;
            extrapolation_ = extrapolation;
            backwardFlat_ = backwardFlat;
            points_ = new double[nLayers][optionTimes_.length][swapLengths_.length];
            transposedPoints_ = new double[nLayers][swapLengths_.length][optionTimes_.length];
            interpolators_ = new Interpolation2D[nLayers];
            updateInterpolators();
        }

        public Cube copy() {
            Cube c = new Cube(optionDates_, swapTenors_, optionTimes_,
                    swapLengths_, nLayers_, extrapolation_, backwardFlat_);
            for (int k = 0; k < nLayers_; ++k)
                for (int j = 0; j < optionTimes_.length; ++j)
                    System.arraycopy(points_[k][j], 0, c.points_[k][j], 0,
                            swapLengths_.length);
            c.updateInterpolators();
            return c;
        }

        public void setElement(int indexOfLayer,
                               int indexOfRow,
                               int indexOfColumn,
                               double x) {
            QL_REQUIRE(indexOfLayer < nLayers_,
                    "Cube::setElement: incompatible IndexOfLayer ");
            QL_REQUIRE(indexOfRow < optionTimes_.length,
                    "Cube::setElement: incompatible IndexOfRow");
            QL_REQUIRE(indexOfColumn < swapLengths_.length,
                    "Cube::setElement: incompatible IndexOfColumn");
            points_[indexOfLayer][indexOfRow][indexOfColumn] = x;
        }

        public void setPoints(double[][][] x) {
            QL_REQUIRE(x.length == nLayers_,
                    "Cube::setPoints: incompatible number of layers ");
            QL_REQUIRE(x[0].length == optionTimes_.length,
                    "Cube::setPoints: incompatible size 1");
            QL_REQUIRE(x[0][0].length == swapLengths_.length,
                    "Cube::setPoints: incompatible size 2");
            points_ = x;
        }

        public void setLayer(int i, double[][] x) {
            QL_REQUIRE(i < nLayers_,
                    "Cube::setLayer: incompatible number of layer ");
            QL_REQUIRE(x.length == optionTimes_.length,
                    "Cube::setLayer: incompatible size 1");
            QL_REQUIRE(x[0].length == swapLengths_.length,
                    "Cube::setLayer: incompatible size 2");
            for (int j = 0; j < optionTimes_.length; ++j)
                System.arraycopy(x[j], 0, points_[i][j], 0,
                        swapLengths_.length);
        }

        public void setPoint(Date optionDate,
                             Period swapTenor,
                             double optionTime,
                             double swapLength,
                             double[] point) {
            boolean expandOptionTimes = !contains(optionTimes_, optionTime);
            boolean expandSwapLengths = !contains(swapLengths_, swapLength);

            int optionTimesIndex = lowerBound(optionTimes_, optionTime);
            int swapLengthsIndex = lowerBound(swapLengths_, swapLength);

            if (expandOptionTimes || expandSwapLengths)
                expandLayers(optionTimesIndex, expandOptionTimes,
                        swapLengthsIndex, expandSwapLengths);

            for (int k = 0; k < nLayers_; ++k)
                points_[k][optionTimesIndex][swapLengthsIndex] = point[k];

            optionTimes_[optionTimesIndex] = optionTime;
            swapLengths_[swapLengthsIndex] = swapLength;
            optionDates_[optionTimesIndex] = optionDate.copy();
            swapTenors_[swapLengthsIndex] = swapTenor.clone();
        }

        public void expandLayers(int i,
                                 boolean expandOptionTimes,
                                 int j,
                                 boolean expandSwapLengths) {
            QL_REQUIRE(i <= optionTimes_.length,
                    "Cube::expandLayers: incompatible size 1");
            QL_REQUIRE(j <= swapLengths_.length,
                    "Cube::expandLayers: incompatible size 2");

            int oldNOption = optionTimes_.length;
            int oldNSwap = swapLengths_.length;
            int newNOption = oldNOption + (expandOptionTimes ? 1 : 0);
            int newNSwap = oldNSwap + (expandSwapLengths ? 1 : 0);

            double[] newOptionTimes = new double[newNOption];
            Date[] newOptionDates = new Date[newNOption];
            for (int u = 0; u < newNOption; ++u) {
                int old = u;
                if (expandOptionTimes && u == i) {
                    newOptionTimes[u] = 0.0;
                    newOptionDates[u] = new Date();
                } else {
                    if (expandOptionTimes && u > i)
                        old = u - 1;
                    newOptionTimes[u] = optionTimes_[old];
                    newOptionDates[u] = optionDates_[old].copy();
                }
            }

            double[] newSwapLengths = new double[newNSwap];
            Period[] newSwapTenors = new Period[newNSwap];
            for (int v = 0; v < newNSwap; ++v) {
                int old = v;
                if (expandSwapLengths && v == j) {
                    newSwapLengths[v] = 0.0;
                    newSwapTenors[v] = new Period();
                } else {
                    if (expandSwapLengths && v > j)
                        old = v - 1;
                    newSwapLengths[v] = swapLengths_[old];
                    newSwapTenors[v] = swapTenors_[old].clone();
                }
            }

            double[][][] newPoints = new double[nLayers_][newNOption][newNSwap];
            for (int k = 0; k < nLayers_; ++k) {
                for (int u = 0; u < oldNOption; ++u) {
                    int indexOfRow = u;
                    if (u >= i && expandOptionTimes)
                        indexOfRow = u + 1;
                    for (int v = 0; v < oldNSwap; ++v) {
                        int indexOfCol = v;
                        if (v >= j && expandSwapLengths)
                            indexOfCol = v + 1;
                        newPoints[k][indexOfRow][indexOfCol]
                                = points_[k][u][v];
                    }
                }
            }

            optionTimes_ = newOptionTimes;
            optionDates_ = newOptionDates;
            swapLengths_ = newSwapLengths;
            swapTenors_ = newSwapTenors;
            points_ = newPoints;
        }

        public double[] optionTimes() {
            return optionTimes_.clone();
        }

        public double[] swapLengths() {
            return swapLengths_.clone();
        }

        public Date[] optionDates() {
            return copyDates(optionDates_);
        }

        public Period[] swapTenors() {
            return copyPeriods(swapTenors_);
        }

        public double point(int layer, int row, int column) {
            return points_[layer][row][column];
        }

        public double[][] layer(int i) {
            double[][] result = new double[optionTimes_.length][];
            for (int j = 0; j < optionTimes_.length; ++j)
                result[j] = points_[i][j].clone();
            return result;
        }

        public Matrix layerMatrix(int i) {
            return new Matrix(layer(i));
        }

        public double[] evaluate(double optionTime, double swapLength) {
            double[] result = new double[nLayers_];
            for (int k = 0; k < nLayers_; ++k)
                result[k] = interpolators_[k].value(optionTime, swapLength,
                        true);
            return result;
        }

        public void updateInterpolators() {
            // Reallocate: expandLayers() can change the grid dimensions.
            double[][][] transposed =
                    new double[nLayers_][swapLengths_.length][optionTimes_.length];
            for (int k = 0; k < nLayers_; ++k) {
                for (int s = 0; s < swapLengths_.length; ++s)
                    for (int o = 0; o < optionTimes_.length; ++o)
                        transposed[k][s][o] = points_[k][o][s];

                Interpolation2D interpolation;
                if (k <= 4 && backwardFlat_)
                    interpolation = new BackwardflatLinearInterpolation(
                            optionTimes_, swapLengths_, transposed[k]);
                else
                    interpolation = new BilinearInterpolation(optionTimes_,
                            swapLengths_, transposed[k]);
                Interpolation2D flat = new FlatExtrapolator2D(interpolation);
                flat.enableExtrapolation();
                interpolators_[k] = flat;
            }
            transposedPoints_ = transposed;
        }

        public Matrix browse() {
            int n = swapLengths_.length * optionTimes_.length;
            double[][] result = new double[n][nLayers_ + 2];
            for (int i = 0; i < swapLengths_.length; ++i) {
                for (int j = 0; j < optionTimes_.length; ++j) {
                    int row = i * optionTimes_.length + j;
                    result[row][0] = swapLengths_[i];
                    result[row][1] = optionTimes_[j];
                    for (int k = 0; k < nLayers_; ++k)
                        result[row][2 + k] = points_[k][j][i];
                }
            }
            return new Matrix(result);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Cube newCube(Date[] optionDates,
                         Period[] swapTenors,
                         double[] optionTimes,
                         double[] swapLengths,
                         int nLayers) {
        return new Cube(optionDates, swapTenors, optionTimes, swapLengths,
                nLayers, true, backwardFlat_);
    }

    private Cube newCube(List<Date> optionDates,
                         List<Period> swapTenors,
                         double[] optionTimes,
                         double[] swapLengths,
                         int nLayers) {
        return newCube(optionDates.toArray(new Date[0]),
                swapTenors.toArray(new Period[0]), optionTimes, swapLengths,
                nLayers);
    }

    private static List<List<Handle<Quote>>> copy(
            List<List<Handle<Quote>>> rows) {
        List<List<Handle<Quote>>> result = new ArrayList<>(rows.size());
        for (List<Handle<Quote>> row : rows)
            result.add(new ArrayList<>(row));
        return result;
    }

    private static double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; ++i)
            result[i] = values.get(i);
        return result;
    }

    private static double toDouble(EndCriteria.Type type) {
        return type.ordinal();
    }

    private static int indexOf(Period[] values, Period value) {
        for (int i = 0; i < values.length; ++i)
            if (TimeUtils.equals(values[i], value))
                return i;
        return values.length;
    }

    private static boolean contains(double[] values, double value) {
        for (double v : values)
            if (v == value)
                return true;
        return false;
    }

    private static int lowerBound(double[] values, double value) {
        int i = 0;
        while (i < values.length && values[i] < value)
            ++i;
        return i;
    }

    private static Date[] copyDates(Date[] values) {
        Date[] result = new Date[values.length];
        for (int i = 0; i < values.length; ++i)
            result[i] = values[i].copy();
        return result;
    }

    private static Period[] copyPeriods(Period[] values) {
        Period[] result = new Period[values.length];
        for (int i = 0; i < values.length; ++i)
            result[i] = values[i].clone();
        return result;
    }

    private static double[] concat(double[] a, double[] b) {
        double[] result = new double[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static double[] sortedUnique(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = 0;
        for (int i = 0; i < sorted.length; ++i)
            if (i == 0 || sorted[i] != sorted[n - 1])
                sorted[n++] = sorted[i];
        return Arrays.copyOf(sorted, n);
    }

    private static Date[] concatDates(List<Date> a, List<Date> b) {
        Date[] result = new Date[a.size() + b.size()];
        for (int i = 0; i < a.size(); ++i)
            result[i] = a.get(i);
        for (int i = 0; i < b.size(); ++i)
            result[a.size() + i] = b.get(i);
        return result;
    }

    private static Date[] sortedUniqueDates(Date[] values) {
        Date[] sorted = copyDates(values);
        Arrays.sort(sorted, Comparator.comparingInt(Date::serialNumber));
        int n = 0;
        for (int i = 0; i < sorted.length; ++i)
            if (i == 0
                    || sorted[i].serialNumber() != sorted[n - 1].serialNumber())
                sorted[n++] = sorted[i];
        return Arrays.copyOf(sorted, n);
    }

    private static Period[] concatPeriods(List<Period> a, List<Period> b) {
        Period[] result = new Period[a.size() + b.size()];
        for (int i = 0; i < a.size(); ++i)
            result[i] = a.get(i);
        for (int i = 0; i < b.size(); ++i)
            result[a.size() + i] = b.get(i);
        return result;
    }

    private static Period[] sortedUniquePeriods(Period[] values) {
        Period[] sorted = copyPeriods(values);
        Arrays.sort(sorted, (p1, p2) -> {
            if (TimeUtils.equals(p1, p2))
                return 0;
            return TimeUtils.less(p1, p2) ? -1 : 1;
        });
        int n = 0;
        for (int i = 0; i < sorted.length; ++i)
            if (i == 0 || !TimeUtils.equals(sorted[i], sorted[n - 1]))
                sorted[n++] = sorted[i];
        return Arrays.copyOf(sorted, n);
    }
}
