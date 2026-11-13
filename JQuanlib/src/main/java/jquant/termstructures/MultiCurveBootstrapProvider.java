package jquant.termstructures;

import jquant.termstructures.yield.MultiCurveBootstrapContributor;

/**
 * A curve that can provide a bootstrapper able to participate in a
 * {@link MultiCurve} joint bootstrap.
 *
 * <p>Port of QuantLib's {@code MultiCurveBootstrapProvider} in
 * {@code ql/termstructures/multicurve.hpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/multicurve.hpp
 */
public interface MultiCurveBootstrapProvider {
    /**
     * Returns the contributor used by {@code MultiCurve}, or {@code null} if
     * this curve does not use a compatible bootstrapper.
     */
    MultiCurveBootstrapContributor multiCurveBootstrapContributor();
}
