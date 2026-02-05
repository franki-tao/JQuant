package jquant.models.volatility;

import jquant.math.Array;
import jquant.math.optimization.Constraint;
import jquant.math.optimization.impl.ConstraintImpl;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class FitAcfConstraint extends Constraint {
    private static class Impl extends ConstraintImpl {
        double gammaLower_, gammaUpper_;
        public Impl(double gammaLower, double gammaUpper) {
            gammaLower_ = gammaLower;
            gammaUpper_ = gammaUpper;
        }

        @Override
        public boolean test(final Array x) {
            QL_REQUIRE(x.size() >= 2, "size of parameters vector < 2");
            return x.get(0) >= gammaLower_ && x.get(0) < gammaUpper_
                    && x.get(1) >= 0 && x.get(1) <= x.get(0);
        }
    }

    public FitAcfConstraint(double gammaLower, double gammaUpper) {
        super(new Impl(gammaLower, gammaUpper));
    }
}
