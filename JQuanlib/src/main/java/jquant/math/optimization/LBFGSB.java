package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.ReferencePkg;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.CommonUtil.Norm2;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;

//! Limited-memory BFGS for bound-constrained optimization (L-BFGS-B)
/*! Faithful port of QuantLib's lbfgsb.cpp (commit cf74c9f), itself a
    faithful implementation of the algorithm of Byrd, Lu, Nocedal and
    Zhu, "A Limited Memory Algorithm for Bound Constrained
    Optimization", SIAM J. Sci. Comput. 16(5):1190-1208, 1995.

    Coordinates whose bound is +/- DBL_MAX are treated as unbounded, so
    with no active bounds the method reduces to plain limited-memory
    BFGS. Explicit bounds may instead be passed to the constructor,
    overriding the constraint.

    \ingroup optimizers
*/
public class LBFGSB extends OptimizationMethod {

    // official: const Real INF = QL_MAX_REAL;
    private static final double INF = QL_MAX_REAL;

    // A bound is "absent" when it equals +/- DBL_MAX (the value returned
    // by the default Constraint and by NoConstraint). The 0.5 factor
    // guards against overflow in subsequent arithmetic.
    private static boolean noUpper(double u) {
        return u >= 0.5 * INF;
    }

    private static boolean noLower(double l) {
        return l <= -0.5 * INF;
    }

    // Compact limited-memory representation of the BFGS Hessian
    // approximation B = theta I - W M W^T (Byrd et al. 1995, eq. 3.5).
    private static final class CompactRep {
        Matrix W;      // n x 2col, [ Y | theta S ]
        Matrix M;      // 2col x 2col, inverse of the middle matrix
        double theta = 1.0;
        int col = 0;
    }

    private static CompactRep buildCompactRep(final List<Array> S,
                                              final List<Array> Y,
                                              double theta) {
        CompactRep rep = new CompactRep();

        rep.theta = theta;
        rep.col = S.size();
        final int col = rep.col;

        if (col == 0)
            return rep;

        final int n = S.get(0).size();
        Matrix W = new Matrix(n, 2 * col, 0.0);

        for (int j = 0; j < col; ++j)
            for (int i = 0; i < n; ++i) {
                W.set(i, j, Y.get(j).get(i));
                W.set(i, col + j, theta * S.get(j).get(i));
            }

        // small Gram matrices S^T S and S^T Y
        Matrix StS = new Matrix(col, col, 0.0);
        Matrix StY = new Matrix(col, col, 0.0);

        for (int i = 0; i < col; ++i)
            for (int j = 0; j < col; ++j) {
                StS.set(i, j, DotProduct(S.get(i), S.get(j)));
                StY.set(i, j, DotProduct(S.get(i), Y.get(j)));
            }

        // middle matrix [ -D  L^T ; L  theta S^T S ] with D = diag(s_i.y_i)
        // and L the strictly lower part of S^T Y.
        Matrix mid = new Matrix(2 * col, 2 * col, 0.0);

        for (int i = 0; i < col; ++i) {
            mid.set(i, i, -StY.get(i, i)); // -D

            for (int j = 0; j < col; ++j) {
                if (i > j)
                    mid.set(col + i, j, StY.get(i, j)); // L
                if (j > i)
                    mid.set(i, col + j, StY.get(j, i)); // L^T

                mid.set(col + i, col + j, theta * StS.get(i, j));
            }
        }

        rep.W = W;
        rep.M = mid.inverse();
        return rep;
    }

    private static Array wRow(final Matrix W, int i) {
        Array r = new Array(W.cols());

        for (int k = 0; k < r.size(); ++k)
            r.set(k, W.get(i, k));

        return r;
    }

    // row vector times matrix: r[j] = sum_i row[i] * W[i][j]
    private static Array rowTimesMatrix(final Array row, final Matrix W) {
        Array r = new Array(W.cols(), 0.0);

        for (int i = 0; i < W.rows(); ++i)
            for (int j = 0; j < W.cols(); ++j)
                r.set(j, r.get(j) + row.get(i) * W.get(i, j));

        return r;
    }

    private static final class CauchyResult {
        Array xcp;
        Array c;
        boolean[] isFree;
    }

    // Generalized Cauchy point (Byrd et al. 1995, Algorithm CP).
    private static CauchyResult generalizedCauchyPoint(final Array x,
                                                       final Array g,
                                                       final Array lo,
                                                       final Array hi,
                                                       final CompactRep rep) {
        final int n = x.size();
        final int m2 = 2 * rep.col;

        CauchyResult result = new CauchyResult();
        Array xcp = new Array(x);
        boolean[] isFree = new boolean[n];
        java.util.Arrays.fill(isFree, true);

        Array t = new Array(n);
        Array d = new Array(n, 0.0);
        List<Integer> brk = new ArrayList<>(); // indices with a positive breakpoint
        for (int i = 0; i < n; ++i) {
            double ti;

            if (g.get(i) < 0.0)
                ti = noUpper(hi.get(i)) ? INF : (x.get(i) - hi.get(i)) / g.get(i);
            else if (g.get(i) > 0.0)
                ti = noLower(lo.get(i)) ? INF : (x.get(i) - lo.get(i)) / g.get(i);
            else
                ti = INF;

            t.set(i, ti);

            if (ti <= 0.0) {
                // already sitting on the bound the gradient pushes into
                d.set(i, 0.0);
                isFree[i] = false;
            } else {
                d.set(i, -g.get(i));
                brk.add(i);
            }
        }

        Array c = new Array(m2, 0.0);
        if (brk.isEmpty()) { // every variable is pinned
            result.xcp = xcp;
            result.c = c;
            result.isFree = isFree;
            return result;
        }

        brk.sort((a, b) -> Double.compare(t.get(a), t.get(b)));

        Array p = (rep.col > 0) ? rowTimesMatrix(d, rep.W) : new Array(0);

        double fp = -DotProduct(d, d); // first derivative of the model, m'(0)
        double fpp = -rep.theta * fp;  // second derivative
        if (rep.col > 0)
            fpp -= DotProduct(p, rep.M.mutiply(p));
        double fppFloor = QL_EPSILON * (fpp > 0.0 ? fpp : 1.0);

        double dtMin = (fpp > 0.0) ? -fp / fpp : 0.0;

        double tOld = 0.0;
        int ptr = 0;
        int b = brk.get(ptr);
        double tb = t.get(b);
        double dt = tb;
        boolean exhausted = false;

        while (dtMin >= dt && tb < INF) {
            // pin variable b at the bound it has reached
            double xcpb = (d.get(b) > 0.0) ? hi.get(b)
                    : (d.get(b) < 0.0 ? lo.get(b) : x.get(b));
            xcp.set(b, xcpb);
            isFree[b] = false;
            double zb = xcpb - x.get(b);
            double gb = g.get(b);

            if (rep.col > 0) {
                c = c.add(p.mutiply(dt));

                Array wb = wRow(rep.W, b);
                Array Mc = rep.M.mutiply(c);
                Array Mp = rep.M.mutiply(p);
                Array Mwb = rep.M.mutiply(wb);

                fp += dt * fpp + gb * gb + rep.theta * gb * zb - gb * DotProduct(wb, Mc);
                fpp += -rep.theta * gb * gb - 2.0 * gb * DotProduct(wb, Mp)
                        - gb * gb * DotProduct(wb, Mwb);
                p = p.add(wb.mutiply(gb));
            } else {
                fp += dt * fpp + gb * gb + rep.theta * gb * zb;
                fpp += -rep.theta * gb * gb;
            }

            if (fpp < fppFloor)
                fpp = fppFloor;

            d.set(b, 0.0);
            dtMin = (fpp > 0.0) ? -fp / fpp : 0.0;
            tOld = tb;

            if (++ptr >= brk.size()) {
                exhausted = true;
                break;
            }

            b = brk.get(ptr);
            tb = t.get(b);
            dt = tb - tOld;
        }

        // advance the still-free variables along the final segment
        if (exhausted)
            dtMin = 0.0;

        dtMin = Math.max(dtMin, 0.0);
        tOld += dtMin;

        for (int i = 0; i < n; ++i)
            if (isFree[i])
                xcp.set(i, x.get(i) + tOld * d.get(i));

        if (rep.col > 0)
            c = c.add(p.mutiply(dtMin));

        result.xcp = xcp;
        result.c = c;
        result.isFree = isFree;
        return result;
    }

    // Subspace minimization by the direct primal method (Byrd et al. 1995,
    // section 5.1).
    private static Array subspaceMinimization(final Array x,
                                              final Array g,
                                              final Array xcp,
                                              final Array c,
                                              final Array lo,
                                              final Array hi,
                                              final CompactRep rep,
                                              final boolean[] isFree) {
        final int n = x.size();
        List<Integer> freeIdx = new ArrayList<>();

        for (int i = 0; i < n; ++i)
            if (isFree[i])
                freeIdx.add(i);

        final int nf = freeIdx.size();

        Array xbar = new Array(xcp);
        if (nf == 0)
            return xbar;

        final double theta = rep.theta;
        final int m2 = 2 * rep.col;

        // reduced gradient of the model at the Cauchy point:
        // r = [ g + theta (xcp - x) - W M c ] restricted to free vars
        Array WMc = new Array(n, 0.0);
        if (rep.col > 0)
            WMc = rep.W.mutiply(rep.M.mutiply(c));

        Array r = new Array(nf);
        for (int k = 0; k < nf; ++k) {
            int i = freeIdx.get(k);
            r.set(k, g.get(i) + theta * (xcp.get(i) - x.get(i)) - WMc.get(i));
        }

        Array dhat = new Array(nf);
        if (rep.col == 0) {
            for (int k = 0; k < nf; ++k)
                dhat.set(k, -r.get(k) / theta);
        } else {
            // v = M (W_free^T r)
            Array wtr = new Array(m2, 0.0);

            for (int k = 0; k < nf; ++k) {
                int i = freeIdx.get(k);

                for (int j = 0; j < m2; ++j)
                    wtr.set(j, wtr.get(j) + rep.W.get(i, j) * r.get(k));
            }

            Array v = rep.M.mutiply(wtr);

            // N = I - (1/theta) M (W_free^T W_free)
            Matrix WftWf = new Matrix(m2, m2, 0.0);

            for (int k = 0; k < nf; ++k) {
                int i = freeIdx.get(k);

                for (int a = 0; a < m2; ++a)
                    for (int bb = 0; bb < m2; ++bb)
                        WftWf.set(a, bb,
                                WftWf.get(a, bb) + rep.W.get(i, a) * rep.W.get(i, bb));
            }

            Matrix N = rep.M.multipy(WftWf).multiply(-1.0 / theta);

            for (int a = 0; a < m2; ++a)
                N.set(a, a, N.get(a, a) + 1.0);
            v = N.inverse().mutiply(v);

            // dhat = -(1/theta) r - (1/theta^2) W_free v
            for (int k = 0; k < nf; ++k) {
                int i = freeIdx.get(k);
                double Wfv = 0.0;

                for (int j = 0; j < m2; ++j)
                    Wfv += rep.W.get(i, j) * v.get(j);

                dhat.set(k, -r.get(k) / theta - Wfv / (theta * theta));
            }
        }

        // truncate so that every free variable stays inside the box
        double alphaStar = 1.0;

        for (int k = 0; k < nf; ++k) {
            int i = freeIdx.get(k);

            if (dhat.get(k) > 0.0 && !noUpper(hi.get(i)))
                alphaStar = Math.min(alphaStar,
                        (hi.get(i) - xcp.get(i)) / dhat.get(k));
            else if (dhat.get(k) < 0.0 && !noLower(lo.get(i)))
                alphaStar = Math.min(alphaStar,
                        (lo.get(i) - xcp.get(i)) / dhat.get(k));
        }
        alphaStar = Math.max(alphaStar, 0.0);

        for (int k = 0; k < nf; ++k)
            xbar.set(freeIdx.get(k),
                    xcp.get(freeIdx.get(k)) + alphaStar * dhat.get(k));
        return xbar;
    }

    // Largest feasible step length along d starting from x.
    private static double maxFeasibleStep(final Array x,
                                          final Array d,
                                          final Array lo,
                                          final Array hi) {
        double stp = INF;

        for (int i = 0; i < x.size(); ++i) {
            if (d.get(i) > 0.0 && !noUpper(hi.get(i)))
                stp = Math.min(stp, (hi.get(i) - x.get(i)) / d.get(i));
            else if (d.get(i) < 0.0 && !noLower(lo.get(i)))
                stp = Math.min(stp, (lo.get(i) - x.get(i)) / d.get(i));
        }
        return stp;
    }

    private static final class LineSearchResult {
        double alpha;
        Array x;
        Array g;
        double f;
    }

    // Evaluation helper mirroring the lambda in the official line search.
    private static final class WolfeEval {
        final Problem P;
        final Array x, d;
        Array xt, gt;
        double ft;
        boolean haveBest = false;
        double bestAlpha = 0.0, bestF;
        Array bestX, bestG;

        WolfeEval(Problem P, Array x, Array d, double f0) {
            this.P = P;
            this.x = x;
            this.d = d;
            this.bestF = f0;
        }

        double eval(double a) {
            xt = x.add(d.mutiply(a));
            gt = new Array(x.size());
            ft = P.valueAndGradient(gt, xt);

            if (ft < bestF) {
                haveBest = true;
                bestF = ft;
                bestAlpha = a;
                bestX = new Array(xt);
                bestG = new Array(gt);
            }
            return DotProduct(gt, d);
        }
    }

    // Line search enforcing the strong Wolfe conditions (Nocedal & Wright,
    // Algorithms 3.5/3.6), capped at the largest feasible step.
    private static LineSearchResult lineSearchWolfe(Problem P,
                                                    final Array x,
                                                    final Array d,
                                                    double f0,
                                                    final Array g0,
                                                    double stpMax) {
        final double c1 = 1e-4, c2 = 0.9;
        final int maxIter = 30;

        double dphi0 = DotProduct(g0, d);
        if (dphi0 >= 0.0)
            return null; // not a descent direction

        WolfeEval ev = new WolfeEval(P, x, d, f0);

        double aLo = 0.0, aHi = 0.0, fLo = f0;
        boolean bracketed = false;

        double aPrev = 0.0, fPrev = f0;
        double a = Math.min(1.0, stpMax);

        for (int i = 0; i < maxIter; ++i) {
            double dphi = ev.eval(a);

            if (ev.ft > f0 + c1 * a * dphi0 || (i > 0 && ev.ft >= fPrev)) {
                aLo = aPrev;
                fLo = fPrev;
                aHi = a;
                bracketed = true;
                break;
            }

            if (Math.abs(dphi) <= -c2 * dphi0) {
                LineSearchResult r = new LineSearchResult();
                r.alpha = a;
                r.x = ev.xt;
                r.f = ev.ft;
                r.g = ev.gt;
                return r; // strong Wolfe satisfied
            }

            if (dphi >= 0.0) {
                aLo = a;
                fLo = ev.ft;
                aHi = aPrev;
                bracketed = true;
                break;
            }

            aPrev = a;
            fPrev = ev.ft;

            if (a >= stpMax)
                break; // cannot expand further
            a = Math.min(2.0 * a, stpMax);
        }

        if (bracketed) {
            for (int j = 0; j < maxIter; ++j) {
                a = 0.5 * (aLo + aHi); // bisection
                double dphi = ev.eval(a);

                if (ev.ft > f0 + c1 * a * dphi0 || ev.ft >= fLo) {
                    aHi = a;
                } else {
                    if (Math.abs(dphi) <= -c2 * dphi0) {
                        LineSearchResult r = new LineSearchResult();
                        r.alpha = a;
                        r.x = ev.xt;
                        r.f = ev.ft;
                        r.g = ev.gt;
                        return r;
                    }

                    if (dphi * (aHi - aLo) >= 0.0)
                        aHi = aLo;

                    aLo = a;
                    fLo = ev.ft;
                }

                if (Math.abs(aHi - aLo) < QL_EPSILON * Math.max(1.0, Math.abs(a)))
                    break;
            }
        }

        // strong Wolfe not reached: accept the best sufficient decrease
        if (ev.haveBest) {
            LineSearchResult r = new LineSearchResult();
            r.alpha = ev.bestAlpha;
            r.x = ev.bestX;
            r.f = ev.bestF;
            r.g = ev.bestG;
            return r;
        }
        return null;
    }

    private final int m_;
    private final double pgTol_, factr_;
    private final Array lowerBound_, upperBound_;

    public LBFGSB() {
        this(10, 1e-8, 1e7 * QL_EPSILON);
    }

    public LBFGSB(int memory) {
        this(memory, 1e-8, 1e7 * QL_EPSILON);
    }

    public LBFGSB(int memory, double pgTol) {
        this(memory, pgTol, 1e7 * QL_EPSILON);
    }

    public LBFGSB(int memory, double pgTol, double fTol) {
        this.m_ = memory;
        this.pgTol_ = pgTol;
        this.factr_ = fTol / QL_EPSILON;
        this.lowerBound_ = new Array(0);
        this.upperBound_ = new Array(0);
        QL_REQUIRE(memory > 0, "memory must be positive");
    }

    public LBFGSB(Array lowerBound, Array upperBound) {
        this(lowerBound, upperBound, 10, 1e-8, 1e7 * QL_EPSILON);
    }

    public LBFGSB(Array lowerBound, Array upperBound, int memory) {
        this(lowerBound, upperBound, memory, 1e-8, 1e7 * QL_EPSILON);
    }

    public LBFGSB(Array lowerBound, Array upperBound, int memory, double pgTol) {
        this(lowerBound, upperBound, memory, pgTol, 1e7 * QL_EPSILON);
    }

    public LBFGSB(Array lowerBound, Array upperBound, int memory,
                  double pgTol, double fTol) {
        this.m_ = memory;
        this.pgTol_ = pgTol;
        this.factr_ = fTol / QL_EPSILON;
        this.lowerBound_ = new Array(lowerBound);
        this.upperBound_ = new Array(upperBound);
        QL_REQUIRE(memory > 0, "memory must be positive");
        QL_REQUIRE(lowerBound_.size() == upperBound_.size(),
                "lower and upper bound sizes are inconsistent");
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        ReferencePkg<EndCriteria.Type> ecType =
                new ReferencePkg<>(EndCriteria.Type.None);
        P.reset();

        Array x = P.currentValue();
        final int n = x.size();

        Array lo = !lowerBound_.empty()
                ? new Array(lowerBound_) : P.constraint().lowerBound(x);
        Array hi = !upperBound_.empty()
                ? new Array(upperBound_) : P.constraint().upperBound(x);

        QL_REQUIRE(lo.size() == n && hi.size() == n,
                "bounds size does not match the number of variables");

        // start from a feasible point
        for (int i = 0; i < n; ++i)
            x.set(i, Math.min(Math.max(x.get(i), lo.get(i)), hi.get(i)));

        Array g = new Array(n);
        double f = P.valueAndGradient(g, x);
        P.setCurrentValue(x);
        P.setFunctionValue(f);

        List<Array> S = new ArrayList<>(), Y = new ArrayList<>();
        double theta = 1.0;
        int iter = 0;
        double pgInf = 0.0; // projected gradient inf-norm; persists for final reporting

        while (true) {
            // infinity norm of the projected gradient
            pgInf = 0.0;

            for (int i = 0; i < n; ++i) {
                double proj = Math.min(Math.max(x.get(i) - g.get(i), lo.get(i)),
                        hi.get(i)) - x.get(i);
                pgInf = Math.max(pgInf, Math.abs(proj));
            }

            P.setGradientNormValue(pgInf * pgInf);

            if (pgInf < pgTol_) {
                ecType.setT(EndCriteria.Type.ZeroGradientNorm);
                break;
            }

            if (endCriteria.checkZeroGradientNorm(pgInf, ecType))
                break;

            if (endCriteria.checkMaxIterations(iter, ecType))
                break;

            // compact representation; drop the oldest pairs if the middle
            // matrix turns out numerically singular
            CompactRep rep;

            while (true) {
                try {
                    rep = buildCompactRep(S, Y, theta);
                    break;
                } catch (RuntimeException ex) {
                    if (S.isEmpty()) {
                        rep = new CompactRep();
                        rep.theta = theta;
                        break;
                    }
                    S.remove(0);
                    Y.remove(0);
                }
            }

            CauchyResult cauchy = generalizedCauchyPoint(x, g, lo, hi, rep);
            Array xcp = cauchy.xcp;
            Array c = cauchy.c;
            boolean[] isFree = cauchy.isFree;

            Array xbar;
            try {
                xbar = subspaceMinimization(x, g, xcp, c, lo, hi, rep, isFree);
            } catch (RuntimeException ex) {
                xbar = new Array(xcp); // fall back to the Cauchy point
            }

            Array d = xbar.subtract(x);
            double dphi0 = DotProduct(g, d);

            // fall back to the (always-descent) Cauchy direction, then to
            // the projected gradient, if the subspace step is not downhill
            if (Norm2(d) < QL_EPSILON || dphi0 >= 0.0) {
                d = xcp.subtract(x);
                dphi0 = DotProduct(g, d);
            }

            if (Norm2(d) < QL_EPSILON) {
                ecType.setT(EndCriteria.Type.StationaryPoint);
                break;
            }

            if (dphi0 >= 0.0) {
                for (int i = 0; i < n; ++i)
                    d.set(i, Math.min(Math.max(x.get(i) - g.get(i), lo.get(i)),
                            hi.get(i)) - x.get(i));
                dphi0 = DotProduct(g, d);
                if (dphi0 >= 0.0 || Norm2(d) < QL_EPSILON) {
                    ecType.setT(EndCriteria.Type.StationaryPoint);
                    break;
                }
            }

            double stpMax = maxFeasibleStep(x, d, lo, hi);
            if (stpMax < QL_EPSILON) {
                ecType.setT(EndCriteria.Type.StationaryPoint);
                break;
            }

            LineSearchResult ls = lineSearchWolfe(P, x, d, f, g, stpMax);
            if (ls == null) {
                ecType.setT(EndCriteria.Type.StationaryFunctionValue);
                break;
            }

            Array xt = ls.x;
            double ft = ls.f;
            Array gt = ls.g;

            // limited-memory update with the curvature safeguard
            Array s = xt.subtract(x);
            Array y = gt.subtract(g);
            double sy = DotProduct(s, y);
            double yy = DotProduct(y, y);

            if (yy > 0.0 && sy > QL_EPSILON * yy) {
                S.add(s);
                Y.add(y);

                if (S.size() > m_) {
                    S.remove(0);
                    Y.remove(0);
                }
                theta = yy / sy;
            }

            double fOld = f;
            x = xt;
            f = ft;
            g = gt;
            ++iter;

            P.setCurrentValue(x);
            P.setFunctionValue(f);

            // relative function-reduction stop (SciPy's factr criterion)
            double denom = Math.max(Math.max(Math.abs(fOld), Math.abs(f)), 1.0);
            if ((fOld - f) <= factr_ * QL_EPSILON * denom) {
                ecType.setT(EndCriteria.Type.StationaryFunctionValue);
                break;
            }
        }

        P.setCurrentValue(x);
        P.setFunctionValue(f);
        P.setGradientNormValue(pgInf * pgInf);
        return ecType.getT();
    }
}
