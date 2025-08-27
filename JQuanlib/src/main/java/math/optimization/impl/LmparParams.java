package math.optimization.impl;

public class LmparParams {
    /**
     * int n,
     *                Real* r,
     *                int ldr,
     *                int* ipvt,
     *                const Real* diag,
     *                Real* qtb,
     *                Real delta,
     *                Real* par,
     *                Real* x,
     *                Real* sdiag,
     *                Real* wa1,
     *                Real* wa2
     */
    public int n;
    public double[] r;
    public int ldr;
    public int[] ipvt;

    public double[] diag;
    public double[] qtb;
    public double delta;
    public double par;

    public double[] x;
    public double[] sdiag;
    public double[] wa1;
    public double[] wa2;

    public LmparParams(int n, double[] r, int ldr, int[] ipvt,
                       double[] diag, double[] qtb, double delta, double par, double[] x, double[] sdiag, double[] wa1, double[] wa2) {
        this.n = n;
        this.r = r;
        this.ldr = ldr;
        this.ipvt = ipvt;
        this.diag = diag;
        this.qtb = qtb;
        this.delta = delta;
        this.par = par;
        this.x = x;
        this.sdiag = sdiag;
        this.wa1 = wa1;
        this.wa2 = wa2;
    }
}
