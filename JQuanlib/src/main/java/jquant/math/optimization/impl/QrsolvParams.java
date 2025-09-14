package jquant.math.optimization.impl;

public class QrsolvParams {
    /**
     * int n,
     *                 Real* r,
     *                 int ldr,
     *                 const int* ipvt,
     *                 const Real* diag,
     *                 const Real* qtb,
     *                 Real* x,
     *                 Real* sdiag,
     *                 Real* wa
     */
    public int n;
    public double[] r;
    public int ldr;
    public int[] ipvt;
    public double[] diag;
    public double[] qtb;
    public double[] x;
    public double[] sdiag;
    public double[] wa;

    public QrsolvParams(int n, double[] r, int ldr, int[] ipvt, double[] diag, double[] qtb, double[] x, double[] sdiag, double[] wa) {
        this.n = n;
        this.r = r;
        this.ldr = ldr;
        this.ipvt = ipvt;
        this.diag = diag;
        this.qtb = qtb;
        this.x = x;
        this.sdiag = sdiag;
        this.wa = wa;
    }
}
