package jquant.termstructures.yield;

import jquant.math.Array;

/**
 * A curve bootstrapper that can take part in a joint multi-curve bootstrap.
 *
 * <p>Port of QuantLib's {@code MultiCurveBootstrapContributor} in
 * {@code ql/termstructures/globalbootstrap.hpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/globalbootstrap.hpp
 */
public interface MultiCurveBootstrapContributor {
    /** Called by {@link MultiCurveBootstrap#add} to store the owning parent. */
    void setParentBootstrapper(MultiCurveBootstrap b);

    /** Initialize the curve and return the initial guess for its variables. */
    Array setupCostFunction();

    /** Write the given curve variables back into the curve. */
    void setCostFunctionArgument(Array v);

    /** Evaluate the curve's error terms at the current variables. */
    Array evaluateCostFunction();

    /** Mark the curve as fully bootstrapped. */
    void setToValid();
}
