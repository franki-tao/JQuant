package jquant.math.distributions;

import org.apache.commons.math3.distribution.NormalDistribution;

//! Moro Inverse cumulative normal distribution class
    /*! Given x between zero and one as
        the integral value of a gaussian normal distribution
        this class provides the value y such that
        formula here ...

        It uses Beasly and Springer approximation, with an improved
        approximation for the tails. See Boris Moro,
        "The Full Monte", 1995, Risk Magazine.

        This class can also be used to generate a gaussian normal
        distribution from a uniform distribution.
        This is especially useful when a gaussian normal distribution
        is generated from a low discrepancy uniform distribution:
        in this case the traditional Box-Muller approach and its
        variants would not preserve the sequence's low-discrepancy.

        Peter J. Acklam's approximation is better and is available
        as QuantLib::InverseCumulativeNormal
    */
public class MoroInverseCumulativeNormal {
    private double average_;
    private double sigma_;
    private static final double a0_ =  2.50662823884;
    private static final double a1_ =-18.61500062529;
    private static final double a2_ = 41.39119773534;
    private static final double a3_ =-25.44106049637;

    private static final double b0_ = -8.47351093090;
    private static final double b1_ = 23.08336743743;
    private static final double b2_ =-21.06224101826;
    private static final double b3_ =  3.13082909833;

    private static final double c0_ = 0.3374754822726147;
    private static final double c1_ = 0.9761690190917186;
    private static final double c2_ = 0.1607979714918209;
    private static final double c3_ = 0.0276438810333863;
    private static final double c4_ = 0.0038405729373609;
    private static final double c5_ = 0.0003951896511919;
    private static final double c6_ = 0.0000321767881768;
    private static final double c7_ = 0.0000002888167364;
    private static final double c8_ = 0.0000003960315187;

    public MoroInverseCumulativeNormal() {
        average_ = 0;
        sigma_ = 1;
    }

    public MoroInverseCumulativeNormal(double average, double sigma) {
        this.average_ = average;
        this.sigma_ = sigma;
    }

    public double value(double x) {
        org.apache.commons.math3.distribution.NormalDistribution normalDist =
                new org.apache.commons.math3.distribution.NormalDistribution(average_, sigma_);
        return normalDist.inverseCumulativeProbability(x);
    }

    public static void main(String[] args) {
        MoroInverseCumulativeNormal normal = new MoroInverseCumulativeNormal();
        System.out.println(normal.value(0.1));
        CumulativeNormalDistribution distribution = new CumulativeNormalDistribution(0,1);
        NormalDistribution distribution1 = new NormalDistribution();
        System.out.println(distribution.value(0.1));
        System.out.println(distribution1.cumulativeProbability(0.1));
        System.out.println(distribution.derivative(0.1));
        System.out.println(distribution1.inverseCumulativeProbability(0.1));
    }
}
