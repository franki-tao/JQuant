package math.optimization;

import math.Array;
import math.Matrix;

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
    public void fcn(int m, int n, double x, double fvec, int d) {
        fcn(m, n, x, fvec);
    }

    public void jacFcn(int m, int n, double x, double fjac, int d) {
        jacFcn(m, n, x, fjac);
    }

    private void fcn(int m, int n, double x, double fvec) {
        //todo
    }

    private void jacFcn(int m, int n, double x, double fjac) {
        //todo
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        return null;
    }
}
