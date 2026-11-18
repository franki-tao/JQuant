package jquant;

/**
 * Averaging algorithm enumeration.
 *
 * <p>Ported from QuantLib {@code ql/instruments/averagetype.hpp}.
 */
public final class Average {
    private Average() {
    }

    public enum Type {
        Arithmetic,
        Geometric
    }

    public static String toString(Average.Type type) {
        switch (type) {
            case Arithmetic:
                return "Arithmetic";
            case Geometric:
                return "Geometric";
            default:
                throw new IllegalArgumentException("unknown Average::Type");
        }
    }
}
