package jquant.math.statistics;

import jquant.math.Point;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! empirical-distribution risk measures
 * ! This class wraps a somewhat generic statistic tool and adds
 * a number of risk measures (e.g.: value-at-risk, expected
 * shortfall, etc.) based on the data distribution as reported by
 * the underlying statistic tool.
 * <p>
 * \todo add historical annualized volatility
 */
public class GenericRiskStatistics {
    public GeneralStatistics stat_;

    public GenericRiskStatistics(GeneralStatistics stat) {
        this.stat_ = stat;
    }

    /*! returns the variance of observations below the mean,
        \f[ \frac{N}{N-1}
            \mathrm{E}\left[ (x-\langle x \rangle)^2 \;|\;
                              x < \langle x \rangle \right]. \f]

        See Markowitz (1959).
    */
    public double semiVariance() {
        return regret(stat_.mean());
    }

    /*! returns the semi deviation, defined as the
        square root of the semi variance.
    */
    public double semiDeviation() {
        return Math.sqrt(semiVariance());
    }

    /*! returns the variance of observations below 0.0,
        \f[ \frac{N}{N-1}
            \mathrm{E}\left[ x^2 \;|\; x < 0\right]. \f]
    */
    public double downsideVariance() {
        return regret(0.0);
    }

    /*! returns the downside deviation, defined as the
        square root of the downside variance.
    */
    public double downsideDeviation() {
        return Math.sqrt(downsideVariance());
    }

    /*! returns the variance of observations below target,
        \f[ \frac{N}{N-1}
            \mathrm{E}\left[ (x-t)^2 \;|\;
                              x < t \right]. \f]

        See Dembo and Freeman, "The Rules Of Risk", Wiley (2001).
    */
    public double regret(double target) {
        // average over the range below the target
        Point<Double, Integer> result = stat_.expectationValue(x -> {
            double d = (x - target);
            return d * d;
        }, x -> {
            return x < target;
        });
        double x = result.getFirst();
        int N = result.getSecond();
        QL_REQUIRE(N > 1,
                "samples under target <= 1, unsufficient");
        return (N / (N - 1.0)) * x;
    }

    //! potential upside (the reciprocal of VAR) at a given percentile
    public double potentialUpside(double centile) {
        QL_REQUIRE(centile >= 0.9 && centile < 1.0,
                "percentile (" + centile + ") out of range [0.9, 1.0)");

        // potential upside must be a gain, i.e., floored at 0.0
        return Math.max(stat_.percentile(centile), 0.0);
    }

    //! value-at-risk at a given percentile
    public double valueAtRisk(double centile) {
        QL_REQUIRE(centile >= 0.9 && centile < 1.0,
                "percentile (" + centile + ") out of range [0.9, 1.0)");

        // must be a loss, i.e., capped at 0.0 and negated
        return -Math.min(stat_.percentile(1.0 - centile), 0.0);
    }

    //! expected shortfall at a given percentile
    /*! returns the expected loss in case that the loss exceeded
        a VaR threshold,

        \f[ \mathrm{E}\left[ x \;|\; x < \mathrm{VaR}(p) \right], \f]

        that is the average of observations below the
        given percentile \f$ p \f$.
        Also know as conditional value-at-risk.

        See Artzner, Delbaen, Eber and Heath,
        "Coherent measures of risk", Mathematical Finance 9 (1999)
    */
    public double expectedShortfall(double centile) {
        QL_REQUIRE(centile >= 0.9 && centile < 1.0,
                "percentile (" + centile + ") out of range [0.9, 1.0)");

        QL_REQUIRE(stat_.samples() != 0, "empty sample set");
        double target = -valueAtRisk(centile);
        Point<Double, Integer> result =
                stat_.expectationValue(x -> {
                            return x;
                        },
                        xi -> {
                            return xi < target;
                        });
        double x = result.getFirst();
        int N = result.getSecond();
        QL_REQUIRE(N != 0, "no data below the target");
        // must be a loss, i.e., capped at 0.0 and negated
        return -Math.min(x, 0.0);
    }

    /*! probability of missing the given target, defined as
        \f[ \mathrm{E}\left[ \Theta \;|\; (-\infty,\infty) \right] \f]
        where
        \f[ \Theta(x) = \left\{
            \begin{array}{ll}
            1 & x < t \\
            0 & x \geq t
            \end{array}
            \right. \f]
    */
    public double shortfall(double target) {
        QL_REQUIRE(stat_.samples() != 0, "empty sample set");
        return stat_.expectationValue(x -> {
            return x < target ? 1.0 : 0.0;
        }).getFirst();
    }

    /*! averaged shortfallness, defined as
        \f[ \mathrm{E}\left[ t-x \;|\; x<t \right] \f]
    */
    public double averageShortfall(double target) {
        Point<Double, Integer> result = stat_.expectationValue(
                xi -> {
                    return target - xi;
                },
                xi -> {
                    return xi < target;
                });
        double x = result.getFirst();
        int N = result.getSecond();
        QL_REQUIRE(N != 0, "no data below the target");
        return x;
    }
}
