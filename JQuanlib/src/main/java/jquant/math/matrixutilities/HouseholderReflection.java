package jquant.math.matrixutilities;

import jquant.math.Array;

import static jquant.math.CommonUtil.*;
import static jquant.math.MathUtils.QL_EPSILON;

public class HouseholderReflection {
    private Array e_;

    public HouseholderReflection(Array e) {
        e_ = e;
    }

    public Array reflectionVector(Array a) {
        double na = Norm2(a);
        QL_REQUIRE(na > 0, "vector of length zero given");

        double aDotE = DotProduct(a, e_);
        Array a1 = e_.mutiply(aDotE);
        Array a2 = a.subtract(a1);

        double eps = DotProduct(a2, a2) / (aDotE * aDotE);
        if (eps < QL_EPSILON * QL_EPSILON) {
            return new Array(a.size(), 0.0);
        } else if (eps < 1e-4) {
            double eps2 = eps * eps;
            double eps3 = eps * eps2;
            double eps4 = eps2 * eps2;
            //(a2 - a1*(eps/2.0 - eps2/8.0 + eps3/16.0 - 5/128.0*eps4)) / (aDotE*std::sqrt(eps + eps2/4.0 - eps3/8.0 + 5/64.0*eps4));
            return (a2.subtract(a1.mutiply(eps / 2.0 - eps2 / 8.0 + eps3 / 16.0 - 5 / 128.0 * eps4)))
                    .div(aDotE * Math.sqrt(eps + eps2 / 4.0 - eps3 / 8.0 + 5 / 64.0 * eps4));
        } else {
            Array c = a.subtract(e_.mutiply(na)); //a - na*e_;
            return c.div(Norm2(c));
        }
    }

    public Array value(Array a) {
        Array v = reflectionVector(a);
        return new HouseholderTransformation(v).value(a);
    }
}
