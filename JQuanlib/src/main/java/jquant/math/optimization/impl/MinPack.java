package jquant.math.optimization.impl;

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

    /*     **********
     *
     *     subroutine lmpar
     *
     *     given an m by n matrix a, an n by n nonsingular diagonal
     *     matrix d, an m-vector b, and a positive number delta,
     *     the problem is to determine a value for the parameter
     *     par such that if x solves the system
     *
     *       a*x = b ,     sqrt(par)*d*x = 0 ,
     *
     *     in the least squares sense, and dxnorm is the euclidean
     *     norm of d*x, then either par is zero and
     *
     *       (dxnorm-delta) .le. 0.1*delta ,
     *
     *     or par is positive and
     *
     *       abs(dxnorm-delta) .le. 0.1*delta .
     *
     *     this subroutine completes the solution of the problem
     *     if it is provided with the necessary information from the
     *     qr factorization, with column pivoting, of a. that is, if
     *     a*p = q*r, where p is a permutation matrix, q has orthogonal
     *     columns, and r is an upper triangular matrix with diagonal
     *     elements of nonincreasing magnitude, then lmpar expects
     *     the full upper triangle of r, the permutation matrix p,
     *     and the first n components of (q transpose)*b. on output
     *     lmpar also provides an upper triangular matrix s such that
     *
     *        t   t           t
     *       p *(a *a + par*d*d)*p = s *s .
     *
     *     s is employed within lmpar and may be of separate interest.
     *
     *     only a few iterations are generally needed for convergence
     *     of the algorithm. if, however, the limit of 10 iterations
     *     is reached, then the output par will contain the best
     *     value obtained so far.
     *
     *     the subroutine statement is
     *
     *   subroutine lmpar(n,r,ldr,ipvt,diag,qtb,delta,par,x,sdiag,
     *            wa1,wa2)
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
     *   delta is a positive input variable which specifies an upper
     *     bound on the euclidean norm of d*x.
     *
     *   par is a nonnegative variable. on input par contains an
     *     initial estimate of the levenberg-marquardt parameter.
     *     on output par contains the final estimate.
     *
     *   x is an output array of length n which contains the least
     *     squares solution of the system a*x = b, sqrt(par)*d*x = 0,
     *     for the output par.
     *
     *   sdiag is an output array of length n which contains the
     *     diagonal elements of the upper triangular matrix s.
     *
     *   wa1 and wa2 are work arrays of length n.
     *
     *     subprograms called
     *
     *   minpack-supplied ... dpmpar,enorm,qrsolv
     *
     *   fortran-supplied ... dabs,dmax1,dmin1,dsqrt
     *
     *     argonne national laboratory. minpack project. march 1980.
     *     burton s. garbow, kenneth e. hillstrom, jorge j. more
     *
     *     **********
     */
    public static void lmpar(LmparParams params) {
        new Lmpar(params).run();
    }

    /*
     *     **********
     *
     *     subroutine lmdif
     *
     *     the purpose of lmdif is to minimize the sum of the squares of
     *     m nonlinear functions in n variables by a modification of
     *     the levenberg-marquardt algorithm. the user must provide a
     *     subroutine which calculates the functions. the jacobian is
     *     then calculated by a forward-difference approximation.
     *
     *     the subroutine statement is
     *
     *   subroutine lmdif(fcn,m,n,x,fvec,ftol,xtol,gtol,maxfev,epsfcn,
     *            diag,mode,factor,nprint,info,nfev,fjac,
     *            ldfjac,ipvt,qtf,wa1,wa2,wa3,wa4)
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
     *     the user wants to terminate execution of lmdif.
     *     in this case set iflag to a negative integer.
     *
     *   m is a positive integer input variable set to the number
     *     of functions.
     *
     *   n is a positive integer input variable set to the number
     *     of variables. n must not exceed m.
     *
     *   x is an array of length n. on input x must contain
     *     an initial estimate of the solution vector. on output x
     *     contains the final estimate of the solution vector.
     *
     *   fvec is an output array of length m which contains
     *     the functions evaluated at the output x.
     *
     *   ftol is a nonnegative input variable. termination
     *     occurs when both the actual and predicted relative
     *     reductions in the sum of squares are at most ftol.
     *     therefore, ftol measures the relative error desired
     *     in the sum of squares.
     *
     *   xtol is a nonnegative input variable. termination
     *     occurs when the relative error between two consecutive
     *     iterates is at most xtol. therefore, xtol measures the
     *     relative error desired in the approximate solution.
     *
     *   gtol is a nonnegative input variable. termination
     *     occurs when the cosine of the angle between fvec and
     *     any column of the jacobian is at most gtol in absolute
     *     value. therefore, gtol measures the orthogonality
     *     desired between the function vector and the columns
     *     of the jacobian.
     *
     *   maxfev is a positive integer input variable. termination
     *     occurs when the number of calls to fcn is at least
     *     maxfev by the end of an iteration.
     *
     *   epsfcn is an input variable used in determining a suitable
     *     step length for the forward-difference approximation. this
     *     approximation assumes that the relative errors in the
     *     functions are of the order of epsfcn. if epsfcn is less
     *     than the machine precision, it is assumed that the relative
     *     errors in the functions are of the order of the machine
     *     precision.
     *
     *   diag is an array of length n. if mode = 1 (see
     *     below), diag is internally set. if mode = 2, diag
     *     must contain positive entries that serve as
     *     multiplicative scale factors for the variables.
     *
     *   mode is an integer input variable. if mode = 1, the
     *     variables will be scaled internally. if mode = 2,
     *     the scaling is specified by the input diag. other
     *     values of mode are equivalent to mode = 1.
     *
     *   factor is a positive input variable used in determining the
     *     initial step bound. this bound is set to the product of
     *     factor and the euclidean norm of diag*x if nonzero, or else
     *     to factor itself. in most cases factor should lie in the
     *     interval (.1,100.). 100. is a generally recommended value.
     *
     *   nprint is an integer input variable that enables controlled
     *     printing of iterates if it is positive. in this case,
     *     fcn is called with iflag = 0 at the beginning of the first
     *     iteration and every nprint iterations thereafter and
     *     immediately prior to return, with x and fvec available
     *     for printing. if nprint is not positive, no special calls
     *     of fcn with iflag = 0 are made.
     *
     *   info is an integer output variable. if the user has
     *     terminated execution, info is set to the (negative)
     *     value of iflag. see description of fcn. otherwise,
     *     info is set as follows.
     *
     *     info = 0  improper input parameters.
     *
     *     info = 1  both actual and predicted relative reductions
     *           in the sum of squares are at most ftol.
     *
     *     info = 2  relative error between two consecutive iterates
     *           is at most xtol.
     *
     *     info = 3  conditions for info = 1 and info = 2 both hold.
     *
     *     info = 4  the cosine of the angle between fvec and any
     *           column of the jacobian is at most gtol in
     *           absolute value.
     *
     *     info = 5  number of calls to fcn has reached or
     *           exceeded maxfev.
     *
     *     info = 6  ftol is too small. no further reduction in
     *           the sum of squares is possible.
     *
     *     info = 7  xtol is too small. no further improvement in
     *           the approximate solution x is possible.
     *
     *     info = 8  gtol is too small. fvec is orthogonal to the
     *           columns of the jacobian to machine precision.
     *
     *   nfev is an integer output variable set to the number of
     *     calls to fcn.
     *
     *   fjac is an output m by n array. the upper n by n submatrix
     *     of fjac contains an upper triangular matrix r with
     *     diagonal elements of nonincreasing magnitude such that
     *
     *        t     t       t
     *       p *(jac *jac)*p = r *r,
     *
     *     where p is a permutation matrix and jac is the final
     *     calculated jacobian. column j of p is column ipvt(j)
     *     (see below) of the identity matrix. the lower trapezoidal
     *     part of fjac contains information generated during
     *     the computation of r.
     *
     *   ldfjac is a positive integer input variable not less than m
     *     which specifies the leading dimension of the array fjac.
     *
     *   ipvt is an integer output array of length n. ipvt
     *     defines a permutation matrix p such that jac*p = q*r,
     *     where jac is the final calculated jacobian, q is
     *     orthogonal (not stored), and r is upper triangular
     *     with diagonal elements of nonincreasing magnitude.
     *     column j of p is column ipvt(j) of the identity matrix.
     *
     *   qtf is an output array of length n which contains
     *     the first n elements of the vector (q transpose)*fvec.
     *
     *   wa1, wa2, and wa3 are work arrays of length n.
     *
     *   wa4 is a work array of length m.
     *
     *     subprograms called
     *
     *   user-supplied ...... fcn, jacFcn
     *
     *   minpack-supplied ... dpmpar,enorm,fdjac2,lmpar,qrfac
     *
     *   fortran-supplied ... dabs,dmax1,dmin1,dsqrt,mod
     *
     *     argonne national laboratory. minpack project. march 1980.
     *     burton s. garbow, kenneth e. hillstrom, jorge j. more
     *
     *     **********
     */
    public static void lmdif(LmdifParams params) {new Lmdif(params).run();}

}
