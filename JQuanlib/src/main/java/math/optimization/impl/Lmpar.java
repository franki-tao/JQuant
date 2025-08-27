package math.optimization.impl;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;
import static math.optimization.impl.MinPack.*;

public class Lmpar {
    LmparParams params;

    public Lmpar(LmparParams params) {
        this.params = params;
    }

    int i, iter, ij, jj, j, jm1, jp1, k, l, nsing;
    double dxnorm, fp, gnorm, parc, parl, paru;
    double sum, temp;
    static double zero = 0.0;
    static double p1 = 0.1;
    static double p001 = 0.001;

    public void L220() {
        if (iter == 0)
            params.par = zero;
    }

    public void L150() {
        iter += 1;
        /*
         *    evaluate the function at the current value of par.
         */
        if (params.par == zero)
            params.par = dmax1(DWARF, p001 * paru);
        temp = sqrt(params.par);
        for (j = 0; j < params.n; j++)
            params.wa1[j] = temp * params.diag[j];
        QrsolvParams pp = new QrsolvParams(params.n, params.r, params.ldr, params.ipvt,
                params.wa1, params.qtb, params.x, params.sdiag, params.wa2);
        new Qrsolv(pp).run();
        // 变量回传
        params.r = pp.r;
        params.x = pp.x;
        params.sdiag = pp.sdiag;
        params.wa2 = pp.wa;


        for (j = 0; j < params.n; j++)
            params.wa2[j] = params.diag[j] * params.x[j];
        dxnorm = enorm(params.n, params.wa2);
        temp = fp;
        fp = dxnorm - params.delta;
        /*
         *    if the function is small enough, accept the current value
         *    of par. also test for the exceptional cases where parl
         *    is zero or the number of iterations has reached 10.
         */
        if ((abs(fp) <= p1 * params.delta) || ((parl == zero) && (fp <= temp) && (temp < zero)) ||
                (iter == 10)) {
            L220();
            return;
        }
        /*
         *    compute the newton correction.
         */
        for (j = 0; j < params.n; j++) {
            l = params.ipvt[j];
            params.wa1[j] = params.diag[l] * (params.wa2[l] / dxnorm);
        }
        jj = 0;
        for (j = 0; j < params.n; j++) {
            params.wa1[j] = params.wa1[j] / params.sdiag[j];
            temp = params.wa1[j];
            jp1 = j + 1;
            if (jp1 < params.n) {
                ij = jp1 + jj;
                for (i = jp1; i < params.n; i++) {
                    params.wa1[i] -= params.r[ij] * temp;
                    ij += 1; /* [i+ldr*j] */
                }
            }
            jj += params.ldr; /* ldr*j */
        }
        temp = enorm(params.n, params.wa1);
        parc = ((fp / params.delta) / temp) / temp;
        /*
         *    depending on the sign of the function, update parl or paru.
         */
        if (fp > zero)
            parl = dmax1(parl, params.par);
        if (fp < zero)
            paru = dmin1(paru, params.par);
        /*
         *    compute an improved estimate for par.
         */
        params.par = dmax1(parl, params.par + parc);
        /*
         *    end of an iteration.
         */
        L150();
        L220();
    }

    public void run() {
        /*
         *     compute and store in x the gauss-newton direction. if the
         *     jacobian is rank-deficient, obtain a least squares solution.
         */
        nsing = params.n;
        jj = 0;
        for (j = 0; j < params.n; j++) {
            params.wa1[j] = params.qtb[j];
            if ((params.r[jj] == zero) && (nsing == params.n))
                nsing = j;
            if (nsing < params.n)
                params.wa1[j] = zero;
            jj += params.ldr + 1; /* [j+ldr*j] */
        }
        if (nsing >= 1) {
            for (k = 0; k < nsing; k++) {
                j = nsing - k - 1;
                params.wa1[j] = params.wa1[j] / params.r[j + params.ldr * j];
                temp = params.wa1[j];
                jm1 = j - 1;
                if (jm1 >= 0) {
                    ij = params.ldr * j;
                    for (i = 0; i <= jm1; i++) {
                        params.wa1[i] -= params.r[ij] * temp;
                        ij += 1;
                    }
                }
            }
        }

        for (j = 0; j < params.n; j++) {
            l = params.ipvt[j];
            params.x[l] = params.wa1[j];
        }
        /*
         *     initialize the iteration counter.
         *     evaluate the function at the origin, and test
         *     for acceptance of the gauss-newton direction.
         */
        iter = 0;
        for (j = 0; j < params.n; j++)
            params.wa2[j] = params.diag[j] * params.x[j];
        dxnorm = enorm(params.n, params.wa2);
        fp = dxnorm - params.delta;
        if (fp <= p1 * params.delta) {
            L220();
            return;
        }
        /*
         *     if the jacobian is not rank deficient, the newton
         *     step provides a lower bound, parl, for the zero of
         *     the function. otherwise set this bound to zero.
         */
        parl = zero;
        if (nsing >= params.n) {
            for (j = 0; j < params.n; j++) {
                l = params.ipvt[j];
                params.wa1[j] = params.diag[l] * (params.wa2[l] / dxnorm);
            }
            jj = 0;
            for (j = 0; j < params.n; j++) {
                sum = zero;
                jm1 = j - 1;
                if (jm1 >= 0) {
                    ij = jj;
                    for (i = 0; i <= jm1; i++) {
                        sum += params.r[ij] * params.wa1[i];
                        ij += 1;
                    }
                }
                params.wa1[j] = (params.wa1[j] - sum) / params.r[j + params.ldr * j];
                jj += params.ldr; /* [i+ldr*j] */
            }
            temp = enorm(params.n, params.wa1);
            parl = ((fp / params.delta) / temp) / temp;
        }
        /*
         *     calculate an upper bound, paru, for the zero of the function.
         */
        jj = 0;
        for (j = 0; j < params.n; j++) {
            sum = zero;
            ij = jj;
            for (i = 0; i <= j; i++) {
                sum += params.r[ij] * params.qtb[i];
                ij += 1;
            }
            l = params.ipvt[j];
            params.wa1[j] = sum / params.diag[l];
            jj += params.ldr; /* [i+ldr*j] */
        }
        gnorm = enorm(params.n, params.wa1);
        paru = gnorm / params.delta;
        if (paru == zero)
            paru = DWARF / dmin1(params.delta, p1);
        /*
         *     if the input par lies outside of the interval (parl,paru),
         *     set par to the closer endpoint.
         */
        params.par = dmax1(params.par, parl);
        params.par = dmin1(params.par, paru);
        if (params.par == zero)
            params.par = gnorm / dxnorm;
        /*
         *     beginning of an iteration.
         */
        L150();
    }
}
