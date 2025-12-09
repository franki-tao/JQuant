package jquant.math.statistics.impl;

import java.util.List;

public abstract class Stat {
    public abstract int samples();
    public abstract double mean();
    public abstract double standardDeviation();
    public abstract double variance();
    public abstract double errorEstimate();
    public abstract double skewness();
    public abstract double kurtosis();
    public abstract double min();
    public abstract double max();
    public abstract void add(double value, double weight);
    public abstract void addSequence(List<Double> arr);
    public abstract void addSequence(List<Double> values, List<Double> weights);
}
