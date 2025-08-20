package math.integrals;

import math.Function;

import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.QL_EPSILON;

public abstract class Integrator {
    private double absoluteAccuracy_;
    private double absoluteError_;
    private int maxEvaluations_;
    private int evaluations_;

    public Integrator(double absoluteAccuracy, int maxEvaluations) {
        QL_REQUIRE(absoluteAccuracy > QL_EPSILON,
                String.format("required tolerance %.30f not allowed. It must be > %.30f", absoluteAccuracy, QL_EPSILON));
        this.absoluteAccuracy_ = absoluteAccuracy;
        this.maxEvaluations_ = maxEvaluations;
    }

    public double value(Function f, double a, double b) {
        evaluations_ = 0;
        if (a == b)
            return 0.0;
        if (b > a)
            return integrate(f, a, b);
        else
            return -integrate(f, b, a);
    }

    public void setAbsoluteAccuracy(double accuracy) {
        this.absoluteAccuracy_ = accuracy;
    }

    public void setMaxEvaluations(int maxEvaluations) {
        this.maxEvaluations_ = maxEvaluations;
    }

    public double absoluteAccuracy() {
        return absoluteAccuracy_;
    }

    public int maxEvaluations() {
        return maxEvaluations_;
    }

    public double absoluteError() {
        return absoluteError_;
    }

    public int numberOfEvaluations() {
        return evaluations_;
    }

    public boolean integrationSuccess() {
        return evaluations_ <= maxEvaluations_
                && absoluteError_ <= absoluteAccuracy_;
    }

    protected abstract double integrate(Function f, double a, double b);

    protected void setAbsoluteError(double error) {
        absoluteError_ = error;
    }

    protected void setNumberOfEvaluations(int evaluations) {
        evaluations_ = evaluations;
    }

    protected void increaseNumberOfEvaluations(int increase) {
        evaluations_ += increase;
    }


}
