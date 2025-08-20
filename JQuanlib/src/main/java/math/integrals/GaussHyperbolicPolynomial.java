package math.integrals;

import static math.MathUtils.M_PI;
import static math.MathUtils.M_PI_2;

public class GaussHyperbolicPolynomial extends GaussianOrthogonalPolynomial {
    @Override
    public double alpha(int i) {
        return 0;
    }

    @Override
    public double beta(int i) {
        return i != 0 ? M_PI_2 * M_PI_2 * i * i : M_PI;
    }

    @Override
    public double w(double x) {
        return 1 / Math.cosh(x);
    }

    @Override
    public double mu_0() {
        return M_PI;
    }
}
