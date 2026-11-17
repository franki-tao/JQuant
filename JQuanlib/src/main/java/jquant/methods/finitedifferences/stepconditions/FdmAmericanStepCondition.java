package jquant.methods.finitedifferences.stepconditions;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * American step condition for multi-dimensional problems.
 *
 * <p>Port of {@code ql/methods/finitedifferences/stepconditions/fdmamericanstepcondition.{hpp,cpp}}.
 */
public class FdmAmericanStepCondition implements FdmStepCondition {
    private final FdmMesher mesher_;
    private final FdmInnerValueCalculator calculator_;
    private final double exerciseStart_;

    public FdmAmericanStepCondition(FdmMesher mesher,
                                    FdmInnerValueCalculator calculator,
                                    double exerciseStart) {
        mesher_ = mesher;
        calculator_ = calculator;
        exerciseStart_ = exerciseStart;
    }

    public FdmAmericanStepCondition(FdmMesher mesher,
                                    FdmInnerValueCalculator calculator) {
        this(mesher, calculator, 0.0);
    }

    @Override
    public void applyTo(Array a, double t) {
        if (t < exerciseStart_)
            return;

        QL_REQUIRE(mesher_.layout().size() == a.size(),
                "inconsistent array dimensions");

        for (FdmLinearOpIterator iter : mesher_.layout()) {
            final double innerValue = calculator_.innerValue(iter, t);
            if (innerValue > a.get(iter.index())) {
                a.set(iter.index(), innerValue);
            }
        }
    }
}
