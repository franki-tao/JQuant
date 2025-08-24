package math.interpolations;

import math.AbcdMathFunction;
import math.optimization.EndCriteria;
import math.optimization.OptimizationMethod;
import math.templateImpl;
import termstructures.volatility.AbcdCalibration;

import java.util.ArrayList;
import java.util.List;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.NULL_REAL;

public class AbcdInterpolationImpl extends templateImpl {
    public double a_, b_, c_, d_;
    public boolean aIsFixed_ = false, bIsFixed_ = false, cIsFixed_ = false, dIsFixed_ = false;
    public List<Double> k_;
    public double error_, maxError_;
    public EndCriteria.Type abcdEndCriteria_ = EndCriteria.Type.None;

    private EndCriteria endCriteria_;
    private OptimizationMethod optMethod_;
    private boolean vegaWeighted_;
    private AbcdCalibration abcdCalibrator_;

    public AbcdInterpolationImpl(
            double[] x,
            double[] y,
            double a,
            double b,
            double c,
            double d,
            boolean aIsFixed,
            boolean bIsFixed,
            boolean cIsFixed,
            boolean dIsFixed,
            boolean vegaWeighted,
            EndCriteria endCriteria,
            OptimizationMethod optMethod
    ) {
        super(x, y, 2);
        a_ = a;
        b_ = b;
        c_ = c;
        d_ = d;
        error_ = NULL_REAL;
        maxError_ = NULL_REAL;
        if (a_ != NULL_REAL)
            aIsFixed_ = aIsFixed;
        else a_ = -0.06;
        if (b_ != NULL_REAL)
            bIsFixed_ = bIsFixed;
        else b_ = 0.17;
        if (c_ != NULL_REAL)
            cIsFixed_ = cIsFixed;
        else c_ = 0.54;
        if (d_ != NULL_REAL)
            dIsFixed_ = dIsFixed;
        else d_ = 0.17;
        AbcdMathFunction.validate(a, b, c, d);
        endCriteria_ = endCriteria;
        optMethod_ = optMethod;
        vegaWeighted_ = vegaWeighted;

    }

    @Override
    public void update() {
        List<Double> times, blackVols;
        times = new ArrayList<>();
        blackVols = new ArrayList<>();
        for (int i = 0; i < super.xValue.length; ++i) {
            times.add(super.xValue[i]);
            blackVols.add(super.yValue[i]);
        }
        abcdCalibrator_ = new AbcdCalibration(times, blackVols,
                a_, b_, c_, d_,
                aIsFixed_, bIsFixed_,
                cIsFixed_, dIsFixed_,
                vegaWeighted_,
                endCriteria_,
                optMethod_);
        abcdCalibrator_.compute();
        a_ = abcdCalibrator_.a();
        b_ = abcdCalibrator_.b();
        c_ = abcdCalibrator_.c();
        d_ = abcdCalibrator_.d();
        k_ = abcdCalibrator_.k(times, blackVols);
        error_ = abcdCalibrator_.error();
        maxError_ = abcdCalibrator_.maxError();
        abcdEndCriteria_ = abcdCalibrator_.endCriteria();
    }

    @Override
    public double value(double v) {
        QL_REQUIRE(v>=0.0, "time must be non negative: " +
                v + " not allowed");
        return abcdCalibrator_.value(v);
    }

    @Override
    public double primitive(double v) {
        QL_FAIL("Abcd primitive not implemented");
        return 0;
    }

    @Override
    public double derivative(double v) {
        QL_FAIL("Abcd derivative not implemented");
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        QL_FAIL("Abcd secondDerivative not implemented");
        return 0;
    }

    public final double k(double t) {
        LinearInterpolation li = new LinearInterpolation(this.xValue, this.yValue);
        return li.value(t, false);
    }
}
