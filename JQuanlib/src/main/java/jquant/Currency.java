package jquant;

import jquant.math.Rounding;

import java.util.Objects;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! %Currency specification
public class Currency {
    protected final class Data {
        public String name, code;
        public int numeric;
        public String symbol, fractionSymbol;
        public int fractionsPerUnit;
        public Rounding rounding;
        public Currency triangulated;
        public Set<String> minorUnitCodes;

        // triangulationCurrency = Currency()/ minorUnitCodes = {}
        public Data(String name, String code, int numericCode, String symbol,
                    String fractionSymbol, int fractionsPerUnit, final Rounding rounding,
                    Currency triangulationCurrency, Set<String> minorUnitCodes) {
            this.name = name;
            this.code = code;
            this.numeric = numericCode;
            this.symbol = symbol;
            this.fractionSymbol = fractionSymbol;
            this.fractionsPerUnit = fractionsPerUnit;
            this.rounding = rounding;
            this.triangulated = triangulationCurrency;
            this.minorUnitCodes = minorUnitCodes;
        }
    }
    protected Data data_;
    //! \name Constructors
    //@{
    //! default constructor
    /*! Instances built via this constructor have undefined
        behavior. Such instances can only act as placeholders
        and must be reassigned to a valid currency before being
        used.
    */
    public Currency() {}

    // triangulationCurrency = Currency()/ minorUnitCodes = {}
    public Currency(final String name,
                    final String code,
                    int numericCode,
                    final String symbol,
                    final String fractionSymbol,
                    int fractionsPerUnit,
                    final Rounding rounding,
                    final Currency triangulationCurrency,
                    final Set<String> minorUnitCodes) {
        data_ = new Data(name,
                code,
                numericCode,
                symbol,
                fractionSymbol,
                fractionsPerUnit,
                rounding,
                triangulationCurrency,
                minorUnitCodes);
    }
    //! currency name, e.g, "U.S. Dollar"
    public final String name() {
        checkNonEmpty();
        return data_.name;
    }

    //! ISO 4217 three-letter code, e.g, "USD"
    public final String code() {
        checkNonEmpty();
        return data_.code;
    }

    //! ISO 4217 numeric code, e.g, "840"
    public int numericCode() {
        checkNonEmpty();
        return data_.numeric;
    }

    //! symbol, e.g, "$"
    public final String symbol() {
        checkNonEmpty();
        return data_.symbol;
    }

    //! fraction symbol, e.g, "¢"
    public final String fractionSymbol() {
        checkNonEmpty();
        return data_.fractionSymbol;
    }

    //! number of fractionary parts in a unit, e.g, 100
    public int fractionsPerUnit() {
        checkNonEmpty();
        return data_.fractionsPerUnit;
    }

    //! rounding convention
    public final Rounding rounding() {
        checkNonEmpty();
        return data_.rounding;
    }

    //! is this a usable instance?
    public boolean empty() {
        return data_ != null;
    }

    //! currency used for triangulated exchange when required
    public final Currency triangulationCurrency() {
        checkNonEmpty();
        return data_.triangulated;
    }
    //! minor unit codes, e.g. GBp, GBX for GBP
    public final Set<String> minorUnitCodes() {
        checkNonEmpty();
        return data_.minorUnitCodes;
    }

    public boolean equals(final Currency c) {
        return (this.empty() && c.empty()) ||
                (!this.empty() && !c.empty() && Objects.equals(this.name(), c.name()));
    }

    private void checkNonEmpty() {
        QL_REQUIRE(data_==null, "no currency data provided");
    }

}
