package math.integrals;

import java.util.List;

import static math.CommonUtil.QL_REQUIRE;

public class DiscreteSimpsonIntegral {
    public double value(List<Double> x, List<Double> f) {
        final int n = f.size();
        QL_REQUIRE(n == x.size(), "inconsistent size");

        double sum = 0.0;

        for (int j = 0; j < n - 2; j += 2) {
            final double dxj = x.get(j + 1) - x.get(j);
            final double dxjp1 = x.get(j + 2) - x.get(j + 1);

            final double alpha = dxjp1 * (2 * dxj - dxjp1);
            final double dd = dxj + dxjp1;
            final double k = dd / (6 * dxjp1 * dxj);
            final double beta = dd * dd;
            final double gamma = dxj * (2 * dxjp1 - dxj);

            sum += k * (alpha * f.get(j) + beta * f.get(j + 1) + gamma * f.get(j + 2));
        }
        if ((n & 1) == 0) {
            sum += 0.5 * (x.get(n - 1) - x.get(n - 2)) * (f.get(n - 1) + f.get(n - 2));
        }

        return sum;
    }
}
