package jquant.models.marketmodels;

import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.models.marketmodels.evolvers.LogNormalFwdRateEuler;
import jquant.models.marketmodels.pathwisegreeks.RatePseudoRootJacobian;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/pathwiseaccountingengine.hpp/.cpp}
 * port of {@code PathwiseVegasAccountingEngine}.
 *
 * <p>Engine collecting cash flows along a market-model simulation for doing
 * pathwise computation of Deltas and vegas using the Giles--Glasserman
 * smoking adjoints method. Note only works with displaced LMM.
 *
 * <p>The method is intimately connected with log-normal Euler evolution.
 *
 * <p>We must work with discretely compounding MM account. To compute a vega
 * means changing the pseudo-square root at each time step, so for each vega we
 * have a vector of matrices. We do the outermost vector by time step and inner
 * one by which vega.
 *
 * <p>This is tested in {@code MarketModelTest::testPathwiseVegas}.
 */
public class PathwiseVegasAccountingEngine {

    private final LogNormalFwdRateEuler evolver_;
    private final MarketModelPathwiseMultiProduct product_;
    private final MarketModel pseudoRootStructure_;
    private List<Integer> numeraires_;

    private final double initialNumeraireValue_;
    private final int numberProducts_;
    private final int numberRates_;
    private final int numberCashFlowTimes_;
    private final int numberSteps_;
    private final int numberBumps_;

    private final List<RatePseudoRootJacobian> jacobianComputers_ = new ArrayList<>();

    private final boolean doDeflation_;

    // workspace
    private List<Double> currentForwards_;
    private List<Double> lastForwards_;
    private final List<Double> numerairesHeld_;
    private final List<Integer> numberCashFlowsThisStep_;
    private final List<List<MarketModelPathwiseMultiProduct.CashFlow>> cashFlowsGenerated_;
    private final List<MarketModelPathwiseDiscounter> discounters_ = new ArrayList<>();

    private final List<Matrix> V_; // one V for each product

    private final Matrix LIBORRatios_; // dimensions are step and rate number
    private final Matrix Discounts_; // dimensions are step and rate number, P(t_0, t_j)

    private final Matrix StepsDiscountsSquared_; // dimensions are step and rate number
    private final List<Double> stepsDiscounts_;

    private final Matrix LIBORRates_; // dimensions are step and rate number
    private final Matrix partials_; // dimensions are factor and rate

    private final Matrix vegasThisPath_; // dimensions are product and which vega
    private final List<Matrix> jacobiansThisPaths_ = new ArrayList<>(); // dimensions are step, rate and factor

    private final List<Double> deflatorAndDerivatives_;
    private final List<Double> fullDerivatives_;

    private final List<List<Integer>> numberCashFlowsThisIndex_;
    private final List<Matrix> totalCashFlowsThisIndex_;

    private final List<List<Integer>> cashFlowIndicesThisStep_ = new ArrayList<>();

    public PathwiseVegasAccountingEngine(LogNormalFwdRateEuler evolver, // method relies heavily on LMM Euler
                                         final MarketModelPathwiseMultiProduct product,
                                         MarketModel pseudoRootStructure, // we need pseudo-roots and displacements
                                         final List<List<Matrix>> vegaBumps,
                                         double initialNumeraireValue) {
        evolver_ = evolver;
        // Official Clone<MarketModelPathwiseMultiProduct> copies the product.
        product_ = product.copy();
        pseudoRootStructure_ = pseudoRootStructure;
        initialNumeraireValue_ = initialNumeraireValue;
        numberProducts_ = product_.numberOfProducts();
        doDeflation_ = !product_.alreadyDeflated();

        numberRates_ = pseudoRootStructure_.numberOfRates();
        numberSteps_ = pseudoRootStructure_.numberOfSteps();

        stepsDiscounts_ = new ArrayList<>();
        for (int i = 0; i < numberRates_ + 1; ++i)
            stepsDiscounts_.add(0.0);
        stepsDiscounts_.set(0, 1.0);

        final EvolutionDescription evolution = pseudoRootStructure_.evolution();
        numeraires_ = EvolutionDescription.moneyMarketMeasure(evolution);

        QL_REQUIRE(vegaBumps.size() == numberSteps_,
                "we need one vector of vega bumps for each step.");

        numberBumps_ = vegaBumps.get(0).size();

        for (int i = 0; i < numberSteps_; ++i) {
            int thisSize = vegaBumps.get(i).size();
            QL_REQUIRE(thisSize == numberBumps_,
                    "We must have precisely the same number of bumps for each step.");
            jacobianComputers_.add(new RatePseudoRootJacobian(
                    pseudoRootStructure_.pseudoRoot(i),
                    evolution.firstAliveRate().get(i),
                    numeraires_.get(i),
                    evolution.rateTaus(),
                    vegaBumps.get(i),
                    pseudoRootStructure_.displacements()));

            jacobiansThisPaths_.add(new Matrix(numberBumps_, numberRates_, 0.0));
        }

        Matrix VModel = new Matrix(numberSteps_ + 1, numberRates_, 0.0);

        Discounts_ = new Matrix(numberSteps_ + 1, numberRates_ + 1, 0.0);

        for (int i = 0; i <= numberSteps_; ++i)
            Discounts_.set(i, 0, 1.0);

        Matrix modelCashFlowIndex =
                new Matrix(product_.possibleCashFlowTimes().size(), numberRates_ + 1, 0.0);

        numerairesHeld_ = new ArrayList<>();
        numberCashFlowsThisStep_ = new ArrayList<>();
        cashFlowsGenerated_ = new ArrayList<>();
        numberCashFlowsThisIndex_ = new ArrayList<>();
        totalCashFlowsThisIndex_ = new ArrayList<>();
        V_ = new ArrayList<>();

        for (int i = 0; i < numberProducts_; ++i) {
            numerairesHeld_.add(0.0);
            numberCashFlowsThisStep_.add(0);

            List<MarketModelPathwiseMultiProduct.CashFlow> innerCashFlows =
                    new ArrayList<>();
            for (int j = 0; j < product_.maxNumberOfCashFlowsPerProductPerStep(); ++j) {
                MarketModelPathwiseMultiProduct.CashFlow cashFlow =
                        new MarketModelPathwiseMultiProduct.CashFlow();
                cashFlow.timeIndex = 0;
                cashFlow.amount = new ArrayList<>();
                for (int k = 0; k <= numberRates_; ++k)
                    cashFlow.amount.add(0.0);
                innerCashFlows.add(cashFlow);
            }
            cashFlowsGenerated_.add(innerCashFlows);

            List<Integer> numberCashFlows = new ArrayList<>();
            for (int j = 0; j < product_.possibleCashFlowTimes().size(); ++j)
                numberCashFlows.add(0);
            numberCashFlowsThisIndex_.add(numberCashFlows);

            V_.add(copyMatrix(VModel));

            totalCashFlowsThisIndex_.add(copyMatrix(modelCashFlowIndex));
        }

        LIBORRatios_ = copyMatrix(VModel);
        StepsDiscountsSquared_ = copyMatrix(VModel);
        LIBORRates_ = copyMatrix(VModel);

        final List<Double> cashFlowTimes = product_.possibleCashFlowTimes();
        numberCashFlowTimes_ = cashFlowTimes.size();

        final List<Double> rateTimes = product_.evolution().rateTimes();
        final List<Double> evolutionTimes = product_.evolution().evolutionTimes();

        for (double cashFlowTime : cashFlowTimes)
            discounters_.add(new MarketModelPathwiseDiscounter(cashFlowTime, rateTimes));

        // need to check that we are in money market measure

        // we need to allocate cash-flow times to steps, i.e. what is the last
        // step completed before a flow occurs. what we really need is for each
        // step, what cash flow time indices to look at.
        for (int i = 0; i < numberSteps_; ++i)
            cashFlowIndicesThisStep_.add(new ArrayList<>());

        for (int i = 0; i < numberCashFlowTimes_; ++i) {
            int it = CommonUtil.upper_bound(evolutionTimes, cashFlowTimes.get(i));
            if (it != 0)
                --it;
            cashFlowIndicesThisStep_.get(it).add(i);
        }

        partials_ = new Matrix(pseudoRootStructure_.numberOfFactors(), numberRates_, 0.0);

        vegasThisPath_ = new Matrix(numberProducts_, numberBumps_, 0.0);

        deflatorAndDerivatives_ = new ArrayList<>();
        for (int i = 0; i <= numberRates_; ++i)
            deflatorAndDerivatives_.add(0.0);

        fullDerivatives_ = new ArrayList<>();
        for (int i = 0; i < numberRates_; ++i)
            fullDerivatives_.add(0.0);
    }

    private static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }

    private double singlePathValues(List<Double> values) {
        final List<Double> initialForwards = pseudoRootStructure_.initialRates();
        currentForwards_ = new ArrayList<>(initialForwards);
        // clear accumulation variables
        for (int i = 0; i < numberProducts_; ++i) {
            numerairesHeld_.set(i, 0.0);

            for (int j = 0; j < numberCashFlowTimes_; ++j) {
                numberCashFlowsThisIndex_.get(i).set(j, 0);

                for (int k = 0; k <= numberRates_; ++k)
                    totalCashFlowsThisIndex_.get(i).set(j, k, 0.0);
            }

            for (int l = 0; l < numberRates_; ++l)
                for (int m = 0; m <= numberSteps_; ++m)
                    V_.get(i).set(m, l, 0.0);

            for (int p = 0; p < numberBumps_; ++p)
                vegasThisPath_.set(i, p, 0.0);
        }

        double weight = evolver_.startNewPath();
        product_.reset();

        int thisStep;

        boolean done = false;
        do {
            thisStep = evolver_.currentStep();
            int storeStep = thisStep + 1;
            weight *= evolver_.advanceStep();

            done = product_.nextTimeStep(evolver_.currentState(),
                    numberCashFlowsThisStep_,
                    cashFlowsGenerated_);

            lastForwards_ = currentForwards_;
            currentForwards_ = new ArrayList<>(evolver_.currentState().forwardRates());

            for (int i = 0; i < numberRates_; ++i) {
                double x = evolver_.currentState().discountRatio(i + 1, i);
                stepsDiscounts_.set(i + 1, x);
                StepsDiscountsSquared_.set(storeStep, i, x * x);

                LIBORRatios_.set(storeStep, i, currentForwards_.get(i) / lastForwards_.get(i));
                LIBORRates_.set(storeStep, i, currentForwards_.get(i));
                Discounts_.set(storeStep, i + 1,
                        evolver_.currentState().discountRatio(i + 1, 0));
            }

            jacobianComputers_.get(thisStep).getBumps(lastForwards_,
                    stepsDiscounts_,
                    currentForwards_,
                    evolver_.browniansThisStep(),
                    jacobiansThisPaths_.get(thisStep));

            // for each product...
            for (int i = 0; i < numberProducts_; ++i) {
                // ...and each cash flow...
                for (int j = 0; j < numberCashFlowsThisStep_.get(i); ++j) {
                    int k = cashFlowsGenerated_.get(i).get(j).timeIndex;
                    numberCashFlowsThisIndex_.get(i).set(k,
                            numberCashFlowsThisIndex_.get(i).get(k) + 1);

                    for (int l = 0; l <= numberRates_; ++l)
                        totalCashFlowsThisIndex_.get(i).set(k, l,
                                totalCashFlowsThisIndex_.get(i).get(k, l)
                                        + cashFlowsGenerated_.get(i).get(j).amount.get(l)
                                        * weight);
                }
            }

        } while (!done);

        // ok we've gathered cash-flows, still have to backwards computation

        int factors = pseudoRootStructure_.numberOfFactors();
        final List<Double> taus = pseudoRootStructure_.evolution().rateTaus();

        boolean flowsFound = false;

        int finalStepDone = thisStep;

        // must be a signed type as we go negative
        for (int currentStep = numberSteps_ - 1; currentStep >= 0; --currentStep) {
            int stepToUse = Math.min(currentStep, finalStepDone) + 1;

            for (int k = 0; k < cashFlowIndicesThisStep_.get(currentStep).size(); ++k) {
                int cashFlowIndex = cashFlowIndicesThisStep_.get(currentStep).get(k);

                // first check to see if anything actually happened before
                // spending time on computing stuff
                boolean noFlows = true;
                for (int l = 0; l < numberProducts_ && noFlows; ++l)
                    noFlows = noFlows
                            && (numberCashFlowsThisIndex_.get(l).get(cashFlowIndex) == 0);

                flowsFound = flowsFound || !noFlows;

                if (!noFlows) {
                    if (doDeflation_)
                        // get amount to discount cash flow by and amount to
                        // multiply its derivatives by
                        discounters_.get(cashFlowIndex).getFactors(
                                LIBORRates_, Discounts_, stepToUse, deflatorAndDerivatives_);

                    for (int j = 0; j < numberProducts_; ++j) {
                        if (numberCashFlowsThisIndex_.get(j).get(cashFlowIndex) > 0) {
                            double deflatedCashFlow =
                                    totalCashFlowsThisIndex_.get(j).get(cashFlowIndex, 0);
                            if (doDeflation_)
                                deflatedCashFlow *= deflatorAndDerivatives_.get(0);
                            numerairesHeld_.set(j,
                                    numerairesHeld_.get(j) + deflatedCashFlow);

                            for (int i = 1; i <= numberRates_; ++i) {
                                double thisDerivative =
                                        totalCashFlowsThisIndex_.get(j).get(cashFlowIndex, i);
                                if (doDeflation_) {
                                    thisDerivative *= deflatorAndDerivatives_.get(0);
                                    thisDerivative +=
                                            totalCashFlowsThisIndex_.get(j).get(cashFlowIndex, 0)
                                                    * deflatorAndDerivatives_.get(i);
                                    fullDerivatives_.set(i - 1, thisDerivative);
                                } else
                                    fullDerivatives_.set(i - 1, thisDerivative);

                                // zeroth row of V is t=0 not t_0
                                V_.get(j).set(stepToUse, i - 1,
                                        V_.get(j).get(stepToUse, i - 1) + thisDerivative);
                            }

                            // ok we've got the derivatives and stored them,
                            // now add them to vegas.
                            // this corresponds to the \frac{\partial F_n}[\partial theta} term
                            // we add the indirect terms later

                            for (int k2 = 0; k2 < numberBumps_; ++k2)
                                for (int i = 0; i < numberRates_; ++i)
                                    vegasThisPath_.set(j, k2,
                                            vegasThisPath_.get(j, k2)
                                                    + fullDerivatives_.get(i)
                                                    * jacobiansThisPaths_.get(stepToUse - 1)
                                                    .get(k2, i));
                        }
                    }
                }
            }

            // need to do backwards updating
            if (flowsFound) {
                int nextStepToUse = Math.min(currentStep - 1, finalStepDone);
                int nextStepIndex = nextStepToUse + 1;
                if (nextStepIndex != stepToUse) // then we need to update V
                {
                    final Matrix thisPseudoRoot = pseudoRootStructure_.pseudoRoot(currentStep);

                    for (int i = 0; i < numberProducts_; ++i) {
                        // compute partials
                        for (int f = 0; f < factors; ++f) {
                            double libor = LIBORRates_.get(stepToUse, numberRates_ - 1);
                            double V = V_.get(i).get(stepToUse, numberRates_ - 1);
                            double pseudo = thisPseudoRoot.get(numberRates_ - 1, f);
                            double thisPartialTerm = libor * V * pseudo;
                            partials_.set(f, numberRates_ - 1, thisPartialTerm);

                            for (int r = numberRates_ - 2; r >= 0; --r) {
                                double thisPartialTermr =
                                        LIBORRates_.get(stepToUse, r)
                                                * V_.get(i).get(stepToUse, r)
                                                * thisPseudoRoot.get(r, f);

                                partials_.set(f, r,
                                        partials_.get(f, r + 1) + thisPartialTermr);
                            }
                        }

                        for (int j = 0; j < numberRates_; ++j) {
                            double nextV = V_.get(i).get(stepToUse, j)
                                    * LIBORRatios_.get(stepToUse, j);
                            V_.get(i).set(nextStepIndex, j, nextV);

                            double summandTerm = 0.0;
                            for (int f = 0; f < factors; ++f)
                                summandTerm += thisPseudoRoot.get(j, f) * partials_.get(f, j);

                            summandTerm *= taus.get(j)
                                    * StepsDiscountsSquared_.get(stepToUse, j);

                            V_.get(i).set(nextStepIndex, j,
                                    V_.get(i).get(nextStepIndex, j) + summandTerm);
                        }

                        // we've done the Vs now the vegas

                        if (nextStepIndex > 0)
                            for (int l = 0; l < numberBumps_; ++l)
                                for (int j = 0; j < numberRates_; ++j)
                                    vegasThisPath_.set(i, l,
                                            vegasThisPath_.get(i, l)
                                                    + V_.get(i).get(nextStepIndex, j)
                                                    * jacobiansThisPaths_.get(nextStepIndex - 1)
                                                    .get(l, j));
                    }
                }
            }
        }

        // write answer into values

        int entriesPerProduct = 1 + numberRates_ + numberBumps_;

        for (int i = 0; i < numberProducts_; ++i) {
            values.set(i * entriesPerProduct,
                    numerairesHeld_.get(i) * initialNumeraireValue_);
            for (int j = 0; j < numberRates_; ++j)
                values.set(i * entriesPerProduct + 1 + j,
                        V_.get(i).get(0, j) * initialNumeraireValue_);
            for (int k = 0; k < numberBumps_; ++k)
                values.set(i * entriesPerProduct + numberRates_ + k + 1,
                        vegasThisPath_.get(i, k) * initialNumeraireValue_);
        }

        // we have put the weight in already, this results in lower variance
        // since weight changes along the path
        return 1.0;
    }

    public void multiplePathValues(List<Double> means, List<Double> errors,
                                   int numberOfPaths) {
        int size = product_.numberOfProducts() * (1 + numberRates_ + numberBumps_);
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < size; ++i)
            values.add(0.0);

        resetToZeros(means, size);
        resetToZeros(errors, size);
        List<Double> sums = new ArrayList<>();
        List<Double> sumsqs = new ArrayList<>();
        for (int i = 0; i < size; ++i) {
            sums.add(0.0);
            sumsqs.add(0.0);
        }

        for (int i = 0; i < numberOfPaths; ++i) {
            /* double weight = */ singlePathValues(values);
            // stats.add(values,weight);
            for (int j = 0; j < values.size(); ++j) {
                sums.set(j, sums.get(j) + values.get(j));
                sumsqs.set(j, sumsqs.get(j) + values.get(j) * values.get(j));
            }
        }

        for (int j = 0; j < values.size(); ++j) {
            means.set(j, sums.get(j) / numberOfPaths);
            double meanSq = sumsqs.get(j) / numberOfPaths;
            double variance = meanSq - means.get(j) * means.get(j);
            errors.set(j, Math.sqrt(variance / numberOfPaths));
        }
    }

    private static void resetToZeros(List<Double> target, int size) {
        // Official means.resize(size) leaves existing entries in place and
        // zero-fills any new ones, but all entries are overwritten below.
        target.clear();
        for (int i = 0; i < size; ++i)
            target.add(0.0);
    }
}
