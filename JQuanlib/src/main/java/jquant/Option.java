package jquant;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! base option class
public abstract class Option extends Instrument {
    public enum Type {Put, Call}

    // arguments
    protected Payoff payoff_;
    protected Exercise exercise_;

    public static class arguments implements PricingEngine.arguments {
        public Payoff payoff;
        public Exercise exercise;

        @Override
        public void validate() {
            QL_REQUIRE(payoff != null, "no payoff given");
            QL_REQUIRE(exercise != null, "no exercise given");
        }
    }

    public Option(Payoff payoff, Exercise exercise) {
        payoff_ = payoff;
        exercise_ = exercise;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        if (args instanceof Option.arguments arguments) {
            arguments.payoff = payoff_;
            arguments.exercise = exercise_;
        } else {
            QL_FAIL("wrong argument type");
        }
    }

    public Payoff payoff() {
        return payoff_;
    }

    public Exercise exercise() {
        return exercise_;
    }
}
