package jquant.methods.montecarlo;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.matrixutilities.SVD;
import jquant.math.statistics.GenericRiskStatistics;
import jquant.math.statistics.SequenceStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/methods/montecarlo/genericlsregression.hpp/.cpp} port.
 *
 * <p>Returns the biased estimate obtained while regressing.
 */
public final class GenericLongstaffSchwartzRegression {

    private GenericLongstaffSchwartzRegression() {
    }

    public static double genericLongstaffSchwartzRegression(
            List<List<NodeData>> simulationData,
            List<List<Double>> basisCoefficients) {

        int steps = simulationData.size();
        basisCoefficients.clear();
        for (int i = 0; i < steps - 1; ++i)
            basisCoefficients.add(new ArrayList<>());

        for (int i = steps - 1; i != 0; --i) {

            List<NodeData> exerciseData = simulationData.get(i);

            // 1) find the covariance matrix of basis function values and
            //    deflated cash-flows
            int N = exerciseData.get(0).values.size();
            List<Double> temp = new ArrayList<>();
            for (int j = 0; j < N + 1; ++j)
                temp.add(0.0);
            SequenceStatistics stats = new SequenceStatistics(N + 1);

            for (int j = 0; j < exerciseData.size(); ++j) {
                NodeData data = exerciseData.get(j);
                if (data.isValid) {
                    for (int k = 0; k < data.values.size(); ++k)
                        temp.set(k, data.values.get(k));
                    temp.set(N, data.cumulatedCashFlows - data.controlValue);

                    stats.add(temp, 1.0);
                }
            }

            List<Double> means = new ArrayList<>(stats.mean());
            Matrix covariance = stats.covariance();

            Matrix C = new Matrix(N, N, 0.0);
            Array target = new Array(N, 0.0);
            for (int k = 0; k < N; ++k) {
                target.set(k, covariance.get(k, N) + means.get(k) * means.get(N));
                for (int l = 0; l <= k; ++l) {
                    double value = covariance.get(k, l) + means.get(k) * means.get(l);
                    C.set(k, l, value);
                    C.set(l, k, value);
                }
            }

            // 2) solve for least squares regression
            Array alphas = new SVD(C).solveFor(target);
            List<Double> coefficients = new ArrayList<>();
            for (int k = 0; k < alphas.size(); ++k)
                coefficients.add(alphas.get(k));
            basisCoefficients.set(i - 1, coefficients);

            // 3) use exercise strategy to divide paths into exercise and
            //    non-exercise domains
            for (int j = 0; j < exerciseData.size(); ++j) {
                NodeData data = exerciseData.get(j);
                if (data.isValid) {
                    double exerciseValue = data.exerciseValue;
                    double continuationValue = data.cumulatedCashFlows;
                    double estimatedContinuationValue = data.controlValue;
                    for (int k = 0; k < data.values.size(); ++k)
                        estimatedContinuationValue +=
                                data.values.get(k) * coefficients.get(k);

                    // for exercise paths, add deflated rebate to
                    // deflated cash-flows at previous time frame;
                    // for non-exercise paths, add deflated cash-flows to
                    // deflated cash-flows at previous time frame
                    double value = estimatedContinuationValue <= exerciseValue ?
                            exerciseValue :
                            continuationValue;

                    simulationData.get(i - 1).get(j).cumulatedCashFlows += value;
                }
            }
        }

        // the value of the product can now be estimated by averaging
        // over all paths
        GenericRiskStatistics estimate = new GenericRiskStatistics();
        for (NodeData node : simulationData.get(0))
            estimate.add(node.cumulatedCashFlows, 1.0);

        return estimate.mean();
    }
}
