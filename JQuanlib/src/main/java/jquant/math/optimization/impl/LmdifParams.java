package jquant.math.optimization.impl;

public class LmdifParams {
    /*
    int m,
   int n,
   Real* x,
   Real* fvec,
   Real ftol,
   Real xtol,
   Real gtol,
   int maxfev,
   Real epsfcn,
   Real* diag,
   int mode,
   Real factor,
   int nprint,
   int* info,
   int* nfev,
   Real* fjac,
   int ldfjac,
   int* ipvt,
   Real* qtf,
   Real* wa1,
   Real* wa2,
   Real* wa3,
   Real* wa4,
   const ::MINPACK::LmdifCostFunction& fcn,
   const QuantLib::MINPACK::LmdifCostFunction& jacFcn
     */

    public int m;
    public int n;
    public double[] x;
    public double[] fvec;
    public double ftol;
    public double xtol;
    public double gtol;
    public int maxfev;
    public double epsfcn;
    public double[] diag;
    public int mode;
    public double factor;
    public int nprint;
    public int info;
    public int nfev;
    public double[] fjac;
    public int ldfjac;
    public int[] ipvt;
    public double[] qtf;
    public double[] wa1;
    public double[] wa2;
    public double[] wa3;
    public double[] wa4;
    public LmdifCostFunction fcn;
    public LmdifCostFunction jacFcn;

    public LmdifParams(int m, int n, double[] x, double[] fvec, double ftol, double xtol, double gtol,
                       int maxfev, double epsfcn, double[] diag, int mode, double factor, int nprint, int info,
                       int nfev, double[] fjac, int ldfjac, int[] ipvt, double[] qtf, double[] wa1, double[] wa2,
                       double[] wa3, double[] wa4, LmdifCostFunction fcn, LmdifCostFunction jacFcn) {
        this.m = m;
        this.n = n;
        this.x = x;
        this.fvec = fvec;
        this.ftol = ftol;
        this.xtol = xtol;
        this.gtol = gtol;
        this.maxfev = maxfev;
        this.epsfcn = epsfcn;
        this.diag = diag;
        this.mode = mode;
        this.factor = factor;
        this.nprint = nprint;
        this.info = info;
        this.nfev = nfev;
        this.fjac = fjac;
        this.ldfjac = ldfjac;
        this.ipvt = ipvt;
        this.qtf = qtf;
        this.wa1 = wa1;
        this.wa2 = wa2;
        this.wa3 = wa3;
        this.wa4 = wa4;
        this.fcn = fcn;
        this.jacFcn = jacFcn;
    }
}
