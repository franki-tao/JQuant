package jquant.cashflows;

import jquant.Option;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.Date;
import jquant.time.Period;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;
import static jquant.pricingengines.BlackFormula.bachelierBlackFormula;
import static jquant.pricingengines.BlackFormula.blackFormula;

/**
 * Vanilla option pricer backed by a swaption smile section.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp/.cpp}
 * ({@code MarketQuotedOptionPricer}). Java {@link Date} is mutable, so the
 * stored expiry date is copied.
 */
public class MarketQuotedOptionPricer implements VanillaOptionPricer {

    private final double forwardValue_;
    private final Date expiryDate_;
    private final Period swapTenor_;
    private final SwaptionVolatilityStructure volatilityStructure_;
    private final SmileSection smile_;

    public MarketQuotedOptionPricer(double forwardValue,
                                    Date expiryDate,
                                    Period swapTenor,
                                    SwaptionVolatilityStructure volatilityStructure) {
        forwardValue_ = forwardValue;
        expiryDate_ = expiryDate == null ? new Date() : expiryDate.copy();
        swapTenor_ = swapTenor;
        volatilityStructure_ = volatilityStructure;
        smile_ = volatilityStructure.smileSection(expiryDate_, swapTenor_);
        QL_REQUIRE(volatilityStructure.volatilityType() == VolatilityType.Normal
                        || (volatilityStructure.volatilityType()
                        == VolatilityType.ShiftedLognormal
                        && close(volatilityStructure.shift(expiryDate_,
                        swapTenor_), 0.0)),
                "VanillaOptionPricer: a normal or a zero-shift lognormal "
                        + "volatility is required");
    }

    @Override
    public double price(double strike, Option.Type optionType, double deflator) {
        double variance = smile_.variance(strike);
        if (volatilityStructure_.volatilityType()
                == VolatilityType.ShiftedLognormal) {
            return deflator * blackFormula(optionType, strike, forwardValue_,
                    Math.sqrt(variance), 1.0, 0.0);
        }
        return deflator * bachelierBlackFormula(optionType, strike,
                forwardValue_, Math.sqrt(variance), 1.0);
    }
}
