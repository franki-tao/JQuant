package jquant.methods.finitedifferences.stepconditions;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bermudan step condition for multi-dimensional problems.
 *
 * <p>Port of {@code ql/methods/finitedifferences/stepconditions/fdmbermudanstepcondition.{hpp,cpp}}.
 * The condition takes the maximum of the continuation value and the inner
 * value only on the prescribed exercise dates.
 */
public class FdmBermudanStepCondition implements FdmStepCondition {
    private final List<Double> exerciseTimes_;
    private final FdmMesher mesher_;
    private final FdmInnerValueCalculator calculator_;

    public FdmBermudanStepCondition(List<Date> exerciseDates,
                                    Date referenceDate,
                                    DayCounter dayCounter,
                                    FdmMesher mesher,
                                    FdmInnerValueCalculator calculator) {
        mesher_ = mesher;
        calculator_ = calculator;

        exerciseTimes_ = new ArrayList<>(exerciseDates.size());
        for (Date exerciseDate : exerciseDates) {
            exerciseTimes_.add(dayCounter.yearFraction(
                    referenceDate, exerciseDate));
        }
    }

    public List<Double> exerciseTimes() {
        return Collections.unmodifiableList(exerciseTimes_);
    }

    @Override
    public void applyTo(Array a, double t) {
        // As in QuantLib, the condition is applied only when the solver
        // reaches one of the prescribed exercise times exactly.
        if (exerciseTimes_.contains(t)) {
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
}
