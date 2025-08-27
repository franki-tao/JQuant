package math.optimization.impl;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

//QuantLib::MINPACK的改写
//考虑到函数参数过多，此处均给其封装成对象
public class MinPack {
    /* resolution of arithmetic */
    public static double MACHEP = 1.2e-16;
    /* smallest nonzero number */
    public static double DWARF = 1.0e-38;

    //计算欧几里得范数
    public static double enorm(int n, double[] x) {
        /*
         *     **********
         *
         *     function enorm
         *
         *     given an n-vector x, this function calculates the
         *     euclidean norm of x.
         *
         *     the euclidean norm is computed by accumulating the sum of
         *     squares in three different sums. the sums of squares for the
         *     small and large components are scaled so that no overflows
         *     occur. non-destructive underflows are permitted. underflows
         *     and overflows do not occur in the computation of the unscaled
         *     sum of squares for the intermediate components.
         *     the definitions of small, intermediate and large components
         *     depend on two constants, rdwarf and rgiant. the main
         *     restrictions on these constants are that rdwarf**2 not
         *     underflow and rgiant**2 not overflow. the constants
         *     given here are suitable for every known computer.
         *
         *     the function statement is
         *
         *   double precision function enorm(n,x)
         *
         *     where
         *
         *   n is a positive integer input variable.
         *
         *   x is an input array of length n.
         *
         *     subprograms called
         *
         *   fortran-supplied ... dabs,dsqrt
         *
         *     argonne national laboratory. minpack project. march 1980.
         *     burton s. garbow, kenneth e. hillstrom, jorge j. more
         *
         *     **********
         */
        int i;
        double agiant, floatn, s1, s2, s3, xabs, x1max, x3max;
        double ans, temp;
        double rdwarf = 3.834e-20;
        double rgiant = 1.304e19;
        double zero = 0.0;
        double one = 1.0;

        s1 = zero;
        s2 = zero;
        s3 = zero;
        x1max = zero;
        x3max = zero;
        floatn = n;
        agiant = rgiant / floatn;

        for (i = 0; i < n; i++) {
            xabs = abs(x[i]);
            if ((xabs > rdwarf) && (xabs < agiant)) {
                /*
                 *       sum for intermediate components.
                 */
                s2 += xabs * xabs;
                continue;
            }

            if (xabs > rdwarf) {
                /*
                 *          sum for large components.
                 */
                if (xabs > x1max) {
                    temp = x1max / xabs;
                    s1 = one + s1 * temp * temp;
                    x1max = xabs;
                } else {
                    temp = xabs / x1max;
                    s1 += temp * temp;
                }
                continue;
            }
            /*
             *          sum for small components.
             */
            if (xabs > x3max) {
                temp = x3max / xabs;
                s3 = one + s3 * temp * temp;
                x3max = xabs;
            } else {
                if (xabs != zero) {
                    temp = xabs / x3max;
                    s3 += temp * temp;
                }
            }
        }
        /*
         *     calculation of norm.
         */
        if (s1 != zero) {
            temp = s1 + (s2 / x1max) / x1max;
            ans = x1max * sqrt(temp);
            return (ans);
        }
        if (s2 != zero) {
            if (s2 >= x3max)
                temp = s2 * (one + (x3max / s2) * (x3max * s3));
            else
                temp = x3max * ((s2 / x3max) + (x3max * s3));
            ans = sqrt(temp);
        } else {
            ans = x3max * sqrt(s3);
        }
        return ans;
        /*
         *     last card of function enorm.
         */
    }

    /************************lmmisc.c*************************/

    public static double dmax1(double a, double b) {
        return Math.max(a, b);
    }

    public static double dmin1(double a, double b) {
        return Math.min(a, b);
    }

    public static int min0(int a, int b) {
        return Math.min(a, b);
    }

    public static int mod(int k, int m) {
        return (k % m);
    }

    public static void fdjac2(Fdjac2Params params) {
        /*
         *     **********
         *
         *     subroutine fdjac2
         *
         *     this subroutine computes a forward-difference approximation
         *     to the m by n jacobian matrix associated with a specified
         *     problem of m functions in n variables.
         *
         *     the subroutine statement is
         *
         *   subroutine fdjac2(fcn,m,n,x,fvec,fjac,ldfjac,iflag,epsfcn,wa)
         *
         *     where
         *
         *   fcn is the name of the user-supplied subroutine which
         *     calculates the functions. fcn must be declared
         *     in an external statement in the user calling
         *     program, and should be written as follows.
         *
         *     subroutine fcn(m,n,x,fvec,iflag)
         *     integer m,n,iflag
         *     double precision x(n),fvec(m)
         *     ----------
         *     calculate the functions at x and
         *     return this vector in fvec.
         *     ----------
         *     return
         *     end
         *
         *     the value of iflag should not be changed by fcn unless
         *     the user wants to terminate execution of fdjac2.
         *     in this case set iflag to a negative integer.
         *
         *   m is a positive integer input variable set to the number
         *     of functions.
         *
         *   n is a positive integer input variable set to the number
         *     of variables. n must not exceed m.
         *
         *   x is an input array of length n.
         *
         *   fvec is an input array of length m which must contain the
         *     functions evaluated at x.
         *
         *   fjac is an output m by n array which contains the
         *     approximation to the jacobian matrix evaluated at x.
         *
         *   ldfjac is a positive integer input variable not less than m
         *     which specifies the leading dimension of the array fjac.
         *
         *   iflag is an integer variable which can be used to terminate
         *     the execution of fdjac2. see description of fcn.
         *
         *   epsfcn is an input variable used in determining a suitable
         *     step length for the forward-difference approximation. this
         *     approximation assumes that the relative errors in the
         *     functions are of the order of epsfcn. if epsfcn is less
         *     than the machine precision, it is assumed that the relative
         *     errors in the functions are of the order of the machine
         *     precision.
         *
         *   wa is a work array of length m.
         *
         *     subprograms called
         *
         *   user-supplied ...... fcn
         *
         *   minpack-supplied ... dpmpar
         *
         *   fortran-supplied ... dabs,dmax1,dsqrt
         *
         *     argonne national laboratory. minpack project. march 1980.
         *     burton s. garbow, kenneth e. hillstrom, jorge j. more
         *
         **********
         */
        int i, j, ij;
        double eps, h, temp;
        double zero = 0.0;

        temp = dmax1(params.epsfcn, MACHEP);
        eps = sqrt(temp);
        ij = 0;
        for (j = 0; j < params.n; j++) {
            temp = params.x[j];
            h = eps * abs(temp);
            if (h == zero)
                h = eps;
            params.x[j] = temp + h;
            LmdifCostFunctionParams fp1 =
                    new LmdifCostFunctionParams(params.m, params.n, params.x, params.wa, params.iflag);
            params.fcn.value(fp1);
            params.backFc1(fp1);
            if (params.iflag < 0)
                return;
            params.x[j] = temp;
            for (i = 0; i < params.m; i++) {
                params.fjac[ij] = (params.wa[i] - params.fvec[i]) / h;
                ij += 1; /* fjac[i+m*j] */
            }
        }

        /*
         * last card of subroutine fdjac2.
         */
    }

    /*
     *     **********
     *
     *     subroutine qrfac
     *
     *     this subroutine uses householder transformations with column
     *     pivoting (optional) to compute a qr factorization of the
     *     m by n matrix a. that is, qrfac determines an orthogonal
     *     matrix q, a permutation matrix p, and an upper trapezoidal
     *     matrix r with diagonal elements of nonincreasing magnitude,
     *     such that a*p = q*r. the householder transformation for
     *     column k, k = 1,2,...,min(m,n), is of the form
     *
     *               t
     *       i - (1/u(k))*u*u
     *
     *     where u has zeros in the first k-1 positions. the form of
     *     this transformation and the method of pivoting first
     *     appeared in the corresponding linpack subroutine.
     *
     *     the subroutine statement is
     *
     *   subroutine qrfac(m,n,a,lda,pivot,ipvt,lipvt,rdiag,acnorm,wa)
     *
     *     where
     *
     *   m is a positive integer input variable set to the number
     *     of rows of a.
     *
     *   n is a positive integer input variable set to the number
     *     of columns of a.
     *
     *   a is an m by n array. on input a contains the matrix for
     *     which the qr factorization is to be computed. on output
     *     the strict upper trapezoidal part of a contains the strict
     *     upper trapezoidal part of r, and the lower trapezoidal
     *     part of a contains a factored form of q (the non-trivial
     *     elements of the u vectors described above).
     *
     *   lda is a positive integer input variable not less than m
     *     which specifies the leading dimension of the array a.
     *
     *   pivot is a logical input variable. if pivot is set true,
     *     then column pivoting is enforced. if pivot is set false,
     *     then no column pivoting is done.
     *
     *   ipvt is an integer output array of length lipvt. ipvt
     *     defines the permutation matrix p such that a*p = q*r.
     *     column j of p is column ipvt(j) of the identity matrix.
     *     if pivot is false, ipvt is not referenced.
     *
     *   lipvt is a positive integer input variable. if pivot is false,
     *     then lipvt may be as small as 1. if pivot is true, then
     *     lipvt must be at least n.
     *
     *   rdiag is an output array of length n which contains the
     *     diagonal elements of r.
     *
     *   acnorm is an output array of length n which contains the
     *     norms of the corresponding columns of the input matrix a.
     *     if this information is not needed, then acnorm can coincide
     *     with rdiag.
     *
     *   wa is a work array of length n. if pivot is false, then wa
     *     can coincide with rdiag.
     *
     *     subprograms called
     *
     *   minpack-supplied ... dpmpar,enorm
     *
     *   fortran-supplied ... dmax1,dsqrt,min0
     *
     *     argonne national laboratory. minpack project. march 1980.
     *     burton s. garbow, kenneth e. hillstrom, jorge j. more
     *
     *     **********
     */
    public static void qrfac(QrFacParams params) {
        new Qrfact(params).run();
    }

    /*
     *     **********
     *
     *     subroutine qrsolv
     *
     *     given an m by n matrix a, an n by n diagonal matrix d,
     *     and an m-vector b, the problem is to determine an x which
     *     solves the system
     *
     *       a*x = b ,     d*x = 0 ,
     *
     *     in the least squares sense.
     *
     *     this subroutine completes the solution of the problem
     *     if it is provided with the necessary information from the
     *     qr factorization, with column pivoting, of a. that is, if
     *     a*p = q*r, where p is a permutation matrix, q has orthogonal
     *     columns, and r is an upper triangular matrix with diagonal
     *     elements of nonincreasing magnitude, then qrsolv expects
     *     the full upper triangle of r, the permutation matrix p,
     *     and the first n components of (q transpose)*b. the system
     *     a*x = b, d*x = 0, is then equivalent to
     *
     *          t       t
     *       r*z = q *b ,  p *d*p*z = 0 ,
     *
     *     where x = p*z. if this system does not have full rank,
     *     then a least squares solution is obtained. on output qrsolv
     *     also provides an upper triangular matrix s such that
     *
     *        t   t       t
     *       p *(a *a + d*d)*p = s *s .
     *
     *     s is computed within qrsolv and may be of separate interest.
     *
     *     the subroutine statement is
     *
     *   subroutine qrsolv(n,r,ldr,ipvt,diag,qtb,x,sdiag,wa)
     *
     *     where
     *
     *   n is a positive integer input variable set to the order of r.
     *
     *   r is an n by n array. on input the full upper triangle
     *     must contain the full upper triangle of the matrix r.
     *     on output the full upper triangle is unaltered, and the
     *     strict lower triangle contains the strict upper triangle
     *     (transposed) of the upper triangular matrix s.
     *
     *   ldr is a positive integer input variable not less than n
     *     which specifies the leading dimension of the array r.
     *
     *   ipvt is an integer input array of length n which defines the
     *     permutation matrix p such that a*p = q*r. column j of p
     *     is column ipvt(j) of the identity matrix.
     *
     *   diag is an input array of length n which must contain the
     *     diagonal elements of the matrix d.
     *
     *   qtb is an input array of length n which must contain the first
     *     n elements of the vector (q transpose)*b.
     *
     *   x is an output array of length n which contains the least
     *     squares solution of the system a*x = b, d*x = 0.
     *
     *   sdiag is an output array of length n which contains the
     *     diagonal elements of the upper triangular matrix s.
     *
     *   wa is a work array of length n.
     *
     *     subprograms called
     *
     *   fortran-supplied ... dabs,dsqrt
     *
     *     argonne national laboratory. minpack project. march 1980.
     *     burton s. garbow, kenneth e. hillstrom, jorge j. more
     *
     *     **********
     */
    public static void qrsolv(QrsolvParams params) {
        new Qrsolv(params).run();
    }

}
