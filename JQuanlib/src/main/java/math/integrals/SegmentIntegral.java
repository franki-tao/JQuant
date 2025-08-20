package math.integrals;

import math.Function;

import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.close_enough;

public class SegmentIntegral extends Integrator {
    private int intervals_;

    public SegmentIntegral(int intervals) {
        super(1, 1);
        this.intervals_ = intervals;
        QL_REQUIRE(intervals > 0, "at least 1 interval needed, 0 given");
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        if (close_enough(a, b))
            return 0.0;
        double dx = (b - a) / intervals_;
        double sum = 0.5 * (f.value(a) + f.value(b));
        double end = b - 0.5 * dx;
        for (double x = a + dx; x < end; x += dx)
            sum += f.value(x);
        return sum * dx;
    }
}
