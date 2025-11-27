package jquant.math.optimization;

//! Differential Evolution configuration object
/*! The algorithm and strategy names are taken from here:

    Price, K., Storn, R., 1997. Differential Evolution -
    A Simple and Efficient Heuristic for Global Optimization
    over Continuous Spaces.
    Journal of Global Optimization, Kluwer Academic Publishers,
    1997, Vol. 11, pp. 341 - 359.

    There are seven basic strategies for creating mutant population
    currently implemented. Three basic crossover types are also
    available.

    Future development:
    1) base element type to be extracted
    2) L differences to be used instead of fixed number
    3) various weights distributions for the differences (dither etc.)
    4) printFullInfo parameter usage to track the algorithm

    \warning This was reported to fail tests on Mac OS X 10.8.4.
*/


import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.ReferencePkg;
import jquant.math.optimization.impl.Candidate;
import jquant.math.optimization.impl.Configuration;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.matrixutilities.MatrixUtil.randomize;

//! %OptimizationMethod using Differential Evolution algorithm
/*! \ingroup optimizers */
public class DifferentialEvolution extends OptimizationMethod {
    public enum Strategy {
        Rand1Standard,
        BestMemberWithJitter,
        CurrentToBest2Diffs,
        Rand1DiffWithPerVectorDither,
        Rand1DiffWithDither,
        EitherOrWithOptimalRecombination,
        Rand1SelfadaptiveWithRotation
    }

    ;

    public enum CrossoverType {
        Normal,
        Binomial,
        Exponential
    }

    ;
    private Configuration configuration_;
    private Array upperBound_, lowerBound_;
    private Array currGenSizeWeights_, currGenCrossover_;
    private Candidate bestMemberEver_;
    private MersenneTwisterUniformRng rng_;

    public DifferentialEvolution(final Configuration configuration) {
        this.configuration_ = configuration;
        rng_ = new MersenneTwisterUniformRng(configuration.seed);
        upperBound_ = new Array(0);
        lowerBound_ = new Array(0);
        currGenSizeWeights_ = new Array(0);
        currGenCrossover_ = new Array(0);
        bestMemberEver_ = new Candidate(1);
    }

    public final Configuration configuration() {
        return configuration_;
    }

    private void fillInitialPopulation(List<Candidate> population, final Problem p) {
        Candidate front = population.get(0);
        front.values = p.currentValue();
        front.cost = p.costFunction().value(front.values);
        population.set(0, front);
        for (int j = 1; j < population.size(); ++j) {
            for (int i = 0; i < p.currentValue().size(); ++i) {
                double l = lowerBound_.get(i), u = upperBound_.get(i);
                population.get(j).values.set(i, l + (u - l) * rng_.nextReal());
            }
            population.get(j).cost = p.costFunction().value(population.get(j).values);
            if (!Double.isFinite(population.get(j).cost))
                population.get(j).cost = QL_MAX_REAL;
        }
    }

    private void getCrossoverMask(List<Array> crossoverMask,
                                  List<Array> invCrossoverMask,
                                  final Array mutationProbabilities) {
        for (int cmIter = 0; cmIter < crossoverMask.size(); cmIter++) {
            for (int memIter = 0; memIter < crossoverMask.get(cmIter).size(); memIter++) {
                if (rng_.nextReal() < mutationProbabilities.get(cmIter)) {
                    invCrossoverMask.get(cmIter).set(memIter, 0.0);
                    // invCrossoverMask[cmIter][memIter] = 0.0;
                } else {
                    crossoverMask.get(cmIter).set(memIter, 0.0);
                    // crossoverMask[cmIter][memIter] = 0.0;
                }
            }
        }
    }

    private Array getMutationProbabilities(final List<Candidate> population) {
        Array mutationProbabilities = currGenCrossover_;
        switch (configuration().crossoverType) {
            case Normal:
                break;
            case Binomial:
                mutationProbabilities = currGenCrossover_
                        .mutiply(1.0 - 1.0 / population.get(0).values.size())
                        .add(1.0 / population.get(0).values.size());
                break;
            case Exponential:
                for (int coIter = 0; coIter < currGenCrossover_.size(); coIter++) {
                    mutationProbabilities.set(coIter, (1.0 - Math.pow(currGenCrossover_.get(coIter),
                            (int) population.get(0).values.size()))
                            / (population.get(0).values.size()
                            * (1.0 - currGenCrossover_.get(coIter))));
                }
                break;
            default:
                QL_FAIL("Unknown crossover type ("
                        + (configuration().crossoverType) + ")");
                break;
        }
        return mutationProbabilities;
    }

    private void adaptSizeWeights() {
        // [=Fl & =Fu] respectively see Brest, J. et al., 2006,
        // "Self-Adapting Control Parameters in Differential
        // Evolution"
        double sizeWeightLowerBound = 0.1, sizeWeightUpperBound = 0.9;
        // [=tau1] A Comparative Study on Numerical Benchmark
        // Problems." page 649 for reference
        double sizeWeightChangeProb = 0.1;

        for (int i = 0; i < currGenSizeWeights_.size(); i++) {
            if (rng_.nextReal() < sizeWeightChangeProb)
                currGenSizeWeights_.set(i, sizeWeightLowerBound + rng_.nextReal() * sizeWeightUpperBound);
        }
    }

    private void adaptCrossover() {
        double crossoverChangeProb = 0.1; // [=tau2]
        for (int i = 0; i < currGenCrossover_.size(); i++) {
            if (rng_.nextReal() < crossoverChangeProb) {
                currGenCrossover_.set(i, rng_.nextReal());
            }
        }
    }

    private void calculateNextGeneration(List<Candidate> population,
                                         Problem p) {
        List<Candidate> mirrorPopulation = new ArrayList<>();
        List<Candidate> oldPopulation = CommonUtil.clone(population);

        switch (configuration().strategy) {

            case Rand1Standard: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop2 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                mirrorPopulation = shuffledPop1;

                for (int popIter = 0; popIter < population.size(); popIter++) {
                    population.get(popIter).values = population.get(popIter).values
                            .add((shuffledPop1.get(popIter).values.subtract(shuffledPop2.get(popIter).values)).mutiply(configuration().stepsizeWeight));
                }
            }
            break;

            case BestMemberWithJitter: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                Array jitter = new Array(population.get(0).values.size(), 0.0);

                for (int popIter = 0; popIter < population.size(); popIter++) {
                    for (int i = 0; i < jitter.size(); i++) {
                        jitter.set(i, rng_.nextReal());
                    }
                    population.get(popIter).values = bestMemberEver_.values
                            .add((shuffledPop1.get(popIter).values.subtract(population.get(popIter).values))
                                    .multiply((jitter.mutiply(0.0001).add(configuration().stepsizeWeight))));
                }
                mirrorPopulation = CommonUtil.ArrayInit(population.size(), bestMemberEver_);
            }
            break;

            case CurrentToBest2Diffs: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);

                for (int popIter = 0; popIter < population.size(); popIter++) {
                    population.get(popIter).values = oldPopulation.get(popIter).values
                            .add((bestMemberEver_.values.subtract(oldPopulation.get(popIter).values)).mutiply(configuration().stepsizeWeight))
                            .add((population.get(popIter).values.subtract(shuffledPop1.get(popIter).values)).mutiply(configuration().stepsizeWeight));
                }
                mirrorPopulation = shuffledPop1;
            }
            break;

            case Rand1DiffWithPerVectorDither: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop2 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                mirrorPopulation = shuffledPop1;
                Array FWeight = new Array(population.get(0).values.size(), 0.0);
                for (int i = 0; i < FWeight.size(); i++) {
                    FWeight.set(i, (1.0 - configuration().stepsizeWeight) * rng_.nextReal() +
                            configuration().stepsizeWeight);
                }
                for (int popIter = 0; popIter < population.size(); popIter++) {
                    population.get(popIter).values = population.get(popIter).values
                            .add(FWeight.multiply((shuffledPop1.get(popIter).values.subtract(shuffledPop2.get(popIter).values))));
                }
            }
            break;

            case Rand1DiffWithDither: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop2 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                mirrorPopulation = shuffledPop1;
                double FWeight = (1.0 - configuration().stepsizeWeight) * rng_.nextReal()
                        + configuration().stepsizeWeight;
                for (int popIter = 0; popIter < population.size(); popIter++) {
                    population.get(popIter).values = population.get(popIter).values
                            .add((shuffledPop1.get(popIter).values.subtract(shuffledPop2.get(popIter).values)).mutiply(FWeight));
                }
            }
            break;

            case EitherOrWithOptimalRecombination: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop2 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                mirrorPopulation = shuffledPop1;
                double probFWeight = 0.5;
                if (rng_.nextReal() < probFWeight) {
                    for (int popIter = 0; popIter < population.size(); popIter++) {
                        population.get(popIter).values = oldPopulation.get(popIter).values
                                .add((shuffledPop1.get(popIter).values
                                        .subtract(shuffledPop2.get(popIter).values)).mutiply(configuration().stepsizeWeight));
                    }
                } else {
                    double K = 0.5 * (configuration().stepsizeWeight + 1); // invariant with respect to probFWeight used
                    for (int popIter = 0; popIter < population.size(); popIter++) {
                        population.get(popIter).values = oldPopulation.get(popIter).values
                                .add((shuffledPop1.get(popIter).values.subtract(shuffledPop2.get(popIter).values)
                                        .subtract(population.get(popIter).values.mutiply(2.0))).mutiply(K));
                    }
                }
            }
            break;

            case Rand1SelfadaptiveWithRotation: {
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop1 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                List<Candidate> shuffledPop2 = CommonUtil.clone(population);
                randomize(population, 0, population.size(), rng_);
                mirrorPopulation = shuffledPop1;

                adaptSizeWeights();

                for (int popIter = 0; popIter < population.size(); popIter++) {
                    if (rng_.nextReal() < 0.1) {
                        population.get(popIter).values = rotateArray(bestMemberEver_.values);
                    } else {
                        population.get(popIter).values = bestMemberEver_.values
                                .add((shuffledPop1.get(popIter).values.subtract(shuffledPop2.get(popIter).values)).mutiply(currGenSizeWeights_.get(popIter)));
                    }
                }
            }
            break;

            default:
                QL_FAIL("Unknown strategy ("
                        + (configuration().strategy) + ")");
        }
        // in order to avoid unnecessary copying we use the same population object for mutants
        crossover(oldPopulation, population, population, mirrorPopulation, p);
    }

    private Array rotateArray(Array inputArray) {
        randomize(inputArray, 0, inputArray.size(), rng_);
        return inputArray;
    }

    private void crossover(final List<Candidate> oldPopulation,
                           List<Candidate> population,
                           final List<Candidate> mutantPopulation,
                           final List<Candidate> mirrorPopulation,
                           Problem p) {
        if (configuration().crossoverIsAdaptive) {
            adaptCrossover();
        }

        Array mutationProbabilities = getMutationProbabilities(population);

        List<Array> crossoverMask = CommonUtil.ArrayInit(population.size(),
                new Array(population.get(0).values.size(), 1.0));
        List<Array> invCrossoverMask = CommonUtil.clone(crossoverMask);
        getCrossoverMask(crossoverMask, invCrossoverMask, mutationProbabilities);

        // crossover of the old and mutant population
        for (int popIter = 0; popIter < population.size(); popIter++) {
            population.get(popIter).values = (oldPopulation.get(popIter).values.multiply(invCrossoverMask.get(popIter)))
                    .add((mutantPopulation.get(popIter).values.multiply(crossoverMask.get(popIter))));
            // immediately apply bounds if specified
            if (configuration().applyBounds) {
                for (int memIter = 0; memIter < population.get(popIter).values.size(); memIter++) {
                    if (population.get(popIter).values.get(memIter) > upperBound_.get(memIter))
                        population.get(popIter).values.set(memIter, upperBound_.get(memIter)
                                + rng_.nextReal()
                                * (mirrorPopulation.get(popIter).values.get(memIter)
                                - upperBound_.get(memIter)));
                    if (population.get(popIter).values.get(memIter) < lowerBound_.get(memIter))
                        population.get(popIter).values.set(memIter, lowerBound_.get(memIter)
                                + rng_.nextReal()
                                * (mirrorPopulation.get(popIter).values.get(memIter)
                                - lowerBound_.get(memIter)));
                }
            }
            // evaluate objective function as soon as possible to avoid unnecessary loops
            try {
                population.get(popIter).cost = p.value(population.get(popIter).values);
            } catch (Exception e) {
                population.get(popIter).cost = QL_MAX_REAL;
            }
            if (!Double.isFinite(population.get(popIter).cost))
                population.get(popIter).cost = QL_MAX_REAL;

        }
    }

    @Override
    public EndCriteria.Type minimize(Problem p, EndCriteria endCriteria) {
        EndCriteria.Type ecType = EndCriteria.Type.None;
        p.reset();

        if (configuration().upperBound.empty()) {
            upperBound_ = p.constraint().upperBound(p.currentValue());
        } else {
            QL_REQUIRE(configuration().upperBound.size() == p.currentValue().size(),
                    "wrong upper bound size in differential evolution configuration");
            upperBound_ = configuration().upperBound;
        }
        if (configuration().lowerBound.empty()) {
            lowerBound_ = p.constraint().lowerBound(p.currentValue());
        } else {
            QL_REQUIRE(configuration().lowerBound.size() == p.currentValue().size(),
                    "wrong lower bound size in differential evolution configuration");
            lowerBound_ = configuration().lowerBound;
        }
        currGenSizeWeights_ =
                new Array(configuration().populationMembers, configuration().stepsizeWeight);
        currGenCrossover_ = new Array(configuration().populationMembers,
                configuration().crossoverProbability);

        List<Candidate> population = new ArrayList<>();
        if (!configuration().initialPopulation.isEmpty()) {
            population = CommonUtil.ArrayInit(configuration().initialPopulation.size(), new Candidate(1));
            for (int i = 0; i < population.size(); ++i) {
                population.get(i).values = configuration().initialPopulation.get(i);
                QL_REQUIRE(population.get(i).values.size() == p.currentValue().size(),
                        "wrong values size in initial population");
                population.get(i).cost = p.costFunction().value(population.get(i).values);
            }
        } else {
            population = CommonUtil.ArrayInit(configuration().populationMembers,
                    new Candidate(p.currentValue().size()));
            fillInitialPopulation(population, p);
        }
        partialSort(population, 1);
        // std::partial_sort (population.begin(), population.begin() + 1, population.end(), sort_by_cost());
        bestMemberEver_ = population.get(0);
        double fxOld = population.get(0).cost;
        int iteration = 0, stationaryPointIteration = 0;

        // main loop - calculate consecutive emerging populations
        while (!endCriteria.checkMaxIterations(iteration++, ecType)) {
            calculateNextGeneration(population, p);
            partialSort(population, 1);
            // std::partial_sort (population.begin(), population.begin() + 1, population.end(), sort_by_cost());
            if (population.get(0).cost < bestMemberEver_.cost)
                bestMemberEver_ = population.get(0);
            double fxNew = population.get(0).cost;
            // 装箱
            ReferencePkg<Integer> spi = new ReferencePkg<>(stationaryPointIteration);
            if (endCriteria.checkStationaryFunctionValue(fxOld, fxNew, spi,
                    ecType)) {
                stationaryPointIteration = spi.getT();
                break;
            }
            stationaryPointIteration = spi.getT();
            fxOld = fxNew;
        }
        p.setCurrentValue(bestMemberEver_.values);
        p.setFunctionValue(bestMemberEver_.cost);
        return ecType;
    }

    private void partialSort(List<Candidate> population, int k) {
        if (population == null || population.size() <= 1 || k <= 0) {
            return;
        }
        k = Math.min(k, population.size());

        PriorityQueue<Candidate> maxHeap = new PriorityQueue<>(
                k,
                Comparator.comparingDouble((Candidate c) -> c.cost).reversed() // 降序排序（大顶堆）
        );

        for (Candidate candidate : population) {
            if (maxHeap.size() < k) {
                maxHeap.offer(candidate);
            } else {
                if (candidate.cost < maxHeap.peek().cost) {
                    maxHeap.poll();
                    maxHeap.offer(candidate);
                }
            }
        }
        List<Candidate> topK = new ArrayList<>(maxHeap);
        topK.sort(Comparator.comparingDouble(c -> c.cost)); // 按 cost 升序

        for (int i = 0; i < k; i++) {
            population.set(i, topK.get(i));
        }
    }
}
