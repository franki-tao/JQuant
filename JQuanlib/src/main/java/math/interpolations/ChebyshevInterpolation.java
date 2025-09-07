package math.interpolations;

import math.Array;
import math.Function;
import math.Interpolation;
import math.interpolations.impl.LagrangeInterpolationImpl;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.M_PI;

// chebyshev interpolation between discrete Chebyshev nodes
/*! See S.A. Sarra: Chebyshev Interpolation: An Interactive Tour. */
public class ChebyshevInterpolation extends Interpolation {
    public enum PointsType {FirstKind, SecondKind};

    private Array x_;
    private Array y_;

    // 默认pointsType = SecondKind
    public ChebyshevInterpolation(Array y, PointsType pointsType) {
        x_ = nodes(y.size(), pointsType);
        y_ = y;
        impl_ = new LagrangeInterpolationImpl(x_.toArray(), y_.toArray());
        impl_.update();
    }

    // 默认pointsType = SecondKind
    public ChebyshevInterpolation(int n , Function f, PointsType pointsType) {
        Array tp = nodes(n, pointsType);
        new ChebyshevInterpolation(tp.transform(f), pointsType);
    }

    public void updateY(Array y) {
        QL_REQUIRE(y.size() == y_.size(),
                "interpolation override has the wrong length");

        for (int i=0; i<y.size(); i++) {
            y_.set(i, y.get(i));
        }
//        std::copy(y.begin(), y.end(), y_.begin());
    }

    public Array nodes() {
        return x_;
    }

    public Array nodes(int n, PointsType pointsType) {
        Array t = new Array(n);

        switch(pointsType) {
            case FirstKind:
                for (int i=0; i < n; ++i)
                    t.set(i, -Math.cos((i+0.5)*M_PI/n));
                    //t[i] = -std::cos((i+0.5)*M_PI/n);
                break;
            case SecondKind:
                for (int i=0; i < n; ++i)
                    t.set(i, -Math.cos(i*M_PI/(n-1)));
                    //t[i] = -std::cos(i*M_PI/(n-1));
                break;
            default:
                QL_FAIL("unknonw Chebyshev interpolation points type");
        }
        return t;
    }
}
