package jquant.models.marketmodels;

import java.util.List;

public abstract class BrownianGenerator {
    public abstract double nextStep(List<Double> arr);
    public abstract double nextPath();

    public abstract int numberOfFactors();
    public abstract int numberOfSteps();
}
