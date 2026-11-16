package jquant.methods.montecarlo;

/**
 * Weighted path sample.
 *
 * <p>Java counterpart of QuantLib {@code Sample<Path>} from
 * {@code ql/methods/montecarlo/sample.hpp}.
 */
public class SamplePath {
    public Path value;
    public double weight;

    public SamplePath(Path value, double weight) {
        this.value = value;
        this.weight = weight;
    }
}
