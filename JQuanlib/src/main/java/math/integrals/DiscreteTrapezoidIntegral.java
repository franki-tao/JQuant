package math.integrals;

import java.util.List;

import static math.CommonUtil.QL_REQUIRE;

/*! References:
        Levy, D. Numerical Integration
        http://www2.math.umd.edu/~dlevy/classes/amsc466/lecture-notes/integration-chap.pdf
    */
public class DiscreteTrapezoidIntegral {
    public double value(List<Double> x, List<Double> f) {
        final int n = f.size();
        QL_REQUIRE(n == x.size(), "inconsistent size");

        double sum = 0.0;

        for (int i = 0; i < n - 1; ++i) {
            sum += (x.get(i + 1) - x.get(i)) * (f.get(i) + f.get(i + 1));
        }

        return 0.5 * sum;
    }
}
