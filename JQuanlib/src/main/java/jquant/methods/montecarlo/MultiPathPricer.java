package jquant.methods.montecarlo;

/**
 * Base class for multi-path pricers.
 *
 * <p>Java counterpart of QuantLib {@code PathPricer<MultiPath>}.
 */
public interface MultiPathPricer {
    double price(MultiPath path);
}
