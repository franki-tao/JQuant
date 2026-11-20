package jquant;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Barrier type.
 *
 * <p>Ported from QuantLib {@code ql/instruments/barriertype.{hpp,cpp}}.
 */
public class Barrier {

    /** Placeholder for enumerated barrier types. */
    public enum Type {
        DownIn,
        UpIn,
        DownOut,
        UpOut
    }

    /**
     * Official {@code operator<<} strings: {@code Down&In}, {@code Up&In},
     * {@code Down&Out}, {@code Up&Out}.
     */
    public static String toString(Type type) {
        if (type == null) {
            QL_FAIL("unknown Barrier::Type");
            return null;
        }
        switch (type) {
            case DownIn:
                return "Down&In";
            case UpIn:
                return "Up&In";
            case DownOut:
                return "Down&Out";
            case UpOut:
                return "Up&Out";
            default:
                QL_FAIL("unknown Barrier::Type (" + type.ordinal() + ")");
                return null;
        }
    }
}
