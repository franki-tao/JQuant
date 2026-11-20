package jquant;

import jquant.pricingengines.GenericEngine;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashMap;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Barrier option on two assets.
 *
 * <p>The value of the first asset is compared to the strike to determine the
 * payoff, while the value of the second asset is monitored to check whether
 * the barrier is hit.
 *
 * <p>Ported from QuantLib
 * {@code ql/instruments/twoassetbarrieroption.{hpp,cpp}}. As in the official
 * class, this derives from {@link Option}, not from
 * {@link OneAssetOption}.
 */
public class TwoAssetBarrierOption extends Option {

    /** Extra arguments for a two-asset barrier option calculation. */
    public static class arguments extends Option.arguments {
        public Barrier.Type barrierType = null;
        public Double barrier = Double.NaN;

        @Override
        public void validate() {
            super.validate();

            if (barrierType == null) {
                QL_FAIL("unknown type");
            }
            switch (barrierType) {
                case DownIn:
                case UpIn:
                case DownOut:
                case UpOut:
                    break;
                default:
                    QL_FAIL("unknown type");
            }

            QL_REQUIRE(!Double.isNaN(barrier), "no barrier given");
        }
    }

    /** Results from a two-asset barrier option calculation. */
    public static class results extends Instrument.results {
        public results() {
            // Instrument.results::reset() clears this map, so it must exist
            // before the first reset.
            additionalResults = new HashMap<>();
            reset();
        }
    }

    /** Base class for two-asset barrier-option engines. */
    public abstract static class engine
            extends GenericEngine<TwoAssetBarrierOption.arguments,
            TwoAssetBarrierOption.results> {
        protected engine() {
            super(new TwoAssetBarrierOption.arguments(),
                    new TwoAssetBarrierOption.results());
        }

        /**
         * Official strict comparison, as for single-barrier options: a down
         * barrier is triggered when the underlying is below it, an up
         * barrier when above it. Equality is not triggered (unlike the
         * inclusive comparison used by double-barrier options).
         */
        protected boolean triggered(double underlying) {
            switch (arguments_.barrierType) {
                case DownIn:
                case DownOut:
                    return underlying < arguments_.barrier;
                case UpIn:
                case UpOut:
                    return underlying > arguments_.barrier;
                default:
                    QL_FAIL("unknown type");
                    return false;
            }
        }
    }

    protected Barrier.Type barrierType_;
    protected double barrier_;

    public TwoAssetBarrierOption(Barrier.Type barrierType,
                                 double barrier,
                                 Payoff payoff,
                                 Exercise exercise) {
        super(payoff, exercise);
        barrierType_ = barrierType;
        barrier_ = barrier;
    }

    @Override
    public boolean isExpired() {
        // QuantLib detail::simple_event(exercise_->lastDate()).hasOccurred()
        // with the default reference date.
        Date today = Settings.instance.evaluationDate().Date();
        return Settings.instance.includeReferenceDateEvents()
                ? TimeUtils.less(exercise_.lastDate(), today)
                : TimeUtils.leq(exercise_.lastDate(), today);
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        super.setupArguments(args);

        if (!(args instanceof TwoAssetBarrierOption.arguments moreArgs)) {
            QL_FAIL("wrong argument type");
            return;
        }
        moreArgs.barrierType = barrierType_;
        moreArgs.barrier = barrier_;
    }
}
