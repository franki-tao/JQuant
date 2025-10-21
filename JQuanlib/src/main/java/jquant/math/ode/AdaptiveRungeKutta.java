package jquant.math.ode;

import jquant.math.CommonUtil;
import jquant.math.ReferencePkg;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.ArrayInit;
import static jquant.math.CommonUtil.QL_FAIL;

public class AdaptiveRungeKutta {
    private List<Double> yStart_;
    private double eps_, h1_, hmin_;
    private double a2 = 0.2, a3 = 0.3, a4 = 0.6, a5 = 1.0, a6 = 0.875, b21 = 0.2, b31, b32,
            b41 = 0.3, b42 = -0.9, b43 = 1.2, b51, b52 = 2.5, b53, b54, b61, b62, b63, b64,
            b65, c1, c3, c4, c6, dc1, dc3, dc4, dc5, dc6;
    private final double ADAPTIVERK_MAXSTP = 10000, ADAPTIVERK_TINY = 1.0E-30, ADAPTIVERK_SAFETY = 0.9,
            ADAPTIVERK_PGROW = -0.2, ADAPTIVERK_PSHRINK = -0.25,
            ADAPTIVERK_ERRCON = 1.89E-4;

    /*! The class is constructed with the following inputs:
        - eps       prescribed error for the solution
        - h1        start step size
        - hmin      smallest step size allowed
    */
    // default eps = 1.0e-6 ; h1 = 1.0e-4 ; hmin = 0.0
    public AdaptiveRungeKutta(final double eps, final double h1, final double hmin) {
        eps_ = (eps);
        h1_ = (h1);
        hmin_ = (hmin);
        b31 = (3.0 / 40.0);
        b32 = (9.0 / 40.0);
        b51 = (-11.0 / 54.0);
        b53 = (-70.0 / 27.0);
        b54 = (35.0 / 27.0);
        b61 = (1631.0 / 55296.0);
        b62 = (175.0 / 512.0);
        b63 = (575.0 / 13824.0);
        b64 = (44275.0 / 110592.0);
        b65 = (253.0 / 4096.0);
        c1 = (37.0 / 378.0);
        c3 = (250.0 / 621.0);
        c4 = (125.0 / 594.0);
        c6 = (512.0 / 1771.0);
        dc1 = (c1 - 2825.0 / 27648.0);
        dc3 = (c3 - 18575.0 / 48384.0);
        dc4 = (c4 - 13525.0 / 55296.0);
        dc5 = (-277.0 / 14336.0);
        dc6 = (c6 - 0.25);
    }

    /*! Integrate the ode from \f$ x1 \f$ to \f$ x2 \f$ with
    initial value condition \f$ f(x1)=y1 \f$.

    The ode is given by a function \f$ F: R \times K^n
    \rightarrow K^n \f$ as \f$ f'(x) = F(x,f(x)) \f$, $K=R,
    C$ */
    public List<Double> value(OdeFct ode, List<Double> y1, double x1, double x2) {
        int n = y1.size();
        List<Double> y = CommonUtil.clone(y1);
        List<Double> yScale = CommonUtil.ArrayInit(n);
        double x = x1;
        double h = h1_ * (x1 <= x2 ? 1 : -1);
        double hnext = 0, hdid = 0;

        for (int nstp = 1; nstp <= ADAPTIVERK_MAXSTP; nstp++) {
            List<Double> dydx = ode.value(x, y);
            for (int i = 0; i < n; i++)
                yScale.set(i, Math.abs(y.get(i)) + Math.abs(dydx.get(i) * h) + ADAPTIVERK_TINY);
            // yScale[i] = std::abs(y[i])+std::abs(dydx[i]*h)+ADAPTIVERK_TINY;
            if ((x + h - x2) * (x + h - x1) > 0.0)
                h = x2 - x;
            ReferencePkg<Double> xx = new ReferencePkg<>(x);
            ReferencePkg<Double> hdid_ = new ReferencePkg<>(hdid);
            ReferencePkg<Double> hnext_ = new ReferencePkg<>(hnext);
            rkqs(y, dydx, xx, h, eps_, yScale, hdid_, hnext_, ode);
            x = xx.getT();
            hdid = hdid_.getT();
            hnext = hnext_.getT();
            if ((x - x2) * (x2 - x1) >= 0.0)
                return y;

            if (Math.abs(hnext) <= hmin_)
                QL_FAIL("Step size (" + hnext + ") too small (" + hmin_ + " min) in AdaptiveRungeKutta");
            h = hnext;
        }
        QL_FAIL("Too many steps (" + ADAPTIVERK_MAXSTP + ") in AdaptiveRungeKutta");
        return new ArrayList<>();
    }

    public double value(OdeFct1d ode, double y1, double x1, double x2) {
        return value(new OdeFctWrapper(ode), CommonUtil.ArrayInit(1, y1), x1, x2).get(0);
    }

    private void rkqs(List<Double> y,
                      final List<Double> dydx,
                      ReferencePkg<Double> x,
                      double htry,
                      double eps,
                      final List<Double> yScale,
                      ReferencePkg<Double> hdid,
                      ReferencePkg<Double> hnext,
                      OdeFct derivs) {
        int n = y.size();
        double errmax, xnew;
        List<Double> yerr = CommonUtil.ArrayInit(n), ytemp = CommonUtil.ArrayInit(n);

        double h = htry;

        for (; ; ) {
            rkck(y, dydx, x.getT(), h, ytemp, yerr, derivs);
            errmax = 0.0;
            for (int i = 0; i < n; i++)
                errmax = Math.max(errmax, Math.abs(yerr.get(i) / yScale.get(i)));
            errmax /= eps;
            if (errmax > 1.0) {
                double htemp1 = ADAPTIVERK_SAFETY * h * Math.pow(errmax, ADAPTIVERK_PSHRINK);
                double htemp2 = h / 10;
                // These would be std::min and std::max, of course,
                // but VC++14 had problems inlining them and caused
                // the wrong results to be calculated.  The problem
                // seems to be fixed in update 3, but let's keep this
                // implementation for compatibility.
                double max_positive = Math.max(htemp1, htemp2);
                double max_negative = Math.min(htemp1, htemp2);
                h = ((h >= 0.0) ? max_positive : max_negative);
                xnew = x.getT() + h;
                if (xnew == x.getT())
                    QL_FAIL("Stepsize underflow (" + h + " at x = " + x
                            + ") in AdaptiveRungeKutta::rkqs");
                continue;
            } else {
                if (errmax > ADAPTIVERK_ERRCON)
                    hnext.setT(ADAPTIVERK_SAFETY * h * Math.pow(errmax, ADAPTIVERK_PGROW));
                else
                    hnext.setT(5.0 * h);
                hdid.setT(h);
                x.setT(x.getT() + h);
                for (int i = 0; i < n; i++)
                    y.set(i, ytemp.get(i));
                break;
            }
        }
    }

    private void rkck(final List<Double> y,
                      final List<Double> dydx,
                      double x,
                      double h,
                      List<Double> yout,
                      List<Double> yerr,
                      OdeFct derivs) {
        int n = y.size();
        List<Double> ak2 = ArrayInit(n);
        List<Double> ak3 = ArrayInit(n);
        List<Double> ak4 = ArrayInit(n);
        List<Double> ak5 = ArrayInit(n);
        List<Double> ak6 = ArrayInit(n);
        List<Double> ytemp = ArrayInit(n);

        // first step
        for (int i = 0; i < n; i++)
            ytemp.set(i, y.get(i) + b21 * h * dydx.get(i));
        // ytemp[i] = y[i] + b21 * h * dydx[i];

        // second step
        ak2 = derivs.value(x + a2 * h, ytemp);
        for (int i = 0; i < n; i++)
            ytemp.set(i, y.get(i) + h * (b31 * dydx.get(i) + b32 * ak2.get(i)));
        // ytemp[i] = y[i] + h * (b31 * dydx[i] + b32 * ak2[i]);

        // third step
        ak3 = derivs.value(x + a3 * h, ytemp);
        for (int i = 0; i < n; i++)
            ytemp.set(i, y.get(i) + h * (b41 * dydx.get(i) + b42 * ak2.get(i) + b43 * ak3.get(i)));
        // ytemp[i] = y[i] + h * (b41 * dydx[i] + b42 * ak2[i] + b43 * ak3[i]);

        // fourth step
        ak4 = derivs.value(x + a4 * h, ytemp);
        for (int i = 0; i < n; i++)
            ytemp.set(i, y.get(i) + h * (b51 * dydx.get(i) + b52 * ak2.get(i) + b53 * ak3.get(i) + b54 * ak4.get(i)));
        // ytemp[i] = y[i] + h * (b51 * dydx[i] + b52 * ak2[i] + b53 * ak3[i] + b54 * ak4[i]);

        // fifth step
        ak5 = derivs.value(x + a5 * h, ytemp);
        for (int i = 0; i < n; i++)
            ytemp.set(i, y.get(i) + h * (b61 * dydx.get(i) + b62 * ak2.get(i) + b63 * ak3.get(i) + b64 * ak4.get(i) + b65 * ak5.get(i)));
        // ytemp[i] = y[i] + h * (b61 * dydx[i] + b62 * ak2[i] + b63 * ak3[i] + b64 * ak4[i] + b65 * ak5[i]);

        // sixth step
        ak6 = derivs.value(x + a6 * h, ytemp);
        for (int i = 0; i < n; i++) {
            yout.set(i, y.get(i) + h * (c1 * dydx.get(i) + c3 * ak3.get(i) + c4 * ak4.get(i) + c6 * ak6.get(i)));
            // yout[i] = y[i] + h * (c1 * dydx[i] + c3 * ak3[i] + c4 * ak4[i] + c6 * ak6[i]);
            yerr.set(i, h * (dc1 * dydx.get(i) + dc3 * ak3.get(i) + dc4 * ak4.get(i) + dc5 * ak5.get(i) + dc6 * ak6.get(i)));
            // yerr[i] = h * (dc1 * dydx[i] + dc3 * ak3[i] + dc4 * ak4[i] + dc5 * ak5[i] + dc6 * ak6[i]);
        }
    }
}
