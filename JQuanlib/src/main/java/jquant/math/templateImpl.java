package jquant.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.close;

public abstract class templateImpl extends Impl {
    protected double[] xValue;
    protected double[] yValue;

    public templateImpl(double[] xValue, double[] yValue, int requiredPoints) {
        this.xValue = xValue;
        this.yValue = yValue;
        if (xValue.length <= requiredPoints || yValue.length < xValue.length) {
            QL_FAIL("not enough points to interpolate");
        }
    }

    @Override
    public double xMin() {
        return xValue[0];
    }

    @Override
    public double xMax() {
        return xValue[xValue.length - 1];
    }

    @Override
    public List<Double> xValues() {
        return Arrays.stream(xValue).boxed().collect(Collectors.toList());
    }

    @Override
    public List<Double> yValues() {
        List<Double> res = new ArrayList<>();
        for (int i = 0; i < xValue.length; i++) {
            res.add(yValue[i]);
        }
        return res;
    }

    @Override
    public boolean isInRange(double x) {
        double x1 = xMin(), x2 = xMax();
        return (x >= x1 && x <= x2) || close(x, x1) || close(x, x2);
    }

    protected int locale(double x) {
        int n = xValue.length;
        if (n < 2) return -1; // 健壮性检查

        // 处理左边界及以下
        if (x <= xValue[0]) {
            return 0;
        }
        // 处理右边界及以上
        if (x >= xValue[n - 1]) {
            return n - 2;
        }

        // 中间区间搜索
        for (int i = 0; i < n - 1; i++) {
            // 修改为左闭右开或确保覆盖端点
            if (x >= xValue[i] && x < xValue[i + 1]) {
                return i;
            }
        }
        return -1;
    }
}
