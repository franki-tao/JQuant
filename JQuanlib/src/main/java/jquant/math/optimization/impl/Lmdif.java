package jquant.math.optimization.impl;

import static java.lang.Math.abs;
import static jquant.math.optimization.impl.MinPack.MACHEP;

public class Lmdif {
    LmdifParams params;

    int i, iflag, ij, jj, iter, j, l;
    double actred, delta = 0, dirder, fnorm, fnorm1, gnorm;
    double par, pnorm, prered, ratio;
    double sum, temp, temp1, temp2, temp3, xnorm = 0;
    static double one = 1.0;
    static double p1 = 0.1;
    static double p5 = 0.5;
    static double p25 = 0.25;
    static double p75 = 0.75;
    static double p0001 = 1.0e-4;
    static double zero = 0.0;

    public Lmdif(LmdifParams params) {
        this.params = params;
    }

    void tmp1() {
        LmdifCostFunctionParams lp = new LmdifCostFunctionParams(params.m,
                params.n, params.x, params.fvec, iflag);
        params.fcn.value(lp);
        params.x = lp.v1;
        params.fvec = lp.v2;
        iflag = lp.v3;
    }

    void tmp2() {
        QrFacParams qp = new QrFacParams(params.m, params.n, params.fjac, params.ldfjac,
                1, params.ipvt, params.n, params.wa1, params.wa2, params.wa3);
        Qrfact qrfact = new Qrfact(qp);
        qrfact.run();
        params.fjac = qp.a;
        params.ipvt = qp.ipvt;
        params.wa1 = qp.rdiag;
        params.wa2 = qp.acnorm;
        params.wa3 = qp.wa;

        //qrfac(m, n, fjac, ldfjac, 1, ipvt, n, wa1, wa2, wa3);
    }

    void tmp3() {
        LmparParams lp = new LmparParams(params.n, params.fjac, params.ldfjac, params.ipvt, params.diag,
                params.qtf, delta, par, params.wa1, params.wa2, params.wa3, params.wa4);
        Lmpar lmpar = new Lmpar(lp);
        lmpar.run();
        params.fjac = lp.r;
        params.ipvt = lp.ipvt;
        params.qtf = lp.qtb;
        par = lp.par;
        params.wa1 = lp.x;
        params.wa2 = lp.sdiag;
        params.wa3 = lp.wa1;
        params.wa4 = lp.wa2;
        //lmpar(n, fjac, ldfjac, ipvt, diag, qtf, delta, & par, wa1, wa2, wa3, wa4);
    }

    // 这一块逻辑是 L300 的内容，提取为 helper
    private void terminate(int finalInfo) {
        if (finalInfo < 0) params.info = finalInfo;
        iflag = 0;
        if (params.nprint > 0) {
            tmp1(); // print final state
        }
    }

    public void run() {
        params.info = 0;
        iflag = 0;
        params.nfev = 0;

        // --- Check Input Parameters ---
        if ((params.n <= 0) || (params.m < params.n) || (params.ldfjac < params.m)
                || (params.ftol < zero) || (params.xtol < zero) ||
                (params.gtol < zero) || (params.maxfev <= 0) || (params.factor <= zero)) {
            terminate(0);
            return;
        }

        if (params.mode == 2) {
            for (j = 0; j < params.n; j++) {
                if (params.diag[j] <= 0.0) {
                    terminate(0);
                    return;
                }
            }
        }

        // --- Initial Evaluation ---
        iflag = 1;
        tmp1(); // call fcn
        params.nfev = 1;
        if (iflag < 0) {
            terminate(iflag);
            return;
        }
        fnorm = MinPack.enorm(params.m, params.fvec);

        // --- Init Loop Variables ---
        par = zero;
        iter = 1;

        // ==========================================================
        // L30: Outer Loop (对应 C++ L30)
        // ==========================================================
        outerLoop:
        while (true) {

            // 1. Calculate Jacobian
            iflag = 2;
            if (params.jacFcn == null) {
                // ... Fdjac2 logic ...
                Fdjac2Params fp = new Fdjac2Params(params.m, params.n, params.x, params.fvec, params.fjac,
                        params.ldfjac, iflag, params.epsfcn, params.wa4, params.fcn);
                MinPack.fdjac2(fp);
                params.x = fp.x;
                params.fvec = fp.fvec;
                params.fjac = fp.fjac;
                iflag = fp.iflag;
                params.wa4 = fp.wa;
            } else {
                // ... User Jacobian logic ...
                LmdifCostFunctionParams lp = new LmdifCostFunctionParams(params.m,
                        params.n, params.x, params.fjac, iflag);
                params.jacFcn.value(lp);
                params.x = lp.v1;
                params.fjac = lp.v2;
                iflag = lp.v3;
            }
            params.nfev += params.n;

            if (iflag < 0) {
                terminate(iflag);
                return;
            }

            // Printing
            if (params.nprint > 0) {
                iflag = 0;
                if (MinPack.mod(iter - 1, params.nprint) == 0) {
                    tmp1();
                    if (iflag < 0) {
                        terminate(iflag);
                        return;
                    }
                }
            }

            // QR Factorization
            tmp2(); // qrfac

            // First iteration scaling
            if (iter == 1) {
                if (params.mode != 2) {
                    for (j = 0; j < params.n; j++) {
                        params.diag[j] = params.wa2[j];
                        if (params.wa2[j] == zero) params.diag[j] = one;
                    }
                }
                for (j = 0; j < params.n; j++)
                    params.wa3[j] = params.diag[j] * params.x[j];
                xnorm = MinPack.enorm(params.n, params.wa3);
                delta = params.factor * xnorm;
                if (delta == zero) delta = params.factor;
            }

            // Form (q transpose)*fvec
            for (i = 0; i < params.m; i++) params.wa4[i] = params.fvec[i];
            jj = 0;
            for (j = 0; j < params.n; j++) {
                temp3 = params.fjac[jj];
                if (temp3 != zero) {
                    sum = zero;
                    ij = jj;
                    for (i = j; i < params.m; i++) {
                        sum += params.fjac[ij] * params.wa4[i];
                        ij += 1;
                    }
                    temp = -sum / temp3;
                    ij = jj;
                    for (i = j; i < params.m; i++) {
                        params.wa4[i] += params.fjac[ij] * temp;
                        ij += 1;
                    }
                }
                params.fjac[jj] = params.wa1[j];
                jj += params.m + 1;
                params.qtf[j] = params.wa4[j];
            }

            // Compute Norm of scaled gradient
            gnorm = zero;
            if (fnorm != zero) {
                jj = 0;
                for (j = 0; j < params.n; j++) {
                    l = params.ipvt[j];
                    if (params.wa2[l] != zero) {
                        sum = zero;
                        ij = jj;
                        for (i = 0; i <= j; i++) {
                            sum += params.fjac[ij] * (params.qtf[i] / fnorm);
                            ij += 1;
                        }
                        gnorm = MinPack.dmax1(gnorm, abs(sum / params.wa2[l]));
                    }
                    jj += params.m;
                }
            }

            // Test convergence (Gradient Norm)
            if (gnorm <= params.gtol) params.info = 4;
            if (params.info != 0) {
                terminate(0);
                return;
            }

            // Rescale
            if (params.mode != 2) {
                for (j = 0; j < params.n; j++)
                    params.diag[j] = MinPack.dmax1(params.diag[j], params.wa2[j]);
            }

            // ==========================================================
            // L200: Inner Loop (对应 C++ L200)
            // ==========================================================
            innerLoop:
            while (true) {

                // Determine LM parameter
                tmp3(); // lmpar

                // Store direction p and x + p
                for (j = 0; j < params.n; j++) {
                    params.wa1[j] = -params.wa1[j];
                    params.wa2[j] = params.x[j] + params.wa1[j];
                    params.wa3[j] = params.diag[j] * params.wa1[j];
                }
                pnorm = MinPack.enorm(params.n, params.wa3);

                if (iter == 1) delta = MinPack.dmin1(delta, pnorm);

                // Evaluate function at x + p
                iflag = 1;
                LmdifCostFunctionParams lp_inner = new LmdifCostFunctionParams(params.m,
                        params.n, params.wa2, params.wa4, iflag);
                params.fcn.value(lp_inner);
                params.wa2 = lp_inner.v1;
                params.wa4 = lp_inner.v2;
                iflag = lp_inner.v3;

                params.nfev += 1;
                if (iflag < 0) {
                    terminate(iflag);
                    return;
                }
                fnorm1 = MinPack.enorm(params.m, params.wa4);

                // Scaled actual reduction
                actred = -one;
                if ((p1 * fnorm1) < fnorm) {
                    temp = fnorm1 / fnorm;
                    actred = one - temp * temp;
                }

                // Scaled predicted reduction
                jj = 0;
                for (j = 0; j < params.n; j++) {
                    params.wa3[j] = zero;
                    l = params.ipvt[j];
                    temp = params.wa1[l];
                    ij = jj;
                    for (i = 0; i <= j; i++) {
                        params.wa3[i] += params.fjac[ij] * temp;
                        ij += 1;
                    }
                    jj += params.m;
                }
                temp1 = MinPack.enorm(params.n, params.wa3) / fnorm;
                temp2 = (Math.sqrt(par) * pnorm) / fnorm;
                prered = temp1 * temp1 + (temp2 * temp2) / p5;
                dirder = -(temp1 * temp1 + temp2 * temp2);

                // Ratio
                ratio = zero;
                if (prered != zero) ratio = actred / prered;

                // Update step bound
                if (ratio <= p25) {
                    if (actred >= zero) temp = p5;
                    else temp = p5 * dirder / (dirder + p5 * actred);
                    if (((p1 * fnorm1) >= fnorm) || (temp < p1)) temp = p1;
                    delta = temp * MinPack.dmin1(delta, pnorm / p1);
                    par = par / temp;
                } else {
                    if ((par == zero) || (ratio >= p75)) {
                        delta = pnorm / p5;
                        par = p5 * par;
                    }
                }

                // Successful iteration?
                if (ratio >= p0001) {
                    for (j = 0; j < params.n; j++) {
                        params.x[j] = params.wa2[j];
                        params.wa2[j] = params.diag[j] * params.x[j];
                    }
                    for (i = 0; i < params.m; i++) params.fvec[i] = params.wa4[i];
                    xnorm = MinPack.enorm(params.n, params.wa2);
                    fnorm = fnorm1;
                    iter += 1;
                }

                // Tests for convergence
                if ((abs(actred) <= params.ftol) && (prered <= params.ftol) && (p5 * ratio <= one))
                    params.info = 1;
                if (delta <= params.xtol * xnorm)
                    params.info = 2;
                if ((abs(actred) <= params.ftol) && (prered <= params.ftol) && (p5 * ratio <= one) && (params.info == 2))
                    params.info = 3;

                if (params.info != 0) {
                    terminate(0);
                    return;
                }

                // Termination tests
                if (params.nfev >= params.maxfev) params.info = 5;
                if ((abs(actred) <= MACHEP) && (prered <= MACHEP) && (p5 * ratio <= one))
                    params.info = 6;
                if (delta <= MACHEP * xnorm) params.info = 7;
                if (gnorm <= MACHEP) params.info = 8;

                if (params.info != 0) {
                    terminate(0);
                    return;
                }

                // Repeat inner loop? (if ratio < 0.0001, iteration unsuccessful)
                if (ratio < p0001) {
                    continue innerLoop; // 对应 C++: goto L200
                }

                // End of inner loop, proceed to next outer loop iteration
                break innerLoop; // 跳出内层循环，回到 outerLoop (对应 C++: goto L30)
            } // end innerLoop

        } // end outerLoop
    }
}