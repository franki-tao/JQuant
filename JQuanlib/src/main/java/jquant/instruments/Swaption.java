package jquant.instruments;

import jquant.CashFlow;
import jquant.Exercise;
import jquant.Handle;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.Settings;
import jquant.math.Function;
import jquant.math.solvers1d.NewtonSafe;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.pricingengines.GenericEngine;
import jquant.pricingengines.swaption.BachelierSwaptionEngine;
import jquant.pricingengines.swaption.BlackSwaptionEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.Date;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual365Fixed;

import java.util.HashMap;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.Normal;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Swaption on a fixed-vs-floating swap.
 *
 * <p>Ported from QuantLib {@code ql/instruments/swaption.{hpp,cpp}}.
 * In Java the underlying is exposed through {@link FixedVsFloatingSwap}
 * rather than through a {@code FixedVsFloatingSwap} base class, because the
 * existing {@link VanillaSwap} extends {@code Instrument} directly. The
 * public behaviour (arguments, valuation, implied volatility, exercise
 * handling) follows the official class.
 */
public class Swaption extends Instrument {
    public enum PriceType {Spot, Forward}

    public static class arguments extends DiscountingSwapEngine.arguments {
        public FixedVsFloatingSwap swap;
        public Settlement.Type settlementType = Settlement.Type.Physical;
        public Settlement.Method settlementMethod;
        public Exercise exercise;

        @Override
        public void validate() {
            super.validate();
            QL_REQUIRE(swap != null, "swap not set");
            QL_REQUIRE(exercise != null, "exercise not set");
            Settlement.checkTypeAndMethodConsistency(settlementType, settlementMethod);
        }
    }

    public static class results extends Instrument.results {
    }

    /** base class for swaption engines */
    public abstract static class engine
            extends GenericEngine<Swaption.arguments, Swaption.results> {
        protected engine() {
            super(new Swaption.arguments(), new Swaption.results());
            results_.additionalResults = new HashMap<>();
        }
    }

    private final FixedVsFloatingSwap swap_;
    private final Exercise exercise_;
    private final Settlement.Type settlementType_;
    private final Settlement.Method settlementMethod_;

    public Swaption(FixedVsFloatingSwap swap,
                    Exercise exercise,
                    Settlement.Type delivery,
                    Settlement.Method settlementMethod) {
        swap_ = swap;
        settlementType_ = delivery;
        settlementMethod_ = settlementMethod;
        exercise_ = exercise;
        registerWith(swap_);
        // When we ask for the NPV of an expired swaption, the swap is not
        // recalculated and thus wouldn't forward later notifications according
        // to the default LazyObject behaviour. Override it as in QuantLib.
        swap_.alwaysForwardNotifications();
    }

    public Settlement.Type settlementType() {
        return settlementType_;
    }

    public Settlement.Method settlementMethod() {
        return settlementMethod_;
    }

    public Swap.Type type() {
        return swap_.swapType();
    }

    public FixedVsFloatingSwap underlying() {
        return swap_;
    }

    /** The exercise (official stores it in {@code Option}); exposed here. */
    public Exercise exercise() {
        return exercise_;
    }

    public void deepUpdate() {
        swap_.deepUpdate();
        update();
    }

    @Override
    public boolean isExpired() {
        Date exerciseDate = exercise_.dates().get(exercise_.dates().size() - 1);
        return CashFlow.hasOccurred(exerciseDate,
                Settings.instance.evaluationDate().Date(), Optional.empty());
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        swap_.setupArguments(args);
        QL_REQUIRE(args instanceof Swaption.arguments, "wrong argument type");
        Swaption.arguments arguments = (Swaption.arguments) args;
        arguments.swap = swap_;
        arguments.settlementType = settlementType_;
        arguments.settlementMethod = settlementMethod_;
        arguments.exercise = exercise_;
    }

    /**
     * Implied volatility.
     *
     * <p>Official signature, including {@link PriceType}. For
     * {@code Forward} the target forward price is converted to a spot price
     * by multiplying by the discount factor to the exercise date.
     */
    public double impliedVolatility(double price,
                                    Handle<YieldTermStructure> discountCurve,
                                    double guess,
                                    double accuracy,
                                    int maxEvaluations,
                                    double minVol,
                                    double maxVol,
                                    VolatilityType type,
                                    double displacement,
                                    PriceType priceType) {
        QL_REQUIRE(!isExpired(), "instrument expired");
        QL_REQUIRE(exercise_.type() == Exercise.Type.European, "not a European option");

        if (priceType == PriceType.Forward) {
            // convert to spot
            price *= discountCurve.currentLink().discount(exercise_.date(0));
        }

        ImpliedSwaptionVolHelper f = new ImpliedSwaptionVolHelper(
                this, discountCurve, price, displacement, type);
        NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, guess, minVol, maxVol);
    }

    private static final class ImpliedSwaptionVolHelper implements Function {
        private final Handle<YieldTermStructure> discountCurve_;
        private final double targetValue_;
        private final SimpleQuote vol_;
        private final PricingEngine engine_;
        private final Instrument.results results_;

        ImpliedSwaptionVolHelper(Swaption swaption,
                                 Handle<YieldTermStructure> discountCurve,
                                 double targetValue,
                                 double displacement,
                                 VolatilityType type) {
            discountCurve_ = discountCurve;
            targetValue_ = targetValue;
            vol_ = new SimpleQuote(-1.0);

            // vol_ is set an implausible value, so that calculation is forced
            // at first ImpliedSwaptionVolHelper.value(x) call
            Handle<Quote> h = new Handle<>(vol_, true);

            switch (type) {
                case ShiftedLognormal:
                    engine_ = new BlackSwaptionEngine(discountCurve_, h,
                            new Actual365Fixed(), displacement);
                    break;
                case Normal:
                    engine_ = new BachelierSwaptionEngine(discountCurve_, h,
                            new Actual365Fixed());
                    break;
                default:
                    QL_FAIL("unknown VolatilityType (" + type + ")");
                    throw new IllegalArgumentException("unknown VolatilityType");
            }
            swaption.setupArguments(engine_.getArguments());
            results_ = (Instrument.results) engine_.getResults();
        }

        @Override
        public double value(double x) {
            if (x != vol_.value()) {
                vol_.setValue(x);
                engine_.calculate();
            }
            return results_.value - targetValue_;
        }

        @Override
        public double derivative(double x) {
            if (x != vol_.value()) {
                vol_.setValue(x);
                engine_.calculate();
            }
            Object vega = results_.additionalResults.get("vega");
            QL_REQUIRE(vega != null, "vega not provided");
            return (Double) vega;
        }
    }
}
