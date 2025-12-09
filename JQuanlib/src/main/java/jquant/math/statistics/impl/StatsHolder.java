package jquant.math.statistics.impl;

//! Helper class for precomputed distributions
public class StatsHolder {
    private double mean_, standardDeviation_;
    public StatsHolder(double mean, double standardDeviation) {
        mean_ = mean;
        standardDeviation_ = standardDeviation;
    }
    public double mean() {
        return mean_;
    }
    public double standardDeviation() {
        return standardDeviation_;
    }
}
