package jquant.methods.montecarlo;

/**
 * Weighted multi-path sample.
 *
 * <p>Java counterpart of QuantLib {@code Sample<MultiPath>} from
 * {@code ql/methods/montecarlo/sample.hpp}.
 */
public class SampleMultiPath {
    public MultiPath value;
    public double weight;

    public SampleMultiPath(MultiPath value, double weight) {
        this.value = value;
        this.weight = weight;
    }
}
