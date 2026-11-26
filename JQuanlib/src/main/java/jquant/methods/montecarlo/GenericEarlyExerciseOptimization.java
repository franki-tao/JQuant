package jquant.methods.montecarlo;

import jquant.math.Array;
import jquant.math.optimization.CostFunction;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.optimization.Problem;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Official QuantLib
 * {@code ql/methods/montecarlo/parametricexercise.cpp} free function port.
 *
 * <p>Returns the biased estimate obtained while optimizing.
 */
public final class GenericEarlyExerciseOptimization {

    private GenericEarlyExerciseOptimization() {
    }

    private static final class ValueEstimate extends CostFunction {

        private final List<NodeData> simulationData_;
        private final ParametricExercise exercise_;
        private final int exerciseIndex_;
        private final List<Double> parameters_;

        ValueEstimate(List<NodeData> simulationData,
                      ParametricExercise exercise,
                      int exerciseIndex) {
            simulationData_ = simulationData;
            exercise_ = exercise;
            exerciseIndex_ = exerciseIndex;
            parameters_ = new ArrayList<>();
            for (int i = 0; i < exercise.numberOfParameters().get(exerciseIndex); ++i)
                parameters_.add(0.0);

            for (NodeData i : simulationData_) {
                if (i.isValid)
                    return;
            }
            QL_FAIL("no valid paths");
        }

        @Override
        public double value(Array parameters) {
            for (int i = 0; i < parameters.size(); ++i)
                parameters_.set(i, parameters.get(i));
            double sum = 0.0;
            int n = 0;
            for (NodeData i : simulationData_) {
                if (i.isValid) {
                    ++n;
                    if (exercise_.exercise(exerciseIndex_, parameters_, i.values))
                        sum += i.exerciseValue;
                    else
                        sum += i.cumulatedCashFlows;
                }
            }
            return -sum / n;
        }

        @Override
        public Array values(Array x) {
            QL_FAIL("values method not implemented");
            return null;
        }
    }

    public static double genericEarlyExerciseOptimization(
            List<List<NodeData>> simulationData,
            ParametricExercise exercise,
            List<List<Double>> parameters,
            EndCriteria endCriteria,
            OptimizationMethod method) {

        int steps = simulationData.size();
        parameters.clear();
        for (int i = 0; i < steps - 1; ++i)
            parameters.add(new ArrayList<>());

        for (int i = steps - 1; i != 0; --i) {
            List<NodeData> exerciseData = simulationData.get(i);

            List<Double> currentParameters = parameters.get(i - 1);
            currentParameters.clear();
            for (int k = 0; k < exercise.numberOfParameters().get(i - 1); ++k)
                currentParameters.add(0.0);

            // optimize
            ValueEstimate f = new ValueEstimate(exerciseData, exercise, i - 1);

            Array guess = new Array(currentParameters.size());
            exercise.guess(i - 1, currentParameters);
            for (int k = 0; k < guess.size(); ++k)
                guess.set(k, currentParameters.get(k));

            NoConstraint c = new NoConstraint();

            Problem p = new Problem(f, c, guess);
            method.minimize(p, endCriteria);

            Array result = p.currentValue();
            for (int k = 0; k < result.size(); ++k)
                currentParameters.set(k, result.get(k));

            List<NodeData> previousData = simulationData.get(i - 1);
            for (int j = 0; j < previousData.size(); ++j) {
                if (exerciseData.get(j).isValid) {
                    if (exercise.exercise(i - 1, currentParameters,
                            exerciseData.get(j).values))
                        previousData.get(j).cumulatedCashFlows +=
                                exerciseData.get(j).exerciseValue;
                    else
                        previousData.get(j).cumulatedCashFlows +=
                                exerciseData.get(j).cumulatedCashFlows;
                }
            }
        }

        double sum = 0.0;
        List<NodeData> initialData = simulationData.get(0);
        for (NodeData node : initialData)
            sum += node.cumulatedCashFlows;
        return sum / initialData.size();
    }
}
