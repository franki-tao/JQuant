package jquant.termstructures.volatility;

/**
 * ZABR pricing kernels.
 *
 * <p>Java counterpart of the evaluation tags in QuantLib
 * {@code ql/termstructures/volatility/zabrsmilesection.hpp}:
 * {@code ZabrShortMaturityLognormal}, {@code ZabrShortMaturityNormal},
 * {@code ZabrLocalVolatility} and {@code ZabrFullFd}. The official code
 * selects the kernel through a template parameter; the port keeps the same
 * names on an enum so that every kernel keeps its official financial
 * semantics.
 */
public enum ZabrEvaluation {
    /** Short-maturity lognormal approximation (official default). */
    ZabrShortMaturityLognormal,
    /** Short-maturity normal approximation implied from the local-vol prices. */
    ZabrShortMaturityNormal,
    /** Local-volatility finite-difference prices, cubic-interpolated in strike. */
    ZabrLocalVolatility,
    /** Full two-dimensional finite-difference prices per strike. */
    ZabrFullFd
}
