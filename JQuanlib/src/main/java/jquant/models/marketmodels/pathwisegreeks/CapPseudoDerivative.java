package jquant.models.marketmodels.pathwisegreeks;

import jquant.Option;
import jquant.math.Function;
import jquant.math.Matrix;
import jquant.math.solvers1d.Brent;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.pricingengines.BlackFormula;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/swaptionpseudojacobian.hpp/.cpp}
 * port of {@code CapPseudoDerivative}.
 *
 * <p>In order to compute market vegas, we need a class that gives the
 * derivative of a cap implied vol against changes in pseudo-root elements.
 *
 * <p>This is tested in the pathwise vegas routine in MarketModels.cpp.
 */
public class CapPseudoDerivative {

    private final MarketModel inputModel_;
    private final List<Matrix> volatilityDerivatives_ = new ArrayList<>();
    private final List<Matrix> priceDerivatives_ = new ArrayList<>();

    private final double impliedVolatility_;
    private final double vega_;
    private final double firstDF_;

    /**
     * This class doesn't copy anything so fast to create and use but make
     * sure everything it uses stays in scope.
     */
    private static class QuickCap implements Function {
        private final double strike_;
        private final List<Double> annuities_;
        private final List<Double> currentRates_;
        private final List<Double> expiries_;
        private final double price_;

        QuickCap(double strike,
                 final List<Double> annuities,
                 final List<Double> currentRates,
                 final List<Double> expiries,
                 double price) {
            strike_ = strike;
            annuities_ = annuities;
            currentRates_ = currentRates;
            expiries_ = expiries;
            price_ = price;
        }

        @Override
        public double value(double volatility) {
            double price = 0.0;
            for (int i = 0; i < annuities_.size(); ++i) {
                price += BlackFormula.blackFormula(Option.Type.Call, strike_,
                        currentRates_.get(i),
                        volatility * Math.sqrt(expiries_.get(i)),
                        annuities_.get(i),
                        0.0);
            }
            return price - price_;
        }

        double vega(double volatility) {
            double vega = 0.0;
            for (int i = 0; i < annuities_.size(); ++i) {
                vega += BlackFormula.blackFormulaVolDerivative(strike_,
                        currentRates_.get(i),
                        volatility * Math.sqrt(expiries_.get(i)),
                        expiries_.get(i),
                        annuities_.get(i),
                        0.0);
            }
            return vega;
        }
    }

    public CapPseudoDerivative(final MarketModel inputModel,
                               double strike,
                               int startIndex,
                               int endIndex,
                               double firstDF) {
        inputModel_ = inputModel;
        firstDF_ = firstDF;
        QL_REQUIRE(startIndex < endIndex,
                "for a cap pseudo derivative the start of the cap must be before the end");
        QL_REQUIRE(endIndex <= inputModel.numberOfRates(),
                "for a cap pseudo derivative the end of the cap must before the end of the rates");

        int numberCaplets = endIndex - startIndex;
        int numberRates = inputModel.numberOfRates();
        int factors = inputModel.numberOfFactors();
        LMMCurveState curve = new LMMCurveState(inputModel.evolution().rateTimes());
        curve.setOnForwardRates(inputModel.initialRates());

        final Matrix totalCovariance =
                inputModel.totalCovariance(inputModel.numberOfSteps() - 1);

        List<Double> displacedImpliedVols = new ArrayList<>();
        List<Double> annuities = new ArrayList<>();
        List<Double> initialRates = new ArrayList<>();
        List<Double> expiries = new ArrayList<>();
        for (int i = 0; i < numberCaplets; ++i) {
            displacedImpliedVols.add(0.0);
            annuities.add(0.0);
            initialRates.add(0.0);
            expiries.add(0.0);
        }

        double capPrice = 0.0;

        double guess = 0.0;
        double minVol = 1e10;
        double maxVol = 0.0;

        for (int j = startIndex; j < endIndex; ++j) {
            int capletIndex = j - startIndex;
            double resetTime = inputModel.evolution().rateTimes().get(j);
            expiries.set(capletIndex, resetTime);

            double sd = Math.sqrt(totalCovariance.get(j, j));
            displacedImpliedVols.set(capletIndex,
                    Math.sqrt(totalCovariance.get(j, j) / resetTime));

            double forward = inputModel.initialRates().get(j);
            initialRates.set(capletIndex, forward);

            double annuity = curve.discountRatio(j + 1, 0)
                    * inputModel.evolution().rateTaus().get(j) * firstDF_;
            annuities.set(capletIndex, annuity);

            double displacement = inputModel.displacements().get(j);

            guess += displacedImpliedVols.get(capletIndex)
                    * (forward + displacement) / forward;
            minVol = Math.min(minVol, displacedImpliedVols.get(capletIndex));
            maxVol = Math.max(maxVol, displacedImpliedVols.get(capletIndex)
                    * (forward + displacement) / forward);

            double capletPrice = BlackFormula.blackFormula(Option.Type.Call,
                    strike,
                    forward,
                    sd,
                    annuity,
                    displacement
            );

            capPrice += capletPrice;
        }

        guess /= numberCaplets;

        for (int step = 0; step < inputModel.evolution().numberOfSteps(); ++step) {
            Matrix thisDerivative = new Matrix(numberRates, factors, 0.0);

            for (int rate = Math.max(inputModel.evolution().firstAliveRate().get(step),
                    startIndex);
                 rate < endIndex; ++rate) {
                for (int f = 0; f < factors; ++f) {
                    double expiry = inputModel.evolution().rateTimes().get(rate);
                    double volDerivative = inputModel.pseudoRoot(step).get(rate, f)
                            / (displacedImpliedVols.get(rate - startIndex) * expiry);
                    double capletVega = BlackFormula.blackFormulaVolDerivative(strike,
                            inputModel.initialRates().get(rate),
                            displacedImpliedVols.get(rate - startIndex)
                                    * Math.sqrt(expiry),
                            expiry,
                            annuities.get(rate - startIndex),
                            inputModel.displacements().get(rate));

                    // note that the cap derivative is equal to one of the
                    // caplet ones so we lose a loop
                    thisDerivative.set(rate, f, volDerivative * capletVega);
                }
            }

            priceDerivatives_.add(SwaptionPseudoDerivative.copyMatrix(thisDerivative));
        }

        QuickCap capPricer = new QuickCap(strike, annuities, initialRates, expiries,
                capPrice);

        int maxEvaluations = 1000;
        double accuracy = 1E-6;

        Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        impliedVolatility_ = solver.solve(capPricer, accuracy, guess,
                minVol * 0.99, maxVol * 1.01);

        vega_ = capPricer.vega(impliedVolatility_);

        for (int step = 0; step < inputModel.evolution().numberOfSteps(); ++step) {
            Matrix thisDerivative = new Matrix(numberRates, factors, 0.0);

            for (int rate = Math.max(inputModel.evolution().firstAliveRate().get(step),
                    startIndex);
                 rate < endIndex; ++rate) {
                for (int f = 0; f < factors; ++f) {
                    thisDerivative.set(rate, f,
                            priceDerivatives_.get(step).get(rate, f) / vega_);
                }
            }

            volatilityDerivatives_.add(
                    SwaptionPseudoDerivative.copyMatrix(thisDerivative));
        }
    }

    public Matrix priceDerivative(int i) {
        return priceDerivatives_.get(i);
    }

    public Matrix volatilityDerivative(int i) {
        return volatilityDerivatives_.get(i);
    }

    public double impliedVolatility() {
        return impliedVolatility_;
    }
}
