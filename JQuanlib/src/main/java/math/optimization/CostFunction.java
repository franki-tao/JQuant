package math.optimization;

import math.Array;
import math.Matrix;

public abstract class CostFunction {
    public abstract Array values(Array x);

    public double value(Array x) {
        Array v = new Array(x);
        double tmp = 0;
        for (int i = 0; i < v.size(); i++) {
            tmp += v.get(i) * v.get(i);
        }
        return Math.sqrt(tmp / v.size());
    }

    public void gradient(Array grad, final Array x) {
        double eps = finiteDifferenceEpsilon(), fp, fm;
        Array xx = new Array(x);
        for (int i = 0; i < x.size(); i++) {
            xx.addEq(i, eps);
            fp = value(xx);
            xx.subtractEq(i, 2.0 * eps);
            fm = value(xx);
            grad.set(i, 0.5 * (fp - fm) / eps);
            xx.set(i, x.get(i));
        }
    }

    public double valueAndGradient(Array grad, final Array x) {
        gradient(grad, x);
        return value(x);
    }

    public void jacobian(Matrix jac, final Array x) {
        double eps = finiteDifferenceEpsilon();
        Array xx = new Array(x), fp, fm;
        for (int i = 0; i < x.size(); ++i) {
            xx.addEq(i, eps);
            fp = values(xx);
            xx.subtractEq(i, 2.0 * eps);
            fm = values(xx);
            for (int j = 0; j < fp.size(); ++j) {
                jac.set(j, i, 0.5 * (fp.get(j) - fm.get(j)) / eps);
            }
            xx.set(i, x.get(i));
        }
    }

    public Array valuesAndJacobian(Matrix jac,
                                   final Array x) {
        jacobian(jac, x);
        return values(x);
    }

    public double finiteDifferenceEpsilon() {
        return 1e-8;
    }
}
