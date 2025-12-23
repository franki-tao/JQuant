package jquant.math.statistics.impl;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;

/**
 * 模拟 boost::accumulators::accumulator_set 的类，专注于加权统计量的计算。
 * 对应 Boost 中的 stats:
 * tag::count, tag::min, tag::max,
 * tag::weighted_mean, tag::weighted_variance,
 * tag::weighted_skewness, tag::weighted_kurtosis,
 * tag::sum_of_weights
 * * 注意：Commons Math 没有内置的加权偏度和加权峰度函数，因此我们将实现它们的标准公式。
 */
public class WeightedAccumulatorSet {
    private final List<Double> values = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    private double[] getValuesArray() {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double[] getWeightsArray() {
        return weights.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * 模拟 boost::accumulators::accumulator_set(value, weight = w)
     */
    public void addValue(double value, double weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("Weight must be non-negative.");
        }
        values.add(value);
        weights.add(weight);
    }

    // --- 对应 tag::count ---
    public int getCount() {
        return values.size();
    }

    // --- 对应 tag::sum_of_weights ---
    public double getSumOfWeights() {
        return DoubleStream.of(getWeightsArray()).sum();
    }

    // --- 对应 tag::min (使用 StatUtils) ---
    public double getMin() {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        return StatUtils.min(getValuesArray());
    }

    // --- 对应 tag::max (使用 StatUtils) ---
    public double getMax() {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        return StatUtils.max(getValuesArray());
    }

    // --- 对应 tag::weighted_mean (手动实现加权公式) ---
    public double getWeightedMean() {
        long count = getCount();
        if (count == 0) {
            return Double.NaN;
        }

        double sumOfWeights = getSumOfWeights();
        if (sumOfWeights == 0.0) {
            return Double.NaN;
        }

        double weightedSum = 0.0;
        for (int i = 0; i < count; i++) {
            weightedSum += values.get(i) * weights.get(i);
        }

        // 公式: (Σ xi * wi) / (Σ wi)
        return weightedSum / sumOfWeights;
    }

    // --- 对应 tag::weighted_variance (手动实现加权方差公式) ---
    public double getWeightedVariance() {
        long count = getCount();
        if (count < 2) {
            return 0.0;
        }

        double sumOfWeights = getSumOfWeights();
        if (sumOfWeights == 0.0) {
            return 0.0;
        }

        double mean = getWeightedMean();
        double sumOfSquaredWeights = 0.0;
        double weightedSquaredDeviationSum = 0.0;

        for (int i = 0; i < count; i++) {
            double weight = weights.get(i);
            double deviation = values.get(i) - mean;

            weightedSquaredDeviationSum += weight * FastMath.pow(deviation, 2);
            sumOfSquaredWeights += weight * weight;
        }

        // Boost 默认使用有偏/无偏修正后的方差估计 (Bias-corrected)
        // Dof = V1 - (V2 / V1)
        double V1 = sumOfWeights;
        double V2 = sumOfSquaredWeights;
        //V1 - (V2 / V1);

        if (sumOfWeights <= 0) {
            return 0.0;
        }

        // 公式: (Σ wi * (xi - x_w)^2) / DoF
        return weightedSquaredDeviationSum / sumOfWeights;
    }

    // --- 对应 tag::weighted_skewness (手动实现) ---
    public double getWeightedSkewness() {
        long count = getCount();
        if (count < 3) {
            return 0.0;
        }

        double mean = getWeightedMean();
        double sumOfWeights = getSumOfWeights();

        // 三阶中心矩 (M3)
        double moment3 = 0.0;
        for (int i = 0; i < count; i++) {
            double deviation = values.get(i) - mean;
            moment3 += weights.get(i) * FastMath.pow(deviation, 3);
        }

        double variance = getWeightedVariance();
        if (variance == 0.0) {
            return 0.0;
        }

        // 标准差
        double stdDev = FastMath.sqrt(variance);

        // 偏度的公式：(1/V1) * M3 / (sigma_w^3)  (Boost 的默认公式)
        return (1.0 / sumOfWeights) * moment3 / FastMath.pow(stdDev, 3);
    }

    // --- 对应 tag::weighted_kurtosis (手动实现) ---
    public double getWeightedKurtosis() {
        long count = getCount();
        if (count < 4) {
            return 0.0;
        }

        double mean = getWeightedMean();
        double sumOfWeights = getSumOfWeights();

        // 四阶中心矩 (M4)
        double moment4 = 0.0;
        for (int i = 0; i < count; i++) {
            double deviation = values.get(i) - mean;
            moment4 += weights.get(i) * FastMath.pow(deviation, 4);
        }

        double variance = getWeightedVariance();
        if (variance == 0.0) {
            return 0.0;
        }

        // 峰度的公式：(1/V1) * M4 / (sigma_w^4) - 3 (超额峰度)
        return (1.0 / sumOfWeights) * moment4 / FastMath.pow(variance, 2) - 3.0;
    }

    public static void main(String[] args) {
        // 1. 初始化
        WeightedAccumulatorSet acc = new WeightedAccumulatorSet();

        // 2. 数据输入 (模拟 Boost 的 acc(value, weight = w))
        acc.addValue(10.0, 1.0); // 值 10.0，权重 1.0
        acc.addValue(20.0, 2.0); // 值 20.0，权重 2.0
        acc.addValue(30.0, 1.0); // 值 30.0，权重 1.0

        // 3. 结果获取 (模拟 Boost 的 extract::tag(acc))
        System.out.println("--- Weighted Accumulator Set Results ---");
        System.out.println("Count (tag::count): " + acc.getCount());
        System.out.println("Min (tag::min): " + acc.getMin());
        System.out.println("Max (tag::max): " + acc.getMax());
        System.out.println("Sum of Weights (tag::sum_of_weights): " + acc.getSumOfWeights());

        System.out.println("\n--- Weighted Statistics ---");
        // 加权平均值 = (10*1 + 20*2 + 30*1) / (1+2+1) = 80 / 4 = 20.0
        System.out.println("Weighted Mean (tag::weighted_mean): " + acc.getWeightedMean());

        System.out.println("Weighted Variance (tag::weighted_variance): " + acc.getWeightedVariance());
        System.out.println("Weighted Skewness (tag::weighted_skewness): " + acc.getWeightedSkewness());
        System.out.println("Weighted Kurtosis (tag::weighted_kurtosis): " + acc.getWeightedKurtosis());
    }
}
