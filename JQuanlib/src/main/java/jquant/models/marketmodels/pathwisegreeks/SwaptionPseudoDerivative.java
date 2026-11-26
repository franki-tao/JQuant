package jquant.models.marketmodels.pathwisegreeks;

import jquant.math.Matrix;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.SwapForwardMappings;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/swaptionpseudojacobian.hpp/.cpp}
 * port.
 *
 * <p>In order to compute market vegas, we need a class that gives the
 * derivative of a swaption implied vol against changes in pseudo-root
 * elements. This is that class.
 *
 * <p>This is tested in the pathwise vegas routine in MarketModels.cpp.
 */
public class SwaptionPseudoDerivative {

    private final MarketModel inputModel_;
    private final List<Matrix> varianceDerivatives_ = new ArrayList<>();
    private final List<Matrix> volatilityDerivatives_ = new ArrayList<>();

    private final double impliedVolatility_;
    private final double expiry_;
    private final double variance_;

    public SwaptionPseudoDerivative(final MarketModel inputModel,
                                    int startIndex,
                                    int endIndex) {
        inputModel_ = inputModel;

        List<Double> subRateTimes = new ArrayList<>(
                inputModel.evolution().rateTimes()
                        .subList(startIndex, endIndex + 1));

        List<Double> subForwards = new ArrayList<>(
                inputModel.initialRates().subList(startIndex, endIndex));

        LMMCurveState cs = new LMMCurveState(subRateTimes);
        cs.setOnForwardRates(subForwards);

        Matrix zed = SwapForwardMappings.coterminalSwapZedMatrix(cs,
                inputModel.displacements().get(0));
        int factors = inputModel.numberOfFactors();

        // first compute variance and implied vol

        double variance = 0.0;
        int index = 0;

        while (index < inputModel.evolution().numberOfSteps()
                && inputModel.evolution().firstAliveRate().get(index) <= startIndex) {
            final Matrix thisPseudo = inputModel.pseudoRoot(index);

            double thisVariance = 0.0;
            for (int j = startIndex; j < endIndex; ++j)
                for (int k = startIndex; k < endIndex; ++k)
                    for (int f = 0; f < factors; ++f)
                        thisVariance += zed.get(0, j - startIndex) * thisPseudo.get(j, f)
                                * thisPseudo.get(k, f) * zed.get(0, k - startIndex);

            variance += thisVariance;

            ++index;
        }
        variance_ = variance;

        int stopIndex = index;

        expiry_ = subRateTimes.get(0);

        impliedVolatility_ = Math.sqrt(variance_ / expiry_);

        double scale = 0.5 * (1.0 / expiry_) / impliedVolatility_;

        int numberRates = inputModel.evolution().numberOfRates();

        Matrix thisDerivative = new Matrix(numberRates, factors, 0.0);
        Matrix nullDerivative = new Matrix(numberRates, factors, 0.0);

        index = 0;

        while (index < stopIndex) {
            final Matrix thisPseudo = inputModel.pseudoRoot(index);

            for (int rate = startIndex; rate < endIndex; ++rate) {
                int zIndex = rate - startIndex;
                for (int f = 0; f < factors; ++f) {
                    double sum = 0.0;
                    for (int rate2 = startIndex; rate2 < endIndex; ++rate2) {
                        int zIndex2 = rate2 - startIndex;
                        sum += zed.get(0, zIndex2) * thisPseudo.get(rate2, f);
                    }
                    sum *= 2.0 * zed.get(0, zIndex);

                    thisDerivative.set(rate, f, sum);
                }
            }

            // Official push_back copies the matrix; Java must deep-copy.
            varianceDerivatives_.add(copyMatrix(thisDerivative));

            for (int rate = startIndex; rate < endIndex; ++rate)
                for (int f = 0; f < factors; ++f)
                    thisDerivative.set(rate, f,
                            thisDerivative.get(rate, f) * scale);

            volatilityDerivatives_.add(copyMatrix(thisDerivative));

            ++index;
        }

        for (; index < inputModel.evolution().numberOfSteps(); ++index) {
            varianceDerivatives_.add(copyMatrix(nullDerivative));
            volatilityDerivatives_.add(copyMatrix(nullDerivative));
        }
    }

    public Matrix varianceDerivative(int i) {
        return varianceDerivatives_.get(i);
    }

    public Matrix volatilityDerivative(int i) {
        return volatilityDerivatives_.get(i);
    }

    public double impliedVolatility() {
        return impliedVolatility_;
    }

    public double variance() {
        return variance_;
    }

    public double expiry() {
        return expiry_;
    }

    static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
