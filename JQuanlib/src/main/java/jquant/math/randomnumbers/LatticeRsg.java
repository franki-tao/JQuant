package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.methods.montecarlo.SampleVector;

import java.util.Arrays;
import java.util.List;

public class LatticeRsg {
    private int dimensionality_;
    private int N_;
    private long i_ = 0;
    private List<Double> z_;

    private SampleVector sequence_;

    public LatticeRsg(int dimensionality, List<Double> z, int N) {
        dimensionality_ = dimensionality;
        N_ = N;
        z_ = z;
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality, 0d), 1.0);
    }

    /**
     * ! skip to the n-th sample in the low-discrepancy sequence
     */
    public void skipTo(long n) {
        i_ += n;
    }

    public final SampleVector nextSequence() {
        for (int j = 0; j < dimensionality_; ++j) {
            double theta = i_ * z_.get(j) / N_;
            sequence_.value.set(j, Math.IEEEremainder(theta, 1.0));
        }
        ++i_;

        return sequence_;
    }

    public int dimension() {
        return dimensionality_;
    }

    public final SampleVector lastSequence() {
        return sequence_;
    }

    public static void main(String[] args) {
        int dim = 3;
        List<Double> z = Arrays.asList(1.1, 2.3, 4.7);  // 生成向量（每个维度一个值）
        int N = 1009;

        // 2. 创建格点序列生成器
        LatticeRsg rsg = new LatticeRsg(dim, z, N);

        // 3. 生成前5个样本
        for (int i = 0; i < 5; i++) {
            SampleVector sample = rsg.nextSequence();
            System.out.printf("第%d个样本：%s%n", i+1, sample.value);
        }

        // 4. 跳过前100个样本，生成第106个样本
        rsg.skipTo(100);
        SampleVector sample106 = rsg.nextSequence();
        System.out.printf("第106个样本：%s%n", sample106.value);
    }
}
