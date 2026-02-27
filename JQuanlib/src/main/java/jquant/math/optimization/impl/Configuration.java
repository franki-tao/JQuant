package jquant.math.optimization.impl;

import jquant.math.Array;
import jquant.math.optimization.DifferentialEvolution;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class Configuration {
    public DifferentialEvolution.Strategy strategy = DifferentialEvolution.Strategy.BestMemberWithJitter;
    public DifferentialEvolution.CrossoverType crossoverType = DifferentialEvolution.CrossoverType.Normal;
    public int populationMembers = 100;
    public double stepsizeWeight = 0.2, crossoverProbability = 0.9;
    public long seed = 0;
    public boolean applyBounds = true, crossoverIsAdaptive = false;
    public List<Array> initialPopulation;
    public Array upperBound, lowerBound;

    // Clang seems to have problems if we use '= default' here.
    // NOLINTNEXTLINE(modernize-use-equals-default)
    public Configuration() {
        initialPopulation = new ArrayList<>();
        upperBound = new Array(0);
        lowerBound = new Array(0);
    }

    public Configuration withBounds(boolean b) {
        applyBounds = b;
        return this;
    }

    public Configuration withCrossoverProbability(double p) {
        QL_REQUIRE(p >= 0.0 && p <= 1.0,
                "Crossover probability (" + p
                        + ") must be in [0,1] range");
        crossoverProbability = p;
        return this;
    }

    public Configuration withPopulationMembers(int n) {
        QL_REQUIRE(n>0, "Positive number of population members required");
        populationMembers = n;
        initialPopulation.clear();
        return this;
    }

    public Configuration withInitialPopulation(final List<Array> c) {
        initialPopulation = c;
        populationMembers = c.size();
        return this;
    }

    public Configuration withUpperBound(final Array u) {
        upperBound = u;
        return this;
    }

    public Configuration withLowerBound(final Array l) {
        lowerBound = l;
        return this;
    }

    public Configuration withSeed(long s) {
        seed = s;
        return this;
    }

    public Configuration withAdaptiveCrossover(boolean b) {
        crossoverIsAdaptive = b;
        return this;
    }

    public Configuration withStepsizeWeight(double w) {
        QL_REQUIRE(w>=0 && w<=2.0,
                "Step size weight ("+ w
                        + ") must be in [0,2] range");
        stepsizeWeight = w;
        return this;
    }

    public Configuration withCrossoverType(DifferentialEvolution.CrossoverType t) {
        crossoverType = t;
        return this;
    }

    public Configuration withStrategy(DifferentialEvolution.Strategy s) {
        strategy = s;
        return this;
    }
}
