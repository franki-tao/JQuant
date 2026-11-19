package jquant.pricingengines.basket;

import jquant.math.Array;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

/**
 * Extracts spot, discount factors, variances and standard deviations from a
 * vector of Black-Scholes-Merton processes.
 *
 * <p>Ported from QuantLib {@code ql/pricingengines/basket/vectorbsmprocessextractor.{hpp,cpp}}.
 */
public class VectorBsmProcessExtractor {
    private final List<GeneralizedBlackScholesProcess> processes_;

    public VectorBsmProcessExtractor(
            List<GeneralizedBlackScholesProcess> processes) {
        processes_ = new ArrayList<>(processes);
    }

    private Array extractProcesses(
            Function<GeneralizedBlackScholesProcess, Double> f) {
        Array x = new Array(processes_.size());
        for (int i = 0; i < processes_.size(); ++i) {
            x.set(i, f.apply(processes_.get(i)));
        }
        return x;
    }

    public double getInterestRateDf(final Date maturityDate) {
        Array dr = extractProcesses(
                p -> p.riskFreeRate().getValue().discount(maturityDate));
        for (int i = 1; i < dr.size(); ++i) {
            QL_REQUIRE(close_enough(dr.get(i), dr.get(0)),
                    "interest rates need to be the same for all underlyings");
        }
        return dr.get(0);
    }

    public Array getSpot() {
        return extractProcesses(GeneralizedBlackScholesProcess::x0);
    }

    public Array getDividendYieldDf(final Date maturityDate) {
        return extractProcesses(
                p -> p.dividendYield().getValue().discount(maturityDate));
    }

    public Array getBlackVariance(final Date maturityDate) {
        return extractProcesses(
                p -> p.blackVolatility().getValue()
                        .blackVariance(maturityDate, p.x0()));
    }

    public Array getBlackStdDev(final Date maturityDate) {
        return extractProcesses(p -> {
            double maturity = p.blackVolatility().getValue()
                    .timeFromReference(maturityDate);
            return p.blackVolatility().getValue()
                    .blackVol(maturityDate, p.x0()) * Math.sqrt(maturity);
        });
    }
}
