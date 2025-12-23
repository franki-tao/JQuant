package jquant.math.statistics.impl;
import org.apache.commons.math3.util.FastMath;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;

/**
 * 模拟 boost::accumulators::accumulator_set 的精简版本。
 * 专注于: tag::count, tag::sum_of_weights, tag::weighted_moment<2>。
 */
public class DownsideAccumulatorSet {
    // 存储数据值
    private final List<Double> values = new ArrayList<>();
    // 存储对应的权重
    private final List<Double> weights = new ArrayList<>();

    // 存储计算加权矩所需的加权平均值，用于增量计算（虽然这里是批处理，但保持独立计算平均值是合理的）
    private double currentWeightedMean = Double.NaN;
    private boolean isDirty = true; // 标记平均值是否需要重新计算

    /**
     * 模拟 boost::accumulators::accumulator_set(value, weight = w)
     * @param value 输入的数据值 (Real)
     * @param weight 对应的权重 (Real)
     */
    public void addValue(double value, double weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("Weight must be non-negative.");
        }
        values.add(value);
        weights.add(weight);
        // 数据改变，标记平均值需要重新计算
        isDirty = true;
    }

    // --- 对应 tag::count ---
    public int getCount() {
        return values.size();
    }

    // --- 对应 tag::sum_of_weights ---
    public double getSumOfWeights() {
        return DoubleStream.of(weights.stream().mapToDouble(Double::doubleValue).toArray()).sum();
    }

    /**
     * 内部方法：计算并缓存加权平均值 (Weighted Mean)
     */
    private double calculateAndCacheWeightedMean() {
        if (!isDirty && !Double.isNaN(currentWeightedMean)) {
            return currentWeightedMean;
        }

        long count = getCount();
        if (count == 0) {
            currentWeightedMean = Double.NaN;
            return currentWeightedMean;
        }

        double sumOfWeights = getSumOfWeights();
        if (sumOfWeights == 0.0) {
            currentWeightedMean = Double.NaN;
            return currentWeightedMean;
        }

        double weightedSum = 0.0;
        for (int i = 0; i < count; i++) {
            weightedSum += values.get(i) * weights.get(i);
        }

        currentWeightedMean = weightedSum / sumOfWeights;
        isDirty = false;
        return currentWeightedMean;
    }

    // --- 对应 tag::weighted_moment<2> ---
    /**
     * 获取加权二阶原点矩 (Weighted Second Raw Moment).
     * 对应 Boost 的 boost::accumulators::tag::moment<2>
     * 公式: M2' = (1/V1) * Σ (wi * xi^2)
     * @return M2' (加权二阶原点矩)
     */
    public double getWeightedSecondCentralMoment() {
        long count = getCount();
        if (count < 1) {
            return 0.0;
        }

        double sumOfWeights = getSumOfWeights();
        if (sumOfWeights <= 0.0) {
            return 0.0;
        }

        double weightedSquaredSum = 0.0;

        for (int i = 0; i < count; i++) {
            double weight = weights.get(i);
            double value = values.get(i);

            // 关键修改：直接计算值的平方，不减去 mean
            weightedSquaredSum += weight * (value * value);
        }

        // 公式: (Σ wi * xi^2) / Σ wi
        return weightedSquaredSum / sumOfWeights;
    }

    // 提供加权方差的快捷方法（虽然不是 Boost 定义的，但与 M2 密切相关）
    public double getWeightedVariance() {
        // Boost::Accumulators 中的加权方差 (tag::weighted_variance) 是 M2 的偏差修正版本。
        // M2 = 加权二阶中心矩
        double M2 = getWeightedSecondCentralMoment();

        if (getCount() < 2) return 0.0;

        double sumOfWeights = getSumOfWeights();
        double sumOfSquaredWeights = DoubleStream.of(weights.stream().mapToDouble(Double::doubleValue).toArray()).map(w -> w * w).sum();

        // 偏差修正自由度 DoF = V1 - (V2 / V1)
        double V1 = sumOfWeights;
        double V2 = sumOfSquaredWeights;
        double dof = V1 - (V2 / V1);

        if (dof <= 0) return 0.0;

        // 如果您需要的是修正后的方差 (Weighted Variance)，则公式为 M2 * (V1 / DoF)
        return M2 * (V1 / dof);
    }

    public static void main(String[] args) {
        DownsideAccumulatorSet acc = new DownsideAccumulatorSet();

        // 输入数据：
        acc.addValue(10.0, 1.0); // W=1
        acc.addValue(20.0, 2.0); // W=2
        acc.addValue(30.0, 1.0); // W=1

        // 总权重 (V1) = 1 + 2 + 1 = 4.0
        // 加权平均值 (x_w) = (10*1 + 20*2 + 30*1) / 4 = 80 / 4 = 20.0

        System.out.println("--- Downside Accumulator Set Results ---");
        System.out.println("Count: " + acc.getCount()); // 3
        System.out.println("Sum of Weights (V1): " + acc.getSumOfWeights()); // 4.0

        // --- Weighted Second Central Moment (M2) Calculation ---
        // (1/V1) * [ w1(x1-xw)^2 + w2(x2-xw)^2 + w3(x3-xw)^2 ]
        // (1/4) * [ 1*(10-20)^2 + 2*(20-20)^2 + 1*(30-20)^2 ]
        // (1/4) * [ 1*100 + 2*0 + 1*100 ]
        // (1/4) * 200 = 50.0
        System.out.println("Weighted Second Central Moment (M2): " + acc.getWeightedSecondCentralMoment()); // 50.0

        // 偏差修正后的加权方差 (仅供参考，非Boost强制要求)
        System.out.println("Bias-Corrected Weighted Variance: " + acc.getWeightedVariance());
        // 预期 Variance = 50.0 (M2) * (4.0 / (4.0 - (1^2+2^2+1^2)/4.0)) = 50 * (4 / (4 - 6/4)) = 50 * (4 / 2.5) = 80.0
    }
}
