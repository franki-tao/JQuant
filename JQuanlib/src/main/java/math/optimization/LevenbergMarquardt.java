package math.optimization;

import math.Array;
import math.CommonUtil;
import math.Matrix;
import math.optimization.impl.LmdifCostFunction;
import math.optimization.impl.LmdifCostFunctionParams;
import math.optimization.impl.LmdifParams;
import math.optimization.impl.MinPack;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;

public class LevenbergMarquardt extends OptimizationMethod {
    private Problem currentProblem_;
    private Array initCostValues_;
    private Matrix initJacobian_;
    private int info_ = 0; // remove together with getInfo
    private double epsfcn_;
    private double xtol_;
    private double gtol_;
    private boolean useCostFunctionsJacobian_;

    public LevenbergMarquardt(double epsfcn, // 1.0e-8,
                              double xtol, // 1.0e-8,
                              double gtol, //1.0e-8,
                              boolean useCostFunctionsJacobian) {// = false)
        epsfcn_ = epsfcn;
        xtol_ = xtol;
        gtol_ = gtol;
        useCostFunctionsJacobian_ = useCostFunctionsJacobian;
    }

    /*! \deprecated Don't use this method; inspect the result of minimize instead.
                        Deprecated in version 1.36.
        */
    public int getInfo() {
        return info_;
    }

    /*! \deprecated Don't use this method; it is for internal use.
                        Deprecated in version 1.37.
        */
    public void fcn(int m, int n, double[] x, double[] fvec, int[] d) {
        fcn(m, n, x, fvec);
    }

    public void jacFcn(int m, int n, double[] x, double[] fjac, int[] d) {
        jacFcn(m, n, x, fjac);
    }

    private void fcn(int m, int n, double[] x, double[] fvec) {
        Array xt = new Array(n);
        xt.copy(x, 0, n);
        // constraint handling needs some improvement in the future:
        // starting point should not be close to a constraint violation
        if (currentProblem_.constraint().test(xt)) {
            Array tmp = currentProblem_.values(xt);
            for (int i = 0; i < tmp.size(); i++) {
                fvec[i] = tmp.get(i);
            }
        } else {
            for (int i = 0; i < initCostValues_.size(); i++) {
                fvec[i] = initCostValues_.get(i);
            }
        }
    }

    private void jacFcn(int m, int n, double[] x, double[] fjac) {
        Array xt = new Array(n);
        xt.copy(x, 0, n);
        // constraint handling needs some improvement in the future:
        // starting point should not be close to a constraint violation
        if (currentProblem_.constraint().test(xt)) {
            Matrix tmp = new Matrix(m, n, 0);
            currentProblem_.costFunction().jacobian(tmp, xt);
            Matrix tmpT = CommonUtil.transpose(tmp);
            for (int i = 0; i < tmpT.rows(); i++) {
                for (int j = 0; j < tmpT.cols(); j++) {
                    fjac[i*j + j] = tmpT.get(i, j);
                }
            }
            // std::copy (tmpT.begin(), tmpT.end(), fjac);
        } else {
            Matrix tmpT = CommonUtil.transpose(initJacobian_);
            for (int i = 0; i < tmpT.rows(); i++) {
                for (int j = 0; j < tmpT.cols(); j++) {
                    fjac[i*j + j] = tmpT.get(i, j);
                }
            }
            //std::copy (tmpT.begin(), tmpT.end(), fjac);
        }
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        P.reset();
        Array initX = P.currentValue();
        currentProblem_ = P;
        initCostValues_ = P.costFunction().values(initX);
        int m = initCostValues_.size();
        int n = initX.size();
        if (useCostFunctionsJacobian_) {
            initJacobian_ = new Matrix(m,n, 0);
            P.costFunction().jacobian(initJacobian_, initX);
        }
        Array xx = new Array(initX);
        double[] fvec = new double[m];
        double[] diag = new double[n];
//        std::unique_ptr<Real[]> fvec(new Real[m]);
//        std::unique_ptr<Real[]> diag(new Real[n]);
        int mode = 1;
        // magic number recommended by the documentation
        double factor = 100;
        // lmdif() evaluates cost function n+1 times for each iteration (technically, 2n+1
        // times if useCostFunctionsJacobian is true, but lmdif() doesn't account for that)
        int maxfev = endCriteria.maxIterations() * (n + 1);
        int nprint = 0;
        int info = 0;
        int nfev = 0;
        double[] fjac = new double[m*n];
        // std::unique_ptr<Real[]> fjac(new Real[m*n]);
        int ldfjac = m;
        int[] ipvt = new int[n];
        double[] qtf = new double[n];
        double[] wa1 = new double[n];
        double[] wa2 = new double[n];
        double[] wa3 = new double[n];
        double[] wa4 = new double[m];

//        std::unique_ptr<int[]> ipvt(new int[n]);
//        std::unique_ptr<Real[]> qtf(new Real[n]);
//        std::unique_ptr<Real[]> wa1(new Real[n]);
//        std::unique_ptr<Real[]> wa2(new Real[n]);
//        std::unique_ptr<Real[]> wa3(new Real[n]);
//        std::unique_ptr<Real[]> wa4(new Real[m]);
        // requirements; check here to get more detailed error messages.
        QL_REQUIRE(n > 0, "no variables given");
        QL_REQUIRE(m >= n,
                "less functions (" + m +
                        ") than available variables (" + n + ")");
        QL_REQUIRE(endCriteria.functionEpsilon() >= 0.0,
                "negative f tolerance");
        QL_REQUIRE(xtol_ >= 0.0, "negative x tolerance");
        QL_REQUIRE(gtol_ >= 0.0, "negative g tolerance");
        QL_REQUIRE(maxfev > 0, "null number of evaluations");

        // call lmdif to minimize the sum of the squares of m functions
        // in n variables by the Levenberg-Marquardt algorithm.
        LmdifCostFunction lmdifCostFunction = new LmdifCostFunction() {
            @Override
            public void value(LmdifCostFunctionParams params) {
                fcn(params.x1, params.x2, params.v1, params.v2);
            }
        };
//        MINPACK::LmdifCostFunction lmdifCostFunction =
//            [this](const auto m, const auto n, const auto x, const auto fvec, const auto iflag) {
//            this->fcn(m, n, x, fvec);
//        };

        LmdifCostFunction lmdifJacFunction = useCostFunctionsJacobian_ ? new LmdifCostFunction() {
            @Override
            public void value(LmdifCostFunctionParams params) {
                jacFcn(params.x1, params.x2, params.v1, params.v2);

            }
        } : null;
        LmdifParams params = new LmdifParams(m,n,xx.toArray(),fvec,endCriteria.functionEpsilon(),
                xtol_,
                gtol_,
                maxfev,
                epsfcn_,
                diag, mode, factor,
                nprint, info, nfev, fjac,
                ldfjac, ipvt, qtf,
                wa1, wa2, wa3, wa4,
                lmdifCostFunction,
                lmdifJacFunction);
        MinPack.lmdif(params);
//        MINPACK::lmdif(m, n, xx.begin(), fvec.get(),
//                endCriteria.functionEpsilon(),
//                xtol_,
//                gtol_,
//                maxfev,
//                epsfcn_,
//                diag.get(), mode, factor,
//                nprint, &info, &nfev, fjac.get(),
//                ldfjac, ipvt.get(), qtf.get(),
//                wa1.get(), wa2.get(), wa3.get(), wa4.get(),
//                lmdifCostFunction,
//                lmdifJacFunction);
        // for the time being
        info_ = params.info;
        // check requirements & endCriteria evaluation
        QL_REQUIRE(params.info != 0, "MINPACK: improper input parameters");
        QL_REQUIRE(params.info != 7, "MINPACK: xtol is too small. no further "+
                "improvement in the approximate "+
                "solution x is possible.");
        QL_REQUIRE(params.info != 8, "MINPACK: gtol is too small. fvec is "+
                "orthogonal to the columns of the "+
                "jacobian to machine precision.");

        EndCriteria.Type ecType = EndCriteria.Type.None;
        switch (params.info) {
            case 1:
            case 2:
            case 3:
            case 4:
                // 2 and 3 should be StationaryPoint, 4 a new gradient-related value,
                // but we keep StationaryFunctionValue for backwards compatibility.
                ecType = EndCriteria.Type.StationaryFunctionValue;
                break;
            case 5:
                ecType = EndCriteria.Type.MaxIterations;
                break;
            case 6:
                ecType = EndCriteria.Type.FunctionEpsilonTooSmall;
                break;
            default:
                QL_FAIL("unknown MINPACK result: " + params.info);
        }
        // set problem
        P.setCurrentValue(new Array(params.x));
        P.setFunctionValue(P.costFunction().value(P.currentValue()));

        return ecType;
    }
}
