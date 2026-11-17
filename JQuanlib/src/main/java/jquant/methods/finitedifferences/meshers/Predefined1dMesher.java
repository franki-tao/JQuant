package jquant.methods.finitedifferences.meshers;

import java.util.List;

import static jquant.math.MathUtils.NULL_REAL;

/**
 * One-dimensional mesher built from a given set of points.
 *
 * <p>Port of {@code ql/methods/finitedifferences/meshers/predefined1dmesher.hpp}.
 */
public class Predefined1dMesher extends Fdm1dMesher {
    public Predefined1dMesher(List<Double> x) {
        super(x.size());
        for (int i = 0; i < x.size(); ++i)
            locations_.set(i, x.get(i));

        dplus_.set(x.size() - 1, NULL_REAL);
        dminus_.set(0, NULL_REAL);
        for (int i = 0; i < x.size() - 1; ++i) {
            final double d = x.get(i + 1) - x.get(i);
            dplus_.set(i, d);
            dminus_.set(i + 1, d);
        }
    }
}
