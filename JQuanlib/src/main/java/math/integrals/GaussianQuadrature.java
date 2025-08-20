package math.integrals;

import math.Array;
import math.Function;
import math.Matrix;
import math.matrixutilities.TqrEigenDecomposition;

import static math.matrixutilities.TqrEigenDecomposition.EigenVectorCalculation.OnlyFirstRowEigenVector;
import static math.matrixutilities.TqrEigenDecomposition.ShiftStrategy.Overrelaxation;

public class GaussianQuadrature {
    protected Array x_;
    protected Array w_;

    public GaussianQuadrature(int n, GaussianOrthogonalPolynomial orthPoly) {
        x_ = new Array(n);
        w_ = new Array(n);
        Array e = new Array(n - 1);
        int i;
        for (i = 1; i < n; ++i) {
            x_.set(i, orthPoly.alpha(i));
            e.set(i - 1, Math.sqrt(orthPoly.beta(i)));
        }
        x_.set(0, orthPoly.alpha(0));

        TqrEigenDecomposition tqr = new TqrEigenDecomposition(
                x_, e,
                OnlyFirstRowEigenVector,
                Overrelaxation);

        x_ = tqr.eigenvalues();
        Matrix ev = tqr.eigenvectors();

        double mu_0 = orthPoly.mu_0();
        for (i = 0; i < n; ++i) {
            w_.set(i, mu_0 * ev.get(0, i) * ev.get(0, i) / orthPoly.w(x_.get(i)));
        }
    }

    public double value(Function f) {
        double sum = 0d;
        for (int i = order() - 1; i >= 0; --i) {
            sum += w_.get(i) * f.value(x_.get(i));
        }
        return sum;
    }

    public int order() {
        return x_.size();
    }

    public Array weights() {return w_;}
    public Array x() {return x_;}

    public static void main(String[] args) {
        GaussianQuadrature gaussianQuadrature = new GaussianQuadrature(10, new GaussChebyshev2ndPolynomial());
        Function f = new Function() {
            @Override
            public double value(double x) {
                return x*x;
            }
        };
        System.out.println(gaussianQuadrature.value(f));
    }
}
