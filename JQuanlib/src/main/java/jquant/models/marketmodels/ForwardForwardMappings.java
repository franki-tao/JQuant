package jquant.models.marketmodels;

import jquant.math.Matrix;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/forwardforwardmappings.hpp/.cpp} port.
 *
 * <p>Utility functions for mapping between forward rates of varying tenor.
 */
public final class ForwardForwardMappings {

    private ForwardForwardMappings() {
    }

    /** Returns the dg[i]/df[j] jacobian between forward rates with tenor
     *  multiplier and forward rates with tenor 1. */
    public static Matrix ForwardForwardJacobian(final CurveState cs,
                                                int multiplier,
                                                int offset) {
        int n = cs.numberOfRates();

        QL_REQUIRE(offset < multiplier, "offset  must be less than period in"
                + "  forward forward mappings");
        int k = (n - offset) / multiplier;

        final List<Double> tau = cs.rateTaus();

        Matrix jacobian = new Matrix(k, n, 0.0);

        int m = offset;
        for (int l = 0; l < k; ++l) {
            double df = cs.discountRatio(m, m + multiplier);
            double bigTau = cs.rateTimes().get(m + multiplier)
                    - cs.rateTimes().get(m);

            for (int r = 0; r < multiplier; ++r, ++m) {
                double value = df * tau.get(m) * cs.discountRatio(m + 1, m) - 1;
                value /= bigTau;
                jacobian.set(l, m, -value);
            }
        }

        return jacobian;
    }

    /** Returns the Y matrix to switch base forward rates with tenor multiplier
     *  and forward rates with tenor 1. */
    public static Matrix YMatrix(final CurveState cs,
                                 final List<Double> shortDisplacements,
                                 final List<Double> longDisplacements,
                                 int multiplier,
                                 int offset) {
        int n = cs.numberOfRates();

        QL_REQUIRE(offset < multiplier, "offset  must be less than period in"
                + "  forward forward mappings");
        int k = (n - offset) / multiplier;

        QL_REQUIRE(shortDisplacements.size() == n, "shortDisplacements must be of size"
                + " equal to number of rates");

        QL_REQUIRE(longDisplacements.size() == k, "longDisplacements must be of size"
                + " equal to (number of rates minus offset) divided by multiplier");

        Matrix jacobian = ForwardForwardJacobian(cs, multiplier, offset);

        for (int i = 0; i < k; ++i) {
            double tau = cs.rateTimes().get((i + 1) * multiplier + offset)
                    - cs.rateTimes().get(i * multiplier + offset);

            double longForward = (cs.discountRatio((i + 1) * multiplier + offset,
                    i * multiplier + offset) - 1.0) / tau;
            double longForwardDisplaced = longForward + longDisplacements.get(i);
            for (int j = 0; j < n; ++j) {
                double shortForward = cs.forwardRate(j);
                double shortForwardDisplaced = shortForward + shortDisplacements.get(j);
                jacobian.set(i, j, jacobian.get(i, j)
                        * shortForwardDisplaced / longForwardDisplaced);
            }
        }

        return jacobian;
    }

    /** Replaces curve state with curve state based on periodic subset of times. */
    public static LMMCurveState RestrictCurveState(final CurveState cs,
                                                   int multiplier,
                                                   int offset) {
        int n = cs.numberOfRates();

        QL_REQUIRE(offset < multiplier, "offset  must be less than period in"
                + "  forward forward mappings");
        int k = (n - offset) / multiplier;

        List<Double> times = new ArrayList<>();
        List<Double> discRatios = new ArrayList<>();

        for (int i = 0; i < k + 1; ++i) {
            times.add(cs.rateTimes().get(i * multiplier + offset));
            discRatios.add(cs.discountRatio(i * multiplier + offset, 0));
        }

        LMMCurveState newState = new LMMCurveState(times);
        newState.setOnDiscountRatios(discRatios);
        return newState;
    }
}
