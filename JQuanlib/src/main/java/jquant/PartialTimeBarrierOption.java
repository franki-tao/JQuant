package jquant;

import jquant.pricingengines.GenericEngine;
import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Partial-time barrier option.
 *
 * <p>A particular type of barrier option in which the barrier is only
 * monitored for a part of the option's lifetime; either from the start to a
 * so-called cover event, or from the cover event to the exercise date.
 *
 * <p>Ported from QuantLib
 * {@code ql/instruments/partialtimebarrieroption.{hpp,cpp}}.
 */
public class PartialTimeBarrierOption extends OneAssetOption {

    /** Extra arguments for a partial-time barrier option calculation. */
    public static class arguments extends Option.arguments {
        public Barrier.Type barrierType = null;
        public PartialBarrier.Range barrierRange = null;
        public Double barrier = Double.NaN;
        public Double rebate = Double.NaN;
        public Date coverEventDate = new Date();

        @Override
        public void validate() {
            super.validate();

            QL_REQUIRE(barrier != null && !Double.isNaN(barrier),
                    "no barrier given");
            QL_REQUIRE(rebate != null && !Double.isNaN(rebate),
                    "no rebate given");
            QL_REQUIRE(coverEventDate != null
                            && !TimeUtils.equals(coverEventDate, new Date()),
                    "no cover event date given");
            QL_REQUIRE(TimeUtils.less(coverEventDate, exercise.lastDate()),
                    "cover event date equal or later than exercise date");
        }
    }

    /** Base class for partial-time barrier-option engines. */
    public abstract static class engine
            extends GenericEngine<PartialTimeBarrierOption.arguments,
            OneAssetOption.results> {
        protected engine() {
            super(new PartialTimeBarrierOption.arguments(),
                    new OneAssetOption.results());
        }
    }

    protected Barrier.Type barrierType_;
    protected PartialBarrier.Range barrierRange_;
    protected double barrier_;
    protected double rebate_;
    protected Date coverEventDate_;

    public PartialTimeBarrierOption(Barrier.Type barrierType,
                                    PartialBarrier.Range barrierRange,
                                    double barrier,
                                    double rebate,
                                    Date coverEventDate,
                                    Payoff payoff,
                                    Exercise exercise) {
        super(payoff, exercise);
        barrierType_ = barrierType;
        barrierRange_ = barrierRange;
        barrier_ = barrier;
        rebate_ = rebate;
        coverEventDate_ = coverEventDate;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        super.setupArguments(args);

        if (!(args instanceof PartialTimeBarrierOption.arguments moreArgs)) {
            QL_FAIL("wrong argument type");
            return;
        }
        moreArgs.barrierType = barrierType_;
        moreArgs.barrierRange = barrierRange_;
        moreArgs.barrier = barrier_;
        moreArgs.rebate = rebate_;
        moreArgs.coverEventDate = coverEventDate_;
    }
}
