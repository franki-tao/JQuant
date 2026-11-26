package jquant.models.marketmodels;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/constrainedevolver.hpp} port.
 *
 * <p>Abstract base class requiring the extra methods above
 * {@link MarketModelEvolver} that let rates be fixed via importance
 * sampling (Fries-Joshi proxy simulation).
 */
public abstract class ConstrainedEvolver extends MarketModelEvolver {

    /** Call once. */
    public abstract void setConstraintType(
            List<Integer> startIndexOfSwapRate,
            List<Integer> endIndexOfSwapRate);

    /** Call before each path. */
    public abstract void setThisConstraint(
            List<Double> rateConstraints,
            boolean[] isConstraintActive);
}
