package jquant.math.statistics;

public class DoublingConvergenceSteps {
    public int initialSamples() {
        return 1;
    }

    public int nextSamples(int current) {
        return 2 * current + 1;
    }
}
