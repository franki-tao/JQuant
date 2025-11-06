package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.CommonUtil.Norm2;

/*! References:
    https://en.wikipedia.org/wiki/Householder_transformation
*/
public class HouseholderTransformation {
    private Array v_;
    public HouseholderTransformation(Array v) {
        v_ = v;
    }
    public Matrix getMatrix() {
        Array y = v_.div(Norm2(v_));
        int n = y.size();

        Matrix m = new Matrix(n, n, Double.NaN);
        for (int i=0; i < n; ++i)
            for (int j=0; j < n; ++j)
                m.set(i,j, ((i == j)? 1.0: 0.0) - 2*y.get(i)*y.get(j));
                // m[i][j] = ((i == j)? 1.0: 0.0) - 2*y[i]*y[j];
        return m;
    }

    public Array value(Array x) {
        return x.subtract(v_.mutiply((2.0*DotProduct(v_, x))));
    }
}
