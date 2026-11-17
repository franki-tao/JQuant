package jquant.methods.finitedifferences.stepconditions;

import jquant.Exercise;
import jquant.cashflows.Dividend;
import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.utilities.FdmDividendHandler;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Composite of finite-difference step conditions.
 *
 * <p>Port of {@code ql/methods/finitedifferences/stepconditions/fdmstepconditioncomposite.{hpp,cpp}}.
 * European, American and Bermudan exercise and cash dividends are implemented.
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

        final List<List<Double>> stoppingTimes = new ArrayList<>();
        final List<FdmStepCondition> stepConditions = new ArrayList<>();

        if (!cashFlow.isEmpty()) {
            final Date maturityDate = exercise.lastDate();
            final List<Dividend> dividends = new ArrayList<>();
            for (Dividend dividend : cashFlow) {
                if (TimeUtils.geq(dividend.date(), refDate)
                        && TimeUtils.leq(dividend.date(), maturityDate)) {
                    dividends.add(dividend);
                }
            }

            final FdmDividendHandler dividendCondition =
                    new FdmDividendHandler(dividends, mesher, refDate,
                            dayCounter, 0);
            stepConditions.add(dividendCondition);

            final List<Double> dividendTimes =
                    new ArrayList<>(dividendCondition.dividendTimes());
            final double maturityTime = dayCounter.yearFraction(
                    refDate, exercise.lastDate());

            // this effectively excludes times after maturity
            for (int i = 0; i < dividendTimes.size(); ++i) {
                dividendTimes.set(i,
                        Math.min(maturityTime, dividendTimes.get(i)));
            }
            stoppingTimes.add(new ArrayList<>(dividendTimes));

            // smoother convergence behavior with number of time steps
            final List<Double> shiftedDividendTimes = new ArrayList<>();
            for (double t : dividendTimes) {
                shiftedDividendTimes.add(Math.min(maturityTime, t + 1e-5));
            }
            stoppingTimes.add(shiftedDividendTimes);
        }

        QL_REQUIRE(exercise.type() == Exercise.Type.American
                        || exercise.type() == Exercise.Type.European
                        || exercise.type() == Exercise.Type.Bermudan,
                "exercise type is not supported");

        if (exercise.type() == Exercise.Type.American) {
            final double exerciseStart = dayCounter.yearFraction(
                    refDate, exercise.date(0));
            stepConditions.add(new FdmAmericanStepCondition(
                    mesher, calculator, exerciseStart));
        } else if (exercise.type() == Exercise.Type.Bermudan) {
            final FdmBermudanStepCondition bermudanCondition =
                    new FdmBermudanStepCondition(exercise.dates(),
                            refDate, dayCounter, mesher, calculator);
            stepConditions.add(bermudanCondition);
            stoppingTimes.add(bermudanCondition.exerciseTimes());
        }

        return new FdmStepConditionComposite(stoppingTimes, stepConditions);
    }
}
