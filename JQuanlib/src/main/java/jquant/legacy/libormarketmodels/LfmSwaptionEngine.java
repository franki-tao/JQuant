package jquant.legacy.libormarketmodels;

import jquant.Handle;
import jquant.PricingEngine;
import jquant.Option;
import jquant.instruments.FixedVsFloatingSwap;
import jquant.instruments.Settlement;
import jquant.instruments.Swap;
import jquant.instruments.Swaption;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityMatrix;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Swaption engine using the Rebonato approximation built into
 * {@link LiborForwardModel}.
 *
 * <p>Official QuantLib
 * {@code ql/legacy/libormarketmodels/lfmswaptionengine.{hpp,cpp}} port.
 */
public class LfmSwaptionEngine extends Swaption.engine {

    private final LiborForwardModel model_;
    private final Handle<YieldTermStructure> discountCurve_;

    public LfmSwaptionEngine(final LiborForwardModel model,
                             Handle<YieldTermStructure> discountCurve) {
        model_ = model;
        discountCurve_ = discountCurve;
        registerWith(model_);
        registerWith(discountCurve_.get());
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with Lfm engine");

        final double basisPoint = 1.0e-4;

        FixedVsFloatingSwap swap = arguments_.swap;
        PricingEngine previousEngine = (swap instanceof jquant.Instrument inst) ? inst.engine() : null;
        DiscountingSwapEngine swapEngine = new DiscountingSwapEngine(discountCurve_);
        swap.setPricingEngine(swapEngine);
        if (previousEngine instanceof jquant.patterns.Observer previous
                && previousEngine != swapEngine)
            previous.unregisterWithAll();

        double correction = swap.spread()
                * Math.abs(swap.floatingLegBPS() / swap.fixedLegBPS());
        double fixedRate = swap.fixedRate() - correction;
        double fairRate = swap.fairRate() - correction;

        SwaptionVolatilityMatrix volatility =
                model_.getSwaptionVolatilityMatrix();

        Date referenceDate = volatility.referenceDate();
        DayCounter dayCounter = volatility.dayCounter();

        double exercise = dayCounter.yearFraction(referenceDate,
                arguments_.exercise.date(0));
        List<Date> fixedPayDates = arguments_.fixedPayDates;
        List<Date> fixedResetDates = arguments_.fixedResetDates;
        double swapLength =
                dayCounter.yearFraction(referenceDate,
                        fixedPayDates.get(fixedPayDates.size() - 1))
                        - dayCounter.yearFraction(referenceDate,
                        fixedResetDates.get(0));

        Option.Type w = arguments_.swap.swapType() == Swap.Type.Payer
                ? Option.Type.Call : Option.Type.Put;
        double vol = volatility.volatility(exercise, swapLength, fairRate, true);
        results_.value = (swap.fixedLegBPS() / basisPoint)
                * BlackFormula.blackFormula(w, fixedRate, fairRate,
                vol * Math.sqrt(exercise), 1.0, 0.0);
    }
}
