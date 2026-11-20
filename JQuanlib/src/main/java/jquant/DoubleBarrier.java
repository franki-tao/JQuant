package jquant;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Double barrier type.
 *
 * <p>Ported from QuantLib {@code ql/instruments/doublebarriertype.{hpp,cpp}}.
 */
public class DoubleBarrier {

    /** Placeholder for enumerated double-barrier types. */
    public enum Type {
        KnockIn,
        KnockOut,
        /** lower barrier KI, upper KO. */
        KIKO,
        /** lower barrier KO, upper KI. */
        KOKI
    }

    /**
     * Official {@code operator<<} strings: {@code KnockIn}, {@code KnockOut},
     * {@code KI lo+KO up}, {@code KO lo+KI up}.
     */
    public static String toString(Type type) {
        if (type == null) {
            QL_FAIL("unknown DoubleBarrier::Type");
            return null;
        }
        switch (type) {
            case KnockIn:
                return "KnockIn";
            case KnockOut:
                return "KnockOut";
            case KIKO:
                return "KI lo+KO up";
            case KOKI:
                return "KO lo+KI up";
            default:
                QL_FAIL("unknown DoubleBarrier::Type (" + type.ordinal() + ")");
                return null;
        }
    }
}
