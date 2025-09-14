package jquant.math.distributions;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Inverse cumulative Student t-distribution
    /*! \todo Find/implement an efficient algorithm for evaluating the
              cumulative Student t-distribution, replacing the Newton
              iteration
    */
public class InverseCumulativeStudent {
    private StudentDistribution d_;
    private CumulativeStudentDistribution f_;
    private double accuracy_;
    private int maxIterations_;

    public InverseCumulativeStudent(int n) {
        this.d_ = new StudentDistribution(n);
        this.f_ = new CumulativeStudentDistribution(n);
        accuracy_ = 1e-6;
        maxIterations_ = 50;

    }

    public InverseCumulativeStudent(int n, double accuracy, int maxIterations) {
        this.d_ = new StudentDistribution(n);
        this.f_ = new CumulativeStudentDistribution(n);
        accuracy_ = accuracy;
        maxIterations_ = maxIterations;

    }

    public double value(double y) {
        QL_REQUIRE(y >= 0 && y <= 1, "argument out of range [0, 1]");

        double x = 0;
        int count = 0;

        // do a few newton steps to find x
        do {
            x -= (f_.value(x) - y) / d_.value(x);
            count++;
        } while (Math.abs(f_.value(x) - y) > accuracy_ && count < maxIterations_);

        QL_REQUIRE(count < maxIterations_,
                "maximum number of iterations reached in InverseCumulativeStudent");

        return x;
    }
}
