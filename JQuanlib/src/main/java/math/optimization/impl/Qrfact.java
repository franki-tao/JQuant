package math.optimization.impl;

import math.Array;

import java.util.Arrays;

import static java.lang.Math.sqrt;
import static math.optimization.impl.MinPack.*;

public class Qrfact {
    QrFacParams params;

    /**
     * 初始化参数
     */
    int i, ij, jj, j, jp1, k, kmax, minmn;
    double ajnorm, sum, temp;
    static double zero = 0.0;
    static double one = 1.0;
    static double p05 = 0.05;

    public Qrfact(QrFacParams params) {
        this.params = params;
    }

    private void L40() {
        /*
         *    compute the householder transformation to reduce the
         *    j-th column of a to a multiple of the j-th unit vector.
         */
        jj = j + params.m * j;
        ajnorm = enorm(params.m - j, subArr(params.a, jj));
        if (ajnorm == zero) {
            L100();
            return;
        }
        if (params.a[jj] < zero)
            ajnorm = -ajnorm;
        ij = jj;
        for (i = j; i < params.m; i++) {
            params.a[ij] /= ajnorm;
            ij += 1; /* [i+m*j] */
        }
        params.a[jj] += one;
        /*
         *    apply the transformation to the remaining columns
         *    and update the norms.
         */
        jp1 = j + 1;
        if (jp1 < params.n) {
            for (k = jp1; k < params.n; k++) {
                sum = zero;
                ij = j + params.m * k;
                jj = j + params.m * j;
                for (i = j; i < params.m; i++) {
                    sum += params.a[jj] * params.a[ij];
                    ij += 1; /* [i+m*k] */
                    jj += 1; /* [i+m*j] */
                }
                temp = sum / params.a[j + params.m * j];
                ij = j + params.m * k;
                jj = j + params.m * j;
                for (i = j; i < params.m; i++) {
                    params.a[ij] -= temp * params.a[jj];
                    ij += 1; /* [i+m*k] */
                    jj += 1; /* [i+m*j] */
                }
                if ((params.pivot != 0) && (params.rdiag[k] != zero)) {
                    temp = params.a[j + params.m * k] / params.rdiag[k];
                    temp = dmax1(zero, one - temp * temp);
                    params.rdiag[k] *= sqrt(temp);
                    temp = params.rdiag[k] / params.wa[k];
                    if ((p05 * temp * temp) <= MACHEP) {
                        params.rdiag[k] = enorm(params.m - j - 1, subArr(params.a, jp1 + params.m * k));
                        params.wa[k] = params.rdiag[k];
                    }
                }
            }
        }
        L100();
    }

    private void L100() {
        params.rdiag[j] = -ajnorm;
    }

    public void run() {
        /*
         *     compute the initial column norms and initialize several arrays.
         */
        ij = 0;
        for (j = 0; j < params.n; j++) {
            params.acnorm[j] = enorm(params.m, subArr(params.a, ij));
            params.rdiag[j] = params.acnorm[j];
            params.wa[j] = params.rdiag[j];
            if (params.pivot != 0)
                params.ipvt[j] = j;
            ij += params.m; /* m*j */
        }
        /*
         *     reduce a to r with householder transformations.
         */
        minmn = min0(params.m, params.n);
        for (j = 0; j < minmn; j++) {
            if (params.pivot == 0) {
                L40();
                return;
            }

            /*
             *    bring the column of largest norm into the pivot position.
             */
            kmax = j;
            for (k = j; k < params.n; k++) {
                if (params.rdiag[k] > params.rdiag[kmax])
                    kmax = k;
            }
            if (kmax == j) {
                L40();
                return;
            }

            ij = params.m * j;
            jj = params.m * kmax;
            for (i = 0; i < params.m; i++) {
                temp = params.a[ij];  /* [i+m*j] */
                params.a[ij] = params.a[jj]; /* [i+m*kmax] */
                params.a[jj] = temp;
                ij += 1;
                jj += 1;
            }
            params.rdiag[kmax] = params.rdiag[j];
            params.wa[kmax] = params.wa[j];
            k = params.ipvt[j];
            params.ipvt[j] = params.ipvt[kmax];
            params.ipvt[kmax] = k;
            L40();
        }
    }


    //inner func
    private double[] subArr(double[] arr, int index) {
        return Arrays.copyOfRange(arr, index, arr.length);
    }
}
