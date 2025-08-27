package math.optimization.impl;

//Quantlib::MINPACK 中fdjac2的参数
public class Fdjac2Params {
    public int m;
    public int n;
    public double[] x;
    public double[] fvec;
    public double[] fjac;
    public int x1;
    public int iflag;
    public double epsfcn;
    public double[] wa;
    public LmdifCostFunction fcn;

    public Fdjac2Params(int m, int n, double[] x, double[] fvec, double[] fjac,
                        int x1, int iflag, double epsfcn, double[] wa, LmdifCostFunction fcn) {
        this.m = m;
        this.n = n;
        this.x = x;
        this.fvec = fvec;
        this.fjac = fjac;
        this.x1 = x1;
        this.iflag = iflag;
        this.epsfcn = epsfcn;
        this.wa = wa;
        this.fcn = fcn;
    }

    //数据回传
    public void backFc1(LmdifCostFunctionParams params) {
        this.x = params.v1;
        this.wa = params.v2;
        this.iflag = params.v3;
    }
}
