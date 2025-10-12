package jquant.methods.montecarlo;

import java.util.List;

public class SampleVector {
    public List<Double> value;
    public double weight;

    public SampleVector(List<Double> value, double weight) {
        this.value = value;
        this.weight = weight;
    }
}
