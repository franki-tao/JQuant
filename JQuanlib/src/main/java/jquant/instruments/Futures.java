package jquant.instruments;

/**
 * Exchange futures conventions. IMM is the third Wednesday, ASX the second
 * Friday, and Custom leaves the dates to the caller.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/futures.hpp
 */
public final class Futures {
    public enum Type {
        IMM,
        ASX,
        Custom
    }

    private Futures() {
    }
}
