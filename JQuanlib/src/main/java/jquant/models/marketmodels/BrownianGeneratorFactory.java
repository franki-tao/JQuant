package jquant.models.marketmodels;

public abstract class BrownianGeneratorFactory {
    public abstract BrownianGenerator create(int factors, int steps);
}
