package jquant.methods.montecarlo;

/**
 * Base class for single-path pricers.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/pathpricer.hpp}.
 * Returns the value of an option on a given path.
 */
public interface PathPricer {
    double price(Path path);
}
