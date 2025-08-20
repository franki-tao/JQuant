package math.integrals;

import static java.lang.Math.sqrt;

//! orthogonal polynomial for Gaussian quadratures
    /*! References:
        Gauss quadratures and orthogonal polynomials

        G.H. Gloub and J.H. Welsch: Calculation of Gauss quadrature rule.
        Math. Comput. 23 (1986), 221-230

        "Numerical Recipes in C", 2nd edition,
        Press, Teukolsky, Vetterling, Flannery,

        The polynomials are defined by the three-term recurrence relation
        \f[
        P_{k+1}(x)=(x-\alpha_k) P_k(x) - \beta_k P_{k-1}(x)
        \f]
        and
        \f[
        \mu_0 = \int{w(x)dx}
        \f]
    */
public abstract class GaussianOrthogonalPolynomial {

    public abstract double alpha(int i);

    public abstract double beta(int i);

    public abstract double w(double x);

    public abstract double mu_0();

    public double value(int n, double x) {
        if (n > 1) {
            return (x - alpha(n - 1)) * value(n - 1, x)
                    - beta(n - 1) * value(n - 2, x);
        } else if (n == 1) {
            return x - alpha(0);
        }

        return 1;
    }

    public double weightedValue(int n, double x) {
        return sqrt(w(x)) * value(n, x);
    }
}
