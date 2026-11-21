package jquant.instruments;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Settlement information for swaptions.
 *
 * <p>Ported from QuantLib {@code ql/instruments/swaption.hpp}.
 */
public final class Settlement {
    private Settlement() {
    }

    public enum Type {
        Physical,
        Cash
    }

    public enum Method {
        PhysicalOTC,
        PhysicalCleared,
        CollateralizedCashPrice,
        ParYieldCurve
    }

    /** check consistency of settlement type and method */
    public static void checkTypeAndMethodConsistency(Type settlementType,
                                                     Method settlementMethod) {
        if (settlementType == Type.Physical) {
            QL_REQUIRE(settlementMethod == Method.PhysicalOTC
                            || settlementMethod == Method.PhysicalCleared,
                    "invalid settlement method for physical settlement");
        }
        if (settlementType == Type.Cash) {
            QL_REQUIRE(settlementMethod == Method.CollateralizedCashPrice
                            || settlementMethod == Method.ParYieldCurve,
                    "invalid settlement method for cash settlement");
        }
    }
}
