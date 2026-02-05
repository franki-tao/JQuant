package jquant.models.volatility;

import jquant.math.Array;
import jquant.math.optimization.Constraint;
import jquant.math.optimization.impl.ConstraintImpl;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class Garch11Constraint extends Constraint {
    private static class Impl extends ConstraintImpl {
        double gammaLower_, gammaUpper_;
        public Impl(double gammaLower, double gammaUpper) {
            gammaLower_ = gammaLower;
            gammaUpper_ = gammaUpper;
        }
        @Override
        public boolean test(final Array x) {
            QL_REQUIRE(x.size() >= 3, "size of parameters vector < 3");
            return x.get(0) > 0 && x.get(1) >= 0 && x.get(2) >= 0
                    && x.get(1) + x.get(2) < gammaUpper_
                    && x.get(1) + x.get(2) >= gammaLower_;
        }
    }

    public Garch11Constraint(double gammaLower, double gammaUpper) {
        super(new Impl(gammaLower, gammaUpper));
    }
}
