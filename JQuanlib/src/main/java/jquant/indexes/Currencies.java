package jquant.indexes;

import jquant.Currency;
import jquant.math.Rounding;

import java.util.Set;

/**
 * Currency definitions used by the indexes in this package, mirroring
 * QuantLib's {@code ql/currencies/america.hpp}.
 */
final class Currencies {
    private Currencies() {
    }

    static Currency usd() {
        return new Currency("U.S. dollar", "USD", 840, "$", "¢", 100,
                new Rounding(), new Currency(), Set.of());
    }
}
