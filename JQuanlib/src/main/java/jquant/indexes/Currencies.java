package jquant.indexes;

import jquant.Currency;
import jquant.math.ClosestRounding;
import jquant.math.Rounding;

import java.util.Set;

/**
 * Currency definitions used by the indexes in this package, mirroring
 * QuantLib's {@code ql/currencies/america.hpp}.
 */
public final class Currencies {
    private Currencies() {
    }

    public static Currency usd() {
        return new Currency("U.S. dollar", "USD", 840, "$", "¢", 100,
                new Rounding(), new Currency(), Set.of());
    }

    // https://github.com/lballabio/QuantLib/blob/master/ql/currencies/oceania.cpp
    public static Currency aud() {
        return new Currency("Australian dollar", "AUD", 36, "A$", "", 100,
                new Rounding(), new Currency(), Set.of());
    }

    // https://github.com/lballabio/QuantLib/blob/master/ql/currencies/oceania.cpp
    public static Currency nzd() {
        return new Currency("New Zealand dollar", "NZD", 554, "NZ$", "", 100,
                new Rounding(), new Currency(), Set.of());
    }

    // https://github.com/lballabio/QuantLib/blob/master/ql/currencies/europe.cpp
    public static Currency eur() {
        return new Currency("European Euro", "EUR", 978, "", "", 100,
                new ClosestRounding(2, 5), new Currency(), Set.of());
    }
}
