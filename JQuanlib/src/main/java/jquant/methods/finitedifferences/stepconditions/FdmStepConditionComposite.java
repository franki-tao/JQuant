package jquant.methods.finitedifferences.stepconditions;

import jquant.Exercise;
import jquant.cashflows.Dividend;
import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Composite of finite-difference step conditions.
 *
 * <p>Port of {@code ql/methods/finitedifferences/stepconditions/fdmstepconditioncomposite.{hpp,cpp}}.
 * Only the European no-dividend path is implemented; American/Bermudan
 * exercise conditions and cash dividends are outside the scope of this port.
 */
public class FdmStepConditionComposite implements FdmStepCondition {
    private final List<Double> stoppingTimes_;
    private final List<FdmStepCondition> conditions_;

    public FdmStepConditionComposite(List<List<Double>> stoppingTimes,
                                     List<FdmStepCondition> conditions) {
        conditions_ = new ArrayList<>(conditions);

        final TreeSet<Double> allStoppingTimes = new TreeSet<>();
        for (List<Double> stoppingTime : stoppingTimes) {
            allStoppingTimes.addAll(stoppingTime);
        }
        stoppingTimes_ = new ArrayList<>(allStoppingTimes);
    }

    public List<FdmStepCondition> conditions() {
        return conditions_;
    }

    public List<Double> stoppingTimes() {
        return stoppingTimes_;
    }

    @Override
    public void applyTo(Array a, double t) {
        for (FdmStepCondition condition : conditions_) {
            condition.applyTo(a, t);
        }
    }

    public static FdmStepConditionComposite joinConditions(FdmSnapshotCondition c1,
                                                          FdmStepConditionComposite c2) {
        final List<List<Double>> stoppingTimes = new ArrayList<>();
        stoppingTimes.add(c2.stoppingTimes());
        stoppingTimes.add(Collections.singletonList(c1.getTime()));

        final List<FdmStepCondition> conditions = new ArrayList<>();
        conditions.add(c2);
        conditions.add(c1);

        return new FdmStepConditionComposite(stoppingTimes, conditions);
    }

    public static FdmStepConditionComposite vanillaComposite(
            List<Dividend> cashFlow,
            Exercise exercise,
            FdmMesher mesher,
            FdmInnerValueCalculator calculator,
            Date refDate,
            DayCounter dayCounter) {

        if (!cashFlow.isEmpty()) {
            QL_FAIL("cash dividends are not supported by this port");
        }

        QL_REQUIRE(exercise.type() == Exercise.Type.European,
                "exercise type is not supported");

        return new FdmStepConditionComposite(
                Collections.emptyList(), Collections.emptyList());
    }
}
