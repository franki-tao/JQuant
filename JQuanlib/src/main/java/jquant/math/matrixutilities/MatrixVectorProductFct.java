package jquant.math.matrixutilities;

import jquant.math.CommonUtil;
import jquant.math.Matrix;

import java.util.List;

public class MatrixVectorProductFct {
    private Matrix m_;
    public MatrixVectorProductFct(Matrix m) {
        m_ = m;
    }
    public List<Double> value(double t, List<Double> y) {
        List<Double> result = CommonUtil.ArrayInit(m_.rows());
        for (int i=0; i < result.size(); i++) {
            double tmp = 0d;
            for (int j = 0; j < y.size(); j++) {
                tmp += y.get(j) * m_.get(i,j);
            }
            result.set(i, tmp);
            // result[i] = std::inner_product(y.begin(), y.end(), m_.row_begin(i), Real(0.0));
        }
        return result;
    }
}
