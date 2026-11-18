package jquant;

import jquant.pricingengines.GenericEngine;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Discrete-averaging Asian option.
 *
 * <p>Ported from QuantLib {@code ql/instruments/asianoption.{hpp,cpp}}.
 */
public class DiscreteAveragingAsianOption extends OneAssetOption {
    /** Extra arguments for a discrete-average Asian option. */
    public static class arguments extends OneAssetOption.arguments {
        public Average.Type averageType = null;
        public Double runningAccumulator = Double.NaN;
        public Integer pastFixings = null;
        public List<Date> fixingDates = new ArrayList<>();

        @Override
        public void validate() {
            super.validate();
            QL_REQUIRE(averageType != null, "unspecified average type");
            QL_REQUIRE(pastFixings != null, "null past-fixing number");
            QL_REQUIRE(!Double.isNaN(runningAccumulator), "null running product");
            switch (averageType) {
                case Arithmetic:
                    QL_REQUIRE(runningAccumulator >= 0.0,
                            "non negative running sum required: "
                                    + runningAccumulator + " not allowed");
                    break;
                case Geometric:
                    QL_REQUIRE(runningAccumulator > 0.0,
                            "positive running product required: "
                                    + runningAccumulator + " not allowed");
                    break;
                default:
                    QL_FAIL("invalid average type");
            }
        }
    }

    /** Base class for discrete-average Asian engines. */
    public abstract static class engine
            extends GenericEngine<DiscreteAveragingAsianOption.arguments,
            OneAssetOption.results> {
        protected engine() {
            super(new DiscreteAveragingAsianOption.arguments(),
                    new OneAssetOption.results());
        }
    }

    protected Average.Type averageType_;
    protected double runningAccumulator_;
    protected int pastFixings_;
    protected List<Date> fixingDates_;
    protected boolean allPastFixingsProvided_;
    protected List<Double> allPastFixings_;

    /**
     * Constructor taking the running sum or product of past fixings,
     * depending on the average type. The fixing dates passed here can be only
     * the future ones.
     */
    public DiscreteAveragingAsianOption(
            Average.Type averageType,
            double runningAccumulator,
            int pastFixings,
            List<Date> fixingDates,
            Payoff payoff,
            Exercise exercise) {
        super(payoff, exercise);
        averageType_ = averageType;
        runningAccumulator_ = runningAccumulator;
        pastFixings_ = pastFixings;
        fixingDates_ = new ArrayList<>(fixingDates);
        allPastFixingsProvided_ = false;

        fixingDates_.sort(Comparator.comparingInt(Date::serialNumber));

        // Hard override of the running accumulator when the option is
        // unseasoned, as in QuantLib.
        if (pastFixings_ == 0) {
            if (averageType == Average.Type.Geometric) {
                runningAccumulator_ = 1.0;
            } else if (averageType == Average.Type.Arithmetic) {
                runningAccumulator_ = 0.0;
            } else {
                QL_FAIL("Unrecognised average type, must be "
                        + "Average::Arithmetic or Average::Geometric");
            }
        }
    }

    /**
     * Constructor taking a vector of past fixings, defaulting to an empty
     * vector representing an unseasoned option. This constructor expects
     * <em>all</em> fixing dates to be provided, including those in the past,
     * and to be already sorted. During the calculations, the option compares
     * them to the evaluation date to determine which are historic; it then
     * takes as many values from {@code allPastFixings} as needed and ignores
     * the others. If not enough fixings are provided, it raises an error.
     */
    public DiscreteAveragingAsianOption(
            Average.Type averageType,
            List<Date> fixingDates,
            Payoff payoff,
            Exercise exercise) {
        this(averageType, fixingDates, payoff, exercise, new ArrayList<>());
    }

    public DiscreteAveragingAsianOption(
            Average.Type averageType,
            List<Date> fixingDates,
            Payoff payoff,
            Exercise exercise,
            List<Double> allPastFixings) {
        super(payoff, exercise);
        averageType_ = averageType;
        runningAccumulator_ = 0.0;
        pastFixings_ = 0;
        // QuantLib's full-history constructor does not sort: the caller must
        // provide the dates already sorted.
        fixingDates_ = new ArrayList<>(fixingDates);
        allPastFixingsProvided_ = true;
        allPastFixings_ = new ArrayList<>(allPastFixings);
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        double runningAccumulator = runningAccumulator_;
        int pastFixings = pastFixings_;
        List<Date> fixingDates = new ArrayList<>(fixingDates_);

        // If the option was initialised with a list of fixings, compare the
        // evaluation date to the fixing dates and derive the past fixings,
        // future fixing dates and running accumulator accordingly.
        if (allPastFixingsProvided_) {
            List<Date> futureFixingDates = new ArrayList<>();
            Date today = Settings.instance.evaluationDate().Date();

            pastFixings = 0;
            for (Date fixingDate : fixingDates_) {
                if (TimeUtils.less(fixingDate, today)) {
                    pastFixings += 1;
                } else {
                    futureFixingDates.add(fixingDate);
                }
            }
            fixingDates = futureFixingDates;

            if (pastFixings > allPastFixings_.size()) {
                QL_FAIL("Not enough past fixings have been provided for the "
                        + "required historical fixing dates");
            }

            if (averageType_ == Average.Type.Geometric) {
                runningAccumulator = 1.0;
                for (int i = 0; i < pastFixings; i++) {
                    runningAccumulator *= allPastFixings_.get(i);
                }
            } else if (averageType_ == Average.Type.Arithmetic) {
                runningAccumulator = 0.0;
                for (int i = 0; i < pastFixings; i++) {
                    runningAccumulator += allPastFixings_.get(i);
                }
            } else {
                QL_FAIL("Unrecognised average type, must be "
                        + "Average::Arithmetic or Average::Geometric");
            }
        }

        super.setupArguments(args);

        if (!(args instanceof arguments moreArgs)) {
            QL_FAIL("wrong argument type");
            return;
        }
        moreArgs.averageType = averageType_;
        moreArgs.runningAccumulator = runningAccumulator;
        moreArgs.pastFixings = pastFixings;
        moreArgs.fixingDates = fixingDates;
    }
}
