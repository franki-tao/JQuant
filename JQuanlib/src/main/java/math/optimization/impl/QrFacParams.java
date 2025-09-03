package math.optimization.impl;

public class QrFacParams {
    /*
    int m,
               int n,
               Real* a,
               int,
               int pivot,
               int* ipvt,
               int,
               Real* rdiag,
               Real* acnorm,
               Real* wa
     */
    public int m;

    public int n;

    public double[] a;

    public int x1;

    public int pivot;

    public int[] ipvt;

    public int x2;

    public double[] rdiag;

    public double[] acnorm;

    public double[] wa;

    public QrFacParams(int m, int n, double[] a, int x1, int pivot, int[] ipvt, int x2,
                       double[] rdiag, double[] acnorm, double[] wa) {
        this.m = m;
        this.n = n;
        this.a = a;
        this.x1 = x1;
        this.pivot = pivot;
        this.ipvt = ipvt;
        this.x2 = x2;
        this.rdiag = rdiag;
        this.acnorm = acnorm;
        this.wa = wa;
    }
}