package jquant.methods.finitedifferences.meshers;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

/**
 * One-dimensional grid mesher combining two existing ones.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/finitedifferences/glued1dmesher.{hpp,cpp}}.
 */
public class Glued1dMesher extends Fdm1dMesher {

    private final boolean commonPoint_;

    public Glued1dMesher(Fdm1dMesher leftMesher,
                         Fdm1dMesher rightMesher) {
        super(leftMesher.locations().size()
                + rightMesher.locations().size()
                - (close(leftMesher.locations().get(
                        leftMesher.locations().size() - 1),
                rightMesher.locations().get(0)) ? 1 : 0));
        commonPoint_ = close(leftMesher.locations().get(
                        leftMesher.locations().size() - 1),
                rightMesher.locations().get(0));

        QL_REQUIRE(leftMesher.locations().get(
                        leftMesher.locations().size() - 1)
                        <= rightMesher.locations().get(0),
                "left meshers rightmost point ("
                        + leftMesher.locations().get(
                        leftMesher.locations().size() - 1)
                        + ") may not be greater than right meshers leftmost "
                        + "point (" + rightMesher.locations().get(0) + ")");

        for (int i = 0; i < leftMesher.locations().size(); ++i)
            locations_.set(i, leftMesher.locations().get(i));
        for (int i = 0; i < rightMesher.locations().size()
                - (commonPoint_ ? 1 : 0); ++i)
            locations_.set(leftMesher.locations().size() + i,
                    rightMesher.locations().get(
                            i + (commonPoint_ ? 1 : 0)));

        for (int i = 0; i < locations_.size() - 1; ++i) {
            final double d = locations_.get(i + 1) - locations_.get(i);
            dplus_.set(i, d);
            dminus_.set(i + 1, d);
        }
        dplus_.set(dplus_.size() - 1, Double.NaN);
        dminus_.set(0, Double.NaN);
    }
}
