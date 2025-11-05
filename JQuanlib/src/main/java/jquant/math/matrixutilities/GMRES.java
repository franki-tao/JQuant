package jquant.math.matrixutilities;


import jquant.math.Array;
import jquant.math.ArrayFunc;
import jquant.math.CommonUtil;
import jquant.math.matrixutilities.impl.GMRESResult;

import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.*;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.squared;

/*! References:
    Saad, Yousef. 1996, Iterative methods for sparse linear systems,
    http://www-users.cs.umn.edu/~saad/books.html

    Dongarra et al. 1994,
    Templates for the Solution of Linear Systems: Building Blocks
    for Iterative Methods, 2nd Edition, SIAM, Philadelphia
    http://www.netlib.org/templates/templates.pdf

    Christian Kanzow
    Numerik linearer Gleichungssysteme (German)
    Chapter 6: GMRES und verwandte Verfahren
    http://bilder.buecher.de/zusatz/12/12950/12950560_lese_1.pdf
*/
public class GMRES {
    protected ArrayFunc A_, M_;
    protected int maxIter_;
    protected double relTol_;

    //default preConditioner = null
    public GMRES(ArrayFunc A, int maxIter, double relTol, ArrayFunc preConditioner) {
        A_ = A;
        M_ = preConditioner;
        maxIter_ = maxIter;
        relTol_ = relTol;
        QL_REQUIRE(maxIter_ > 0, "maxIter must be greater than zero");
    }

    // default x0 = Array()
    public GMRESResult solve(Array b, Array x0) {
        GMRESResult result = solveImpl(b, x0);

        QL_REQUIRE(result.back() < relTol_, "could not converge");

        return result;
    }

    //default x0 = Array()
    public GMRESResult solveWithRestart(int restart, Array b, Array x0) {
        GMRESResult result = solveImpl(b, x0);

        List<Double> errors = result.errors;

        for (int i = 0; i < restart - 1 && result.back() >= relTol_; ++i) {
            result = solveImpl(b, result.x);
            errors.addAll(result.errors);
            // errors.insert(errors.end(), result.errors.begin(), result.errors.end());
        }

        QL_REQUIRE(errors.get(errors.size() - 1) < relTol_, "could not converge");

        result.errors = errors;
        return result;
    }

    protected GMRESResult solveImpl(Array b, Array x0) {
        final double bn = Norm2(b);
        if (bn == 0.0) {
            //{ std::list<Real>(1, 0.0), b };
            return new GMRESResult(Arrays.asList(1d, 0d), b);
        }

        Array x = ((!x0.empty()) ? x0 : new Array(b.size(), 0.0));
        Array r = b.subtract(A_.value(x));//b - A_(x);

        final double g = Norm2(r);
        if (g / bn < relTol_) {
            //{ std::list<Real>(1, g/bn), x };
            return new GMRESResult(Arrays.asList(1d, g / bn), x);
        }
        List<Array> v = CommonUtil.ArrayInit(1, r.div(g));
        // std::vector<Array> v(1, r/g);
        List<Array> h = CommonUtil.ArrayInit(1, new Array(maxIter_, 0d));
        // std::vector<Array> h(1, Array(maxIter_, 0.0));
        List<Double> c = CommonUtil.ArrayInit(maxIter_ + 1);
        List<Double> s = CommonUtil.ArrayInit(maxIter_ + 1);
        List<Double> z = CommonUtil.ArrayInit(maxIter_ + 1);
        // std::vector<Real>  c(maxIter_+1), s(maxIter_+1), z(maxIter_+1);
        z.set(0, g);
        List<Double> errors = CommonUtil.ArrayInit(1, g / bn);
        // std::list<Real> errors(1, g/bn);
        for (int j = 0; j < maxIter_ && errors.get(errors.size() - 1) >= relTol_; ++j) {
            h.add(new Array(maxIter_, 0.0));
            // h.emplace_back(maxIter_, 0.0);
            Array w = A_.value(M_ == null ? v.get(j) : M_.value(v.get(j)));

            for (int i = 0; i <= j; ++i) {
                h.get(i).set(j, DotProduct(w, v.get(i)));
                // h[i][j] = DotProduct(w, v[i]);
                w = w.subtract(v.get(i).mutiply(h.get(i).get(j)));
                // w -= h[i][j] * v[i];
            }
            h.get(j + 1).set(j, Norm2(w));
            // h[j+1][j] = Norm2(w);

            if (h.get(j + 1).get(j) < QL_EPSILON * QL_EPSILON)
                break;
            v.add(w.div(h.get(j + 1).get(j)));
            // v.push_back(w / h[j+1][j]);

            for (int i = 0; i < j; ++i) {
                double h0 = c.get(i) * h.get(i).get(j) + s.get(i) * h.get(i + 1).get(j);
                double h1 = -s.get(i) * h.get(i).get(j) + c.get(i) * h.get(i + 1).get(j);
                h.get(i).set(j, h0);
                // h[i][j]   = h0;
                h.get(i + 1).set(j, h1);
                // h[i+1][j] = h1;
            }

            double nu = Math.sqrt(squared(h.get(j).get(j)) + squared(h.get(j + 1).get(j)));

            c.set(j, h.get(j).get(j) / nu);
            s.set(j, h.get(j + 1).get(j) / nu);
//            c[j] = h[j][j]/nu;
//            s[j] = h[j+1][j]/nu;

            h.get(j).set(j, nu);
            h.get(j + 1).set(j, 0d);
//            h[j][j]   = nu;
//            h[j+1][j] = 0.0;

            z.set(j + 1, -s.get(j) * z.get(j));
            z.set(j, c.get(j) * z.get(j));
//            z[j+1] = -s[j]*z[j];
//            z[j] = c[j] * z[j];

            errors.add(Math.abs(z.get(j + 1) / bn));
        }

        int k = v.size() - 1;

        Array y = new Array(k, 0.0);
        y.set(k - 1, z.get(k - 1) / h.get(k - 1).get(k - 1));
        // y[k-1]=z[k-1]/h[k-1][k-1];

        for (int i = k - 2; i >= 0; --i) {
            double tmp = 0.0;
            for (int j = i + 1; j < k; j++) {
                tmp += h.get(i).get(j) * y.get(j);
            }
            y.set(i, (z.get(i) - tmp) / h.get(i).get(i));
            // y[i] = (z[i] - std::inner_product(h[i].begin()+i+1, h[i].begin()+k, y.begin()+i+1, Real(0.0)))/h[i][i];
        }

        Array xm = new Array(x.size(), 0.0);
        for (int i = 0; i < k; i++) {
            xm = xm.add(v.get(i).mutiply(y.get(i)));
        }

        // Array xm = std::inner_product(v.begin(), v.begin()+k, y.begin(), Array(x.size(), Real(0.0)));
        xm = x.add(M_ == null ? xm : M_.value(xm));
        // xm = x + (!M_ ? xm : M_(xm));

        //{ errors, xm };
        return new GMRESResult(errors, xm);
    }


}
