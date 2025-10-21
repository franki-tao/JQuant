package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.FunctionN;
import jquant.math.matrixutilities.impl.BiCGStabResult;

import static jquant.math.CommonUtil.*;

public class BiCGstab {
    protected FunctionN A_, M_;
    protected int maxIter_;
    protected double relTol_;

    public BiCGstab(FunctionN A,
                    int maxIter,
                    double relTol,
                    FunctionN preConditioner) {
        A_ = A;
        M_ = preConditioner;
        maxIter_ = maxIter;
        relTol_ = relTol;
    }

    // Array& x0 = Array()
    public BiCGStabResult solve(Array b, Array x0) {
        double bnorm2 = Norm2(b);
        if (bnorm2 == 0.0) {
            return new BiCGStabResult(0, 0d, b);
        }

        Array x = ((!x0.empty()) ? x0 : new Array(b.size(), 0.0));
        Array r = b.subtract(A_.value(x));

        Array rTld = r;
        Array pTld, s, sTld, t, p = null, v = null;
        double omega = 1.0;
        double rho, rhoTld = 1.0;
        double alpha = 0.0, beta;
        double error = Norm2(r) / bnorm2;

        int i;
        for (i = 0; i < maxIter_ && error >= relTol_; ++i) {
            rho = DotProduct(rTld, r);
            if (rho == 0.0 || omega == 0.0)
                break;

            if (i != 0) {
                beta = (rho / rhoTld) * (alpha / omega);
                p = r.add((p.subtract(v.mutiply(omega))).mutiply(beta));
                //p = r + beta * (p - omega * v);
            } else {
                p = r;
            }

            pTld = (M_ == null ? p : M_.value(p));
            v = A_.value(pTld);

            alpha = rho / DotProduct(rTld, v);
            s = r.subtract(v.mutiply(alpha));
            //s = r - alpha * v;
            if (Norm2(s) < relTol_ * bnorm2) {
                x = x.add(pTld.mutiply(alpha));
                // x += alpha * pTld;
                error = Norm2(s) / bnorm2;
                break;
            }

            sTld = (M_==null ? s : M_.value(s));
            t = A_.value(sTld);
            omega = DotProduct(t, s) / DotProduct(t, t);
            x = x.add(pTld.mutiply(alpha)).add(sTld.mutiply(omega));
            // x += alpha * pTld + omega * sTld;
            r = s.subtract(t.mutiply(omega));
            //r = s - omega * t;
            error = Norm2(r) / bnorm2;
            rhoTld = rho;
        }

        QL_REQUIRE(i < maxIter_, "max number of iterations exceeded");
        QL_REQUIRE(error < relTol_, "could not converge");

        return new BiCGStabResult(i, error, x);
    }
}
