package jquant;

import jquant.pricingengines.GenericEngine;
import jquant.time.Date;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Continuous-averaging Asian option.
 *
 * <p>Ported from QuantLib {@code ql/instruments/asianoption.{hpp,cpp}}.
 */
public class ContinuousAveragingAsianOption extends OneAssetOption {
    /** Extra arguments for a continuous-average Asian option. */
    public static class arguments extends OneAssetOption.arguments {
        public Average.Type averageType;
        public Date startDate = new Date();

        @Override
        public void validate() {
            super.validate();
            QL_REQUIRE(averageType != null, "unspecified average type");
        }
    }

    /** Base class for continuous-average Asian engines. */
    public abstract static class engine
            extends GenericEngine<ContinuousAveragingAsianOption.arguments,
            OneAssetOption.results> {
        protected engine() {
            super(new ContinuousAveragingAsianOption.arguments(),
                    new OneAssetOption.results());
        }
    }

    protected Average.Type averageType_;
    protected Date startDate_ = new Date();

    /**
     * Constructor for unseasoned (fresh) options where averaging has not yet
     * started.
     */
    public ContinuousAveragingAsianOption(
            Average.Type averageType,
            Payoff payoff,
            Exercise exercise) {
        super(payoff, exercise);
        averageType_ = averageType;
    }

    /**
     * Constructor for seasoned options where averaging has already started.
     * The start date is a contract term specifying when averaging began.
     */
    public ContinuousAveragingAsianOption(
            Average.Type averageType,
            Date startDate,
            Payoff payoff,
            Exercise exercise) {
        super(payoff, exercise);
        averageType_ = averageType;
        startDate_ = startDate;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        super.setupArguments(args);
        if (!(args instanceof arguments moreArgs)) {
            QL_FAIL("wrong argument type");
            return;
        }
        moreArgs.averageType = averageType_;
        moreArgs.startDate = startDate_;
    }
}
