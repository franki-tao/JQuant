package jquant.math.optimization.impl;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

public class Qrsolv {
    QrsolvParams params;

    int i, ij, ik, kk, j, jp1, k, kp1, l, nsing;
    double cos, cotan, qtbpj, sin, sum, tan, temp;
    static double zero = 0.0;
    static double p25 = 0.25;
    static double p5 = 0.5;


    public Qrsolv(QrsolvParams params) {
        this.params = params;
    }

    private void L150() {
        for (j = 0; j < params.n; j++) {
            l = params.ipvt[j];
            params.x[l] = params.wa[j];
        }
    }

    public void run() {
        /*
         *     copy r and (q transpose)*b to preserve input and initialize s.
         *     in particular, save the diagonal elements of r in x.
         */
        kk = 0;
        for (j = 0; j < params.n; j++) {
            ij = kk;
            ik = kk;
            for (i = j; i < params.n; i++) {
                params.r[ij] = params.r[ik];
                ij += 1;   /* [i+ldr*j] */
                ik += params.ldr; /* [j+ldr*i] */
            }
            params.x[j] = params.r[kk];
            params.wa[j] = params.qtb[j];
            kk += params.ldr + 1; /* j+ldr*j */
        }
        /*
         *     eliminate the diagonal matrix d using a givens rotation.
         */
        for (j = 0; j < params.n; j++) {
            /*
             *    prepare the row of d to be eliminated, locating the
             *    diagonal element using p from the qr factorization.
             */
            l = params.ipvt[j];
            if (params.diag[l] == zero) {
                // L90
                kk = j + params.ldr * j;
                params.sdiag[j] = params.r[kk];
                params.r[kk] = params.x[j];
                continue;
            }
            for (k = j; k < params.n; k++)
                params.sdiag[k] = zero;
            params.sdiag[j] = params.diag[l];
            /*
             *    the transformations to eliminate the row of d
             *    modify only a single element of (q transpose)*b
             *    beyond the first n, which is initially zero.
             */
            qtbpj = zero;
            for (k = j; k < params.n; k++) {
                /*
                 *       determine a givens rotation which eliminates the
                 *       appropriate element in the current row of d.
                 */
                if (params.sdiag[k] == zero)
                    continue;
                kk = k + params.ldr * k;
                if (abs(params.r[kk]) < abs(params.sdiag[k])) {
                    cotan = params.r[kk] / params.sdiag[k];
                    sin = p5 / sqrt(p25 + p25 * cotan * cotan);
                    cos = sin * cotan;
                } else {
                    tan = params.sdiag[k] / params.r[kk];
                    cos = p5 / sqrt(p25 + p25 * tan * tan);
                    sin = cos * tan;
                }
                /*
                 *       compute the modified diagonal element of r and
                 *       the modified element of ((q transpose)*b,0).
                 */
                params.r[kk] = cos * params.r[kk] + sin * params.sdiag[k];
                temp = cos * params.wa[k] + sin * qtbpj;
                qtbpj = -sin * params.wa[k] + cos * qtbpj;
                params.wa[k] = temp;
                /*
                 *       accumulate the tranformation in the row of s.
                 */
                kp1 = k + 1;
                if (params.n > kp1) {
                    ik = kk + 1;
                    for (i = kp1; i < params.n; i++) {
                        temp = cos * params.r[ik] + sin * params.sdiag[i];
                        params.sdiag[i] = -sin * params.r[ik] + cos * params.sdiag[i];
                        params.r[ik] = temp;
                        ik += 1; /* [i+ldr*k] */
                    }
                }
            }
            //L90:
            /*
             *    store the diagonal element of s and restore
             *    the corresponding diagonal element of r.
             */
            kk = j + params.ldr * j;
            params.sdiag[j] = params.r[kk];
            params.r[kk] = params.x[j];
        }
        /*
         *     solve the triangular system for z. if the system is
         *     singular, then obtain a least squares solution.
         */
        nsing = params.n;
        for (j = 0; j < params.n; j++) {
            if ((params.sdiag[j] == zero) && (nsing == params.n))
                nsing = j;
            if (nsing < params.n)
                params.wa[j] = zero;
        }
        if (nsing < 1) {
            L150();
            return;
        }

        for (k = 0; k < nsing; k++) {
            j = nsing - k - 1;
            sum = zero;
            jp1 = j + 1;
            if (nsing > jp1) {
                ij = jp1 + params.ldr * j;
                for (i = jp1; i < nsing; i++) {
                    sum += params.r[ij] * params.wa[i];
                    ij += 1; /* [i+ldr*j] */
                }
            }
            params.wa[j] = (params.wa[j] - sum) / params.sdiag[j];
        }
        L150();
        //L150:
        /*
         *     permute the components of z back to components of x.
         */
        /*
         *     last card of subroutine qrsolv.
         */
    }
}
