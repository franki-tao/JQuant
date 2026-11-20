package jquant.experimental.barrieroption;

import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.Option;
import jquant.TimeGrid;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Perturbative barrier-option engine.
 *
 * <p>Java port of {@code PerturbativeBarrierOptionEngine} from
 * {@code ql/experimental/barrieroption/perturbativebarrieroptionengine.cpp}.
 * The numerical formulas and integration defaults are kept unchanged.
 */
public class PerturbativeBarrierOptionEngine extends BarrierOption.engine {

    private static final double PI = 3.14159265358979324;
    private static final double halfPi = PI / 2.0;
    private static final double twoPi = 2.0 * PI;
    private static final double sqrtTwo = 1.4142135623730950488;
    private static final double sqrtPi = 1.7724538509055160273;
    private static final double sqrtTwoPi = sqrtTwo * sqrtPi;

    private final GeneralizedBlackScholesProcess process_;
    private final int order_;
    private final boolean zeroGamma_;
    private final int npoint_;
    private final int npoint2_;

    public PerturbativeBarrierOptionEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, 1, false, 1000, 100);
    }

    public PerturbativeBarrierOptionEngine(
            GeneralizedBlackScholesProcess process,
            int order) {
        this(process, order, false, 1000, 100);
    }

    public PerturbativeBarrierOptionEngine(
            GeneralizedBlackScholesProcess process,
            int order,
            boolean zeroGamma) {
        this(process, order, zeroGamma, 1000, 100);
    }

    public PerturbativeBarrierOptionEngine(
            GeneralizedBlackScholesProcess process,
            int order,
            boolean zeroGamma,
            int npoint,
            int npoint2) {
        process_ = process;
        order_ = order;
        zeroGamma_ = zeroGamma;
        npoint_ = npoint;
        npoint2_ = npoint2;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.barrierType == Barrier.Type.UpOut,
                "this engine only manages up-and-out options");
        QL_REQUIRE(arguments_.rebate == 0.0,
                "this engine does not manage non-null rebates");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;
        QL_REQUIRE(payoff.optionType() == Option.Type.Put,
                "this engine only manages put options");

        double stock = process_.x0();
        double kprice = payoff.strike();
        double hbarr = arguments_.barrier;

        double tauMin = 0.0;
        double tauMax = process_.time(arguments_.exercise.lastDate());

        QL_REQUIRE(order_ <= 2, "order must be <= 2");
        QL_REQUIRE(npoint_ > 0 && npoint2_ > 0,
                "integration point counts must be positive");

        int igm = zeroGamma_ ? 0 : 1;

        double s = process_.x0();
        YieldTermStructure r = process_.riskFreeRate().getValue();
        YieldTermStructure q = process_.dividendYield().getValue();
        BlackVolTermStructure v = process_.blackVolatility().getValue();

        DoubleBinaryOperator integr = (t1, t2) ->
                r.forwardRate(t1, t2, Compounding.Continuous,
                        Frequency.ANNUAL, false).rate() * (t2 - t1);
        DoubleBinaryOperator integalpha = (t1, t2) ->
                (r.forwardRate(t1, t2, Compounding.Continuous,
                        Frequency.ANNUAL, false).rate()
                        - q.forwardRate(t1, t2, Compounding.Continuous,
                        Frequency.ANNUAL, false).rate()) * (t2 - t1);
        DoubleBinaryOperator integs = (t1, t2) ->
                v.blackForwardVariance(t1, t2, s, true);
        DoubleUnaryOperator alpha = t ->
                r.forwardRate(t, t, Compounding.Continuous,
                        Frequency.ANNUAL, false).rate()
                        - q.forwardRate(t, t, Compounding.Continuous,
                        Frequency.ANNUAL, false).rate();
        DoubleUnaryOperator sigmaq = t -> {
            double sigma = v.blackForwardVol(t, t, s, true);
            return sigma * sigma;
        };

        results_.value = barrierUPD(kprice, stock, hbarr,
                tauMin, tauMax, order_, igm, npoint_, npoint2_,
                integr, integalpha, integs, alpha, sigmaq);
    }

    private static double SIGN(double a, double b) {
        if (b > 0.0)
            return Math.abs(a);
        else
            return -Math.abs(a);
    }

    private double barrierUPD(double kprice,
                              double stock,
                              double hbarr,
                              double taumin,
                              double taumax,
                              int iord,
                              int igm,
                              int npoint,
                              int npoint2,
                              DoubleBinaryOperator integr,
                              DoubleBinaryOperator integalpha,
                              DoubleBinaryOperator integs,
                              DoubleUnaryOperator alpha,
                              DoubleUnaryOperator sigmaq) {
        double v0 = 0.0, v1 = 0.0, v1p = 0.0, v2p = 0.0, v2pp = 0.0, gm = 0.0;
        int i = 0, j = 0;
        double tmp = 0.0;
        double xstar = 0.0, s0 = 0.0;
        double sigmat = 0.0, disc = 0.0, d1 = 0.0, d2 = 0.0, d3 = 0.0, d4 = 0.0;
        double et = 0.0, tt = 0.0, dt = 0.0, p = 0.0;
        double dsqpi;
        double caux = 0.0, ccaux = 0.0;
        double auxnew = 0.0;
        double x = 0.0, b = 0.0, c = 0.0;

        if (igm == 0) {
            gm = 0.0;
        } else if (igm == 1) {
            gm = integalpha.applyAsDouble(taumin, taumax)
                    / (0.5 * integs.applyAsDouble(taumin, taumax));
        } else {
            gm = 0.0;
        }

        // xstar=min(0,log(kprice/hbarr))
        xstar = Math.log(kprice / hbarr);

        if (xstar > 0.0) xstar = 0.0;
        sigmat = integs.applyAsDouble(taumin, taumax);
        disc = -integr.applyAsDouble(taumin, taumax);
        final double oneMinusGamma = 1.0 - gm;
        final double onePlusGamma = 1.0 + gm;
        final double barrierFactor = hbarr * Math.exp(0.5 * onePlusGamma * xstar)
                - kprice * Math.exp(-0.5 * oneMinusGamma * xstar);

        // Change of variable
        s0 = stock / hbarr;

        // Compute the zero-th order term P_0
        x = Math.log(s0);
        final double invSqrtSigmat = 1.0 / Math.sqrt(sigmat);
        final double halfSigmat = 0.5 * sigmat;
        final double minusGammaNumerator = xstar + oneMinusGamma * halfSigmat;
        final double plusGammaNumerator = xstar - onePlusGamma * halfSigmat;
        final double scaledLogS0 = x * invSqrtSigmat;
        final double scaledMinusGammaNumerator = minusGammaNumerator * invSqrtSigmat;
        final double scaledPlusGammaNumerator = plusGammaNumerator * invSqrtSigmat;
        d1 = scaledMinusGammaNumerator - scaledLogS0;
        d2 = scaledMinusGammaNumerator + scaledLogS0;
        d3 = scaledPlusGammaNumerator - scaledLogS0;
        d4 = scaledPlusGammaNumerator + scaledLogS0;

        double e1 = PHID(d1);
        double e2 = PHID(d2);
        double e3 = PHID(d3);
        double e4 = PHID(d4);

        v0 = kprice * e1 - kprice * Math.exp(oneMinusGamma * x) * e2;
        v0 = v0 + Math.exp(gm * halfSigmat) * (-hbarr * s0 * e3
                + hbarr * Math.exp(-gm * x) * e4);
        v0 = v0 * Math.exp(disc);

        if (iord == 0) return v0;

        // Compute the first order term P_1

        dt = (taumax - taumin) / (double) npoint;

        tt = 0.5 * integs.applyAsDouble(taumin, taumax);

        et = Math.exp(0.5 * (1.0 - gm) * x);

        dsqpi = sqrtPi;

        v1 = 0.0;
        for (i = 1; i <= npoint; i++) {
            v1p = 0.0;
            tmp = taumin + dt * (double) (2 * i - 1) * 0.5;
            p = 0.5 * integs.applyAsDouble(tmp, taumax);
            final double expGammaP = Math.exp(gm * p);

            // Function E(p,tt,a,b,gm)
            ccaux = v(p, tt, x, xstar, gm) + v(p, tt, x, -xstar, gm)
                    - v(p, tt, -x, xstar, gm) - v(p, tt, -x, -xstar, gm);
            auxnew = ccaux * (-kprice * Math.exp(-xstar * 0.5 * (1.0 - gm))
                    + hbarr * Math.exp(xstar * 0.5 * (1.0 + gm)));
            v1p = v1p + auxnew;

            // Function L(p,tt,a,b,c,gm)
            b = gm - 1.0;
            c = -xstar;
            ccaux = llold(p, tt, x, b, c, gm) - llold(p, tt, -x, b, c, gm);
            auxnew = kprice * (1.0 - gm) * ccaux;
            v1p = v1p + auxnew;

            b = -(gm + 1.0);
            c = xstar;
            ccaux = llold(p, tt, x, b, c, gm) - llold(p, tt, -x, b, c, gm);
            auxnew = -expGammaP * hbarr * ccaux;
            v1p = v1p + auxnew;

            b = (gm + 1.0);
            c = -xstar;
            ccaux = llold(p, tt, x, b, c, gm) - llold(p, tt, -x, b, c, gm);
            auxnew = expGammaP * hbarr * gm * ccaux;
            v1p = v1p + auxnew;

            // Function F(p,tt,a,b,c,gm)
            b = gm - 1.0;
            auxnew = -kprice * (1.0 - gm)
                    * (ff(p, tt, x, b, gm) - ff(p, tt, -x, b, gm));
            v1p = v1p + auxnew;

            b = gm + 1.0;
            auxnew = -expGammaP * gm * hbarr
                    * (ff(p, tt, x, b, gm) - ff(p, tt, -x, b, gm));
            v1p = v1p + auxnew;

            v1 = v1 + (alpha.applyAsDouble(tmp)
                    - gm * 0.5 * sigmaq.applyAsDouble(tmp)) * v1p;
        }

        v1 = Math.exp(disc) * et * v1 * dt / (dsqpi * 2.0);

        if (iord == 1) return v0 + v1;

        // Compute the second order term P_2

        double v2, dtp, tmp1, s;
        v2 = 0.0;

        for (i = 1; i <= npoint; i++) {
            v2p = 0.0;
            tmp = taumin + dt * (double) (2 * i - 1) * 0.5;
            p = 0.5 * integs.applyAsDouble(tmp, taumax);

            dtp = (taumax - tmp) / (double) (npoint2);
            final double outerPerturbation =
                    alpha.applyAsDouble(tmp) - gm * 0.5 * sigmaq.applyAsDouble(tmp);

            for (j = 1; j <= npoint2; j++) {
                tmp1 = tmp + dtp * (double) (2 * j - 1) * 0.50;
                s = 0.50 * integs.applyAsDouble(tmp1, taumax);
                final double expGammaS = Math.exp(gm * s);
                final double innerPerturbation =
                        alpha.applyAsDouble(tmp1)
                                - gm * 0.5 * sigmaq.applyAsDouble(tmp1);

                caux = dll(s, p, tt, -x, -1.0 + gm, -xstar, gm)
                        - dll(s, p, tt, x, -1.0 + gm, -xstar, gm);
                v2pp = caux * kprice * (1.0 - gm);

                caux = dll(s, p, tt, -x, -1.0 - gm, xstar, gm)
                        - dll(s, p, tt, x, -1.0 - gm, xstar, gm);
                v2pp = v2pp - expGammaS * hbarr * caux;

                caux = dll(s, p, tt, -x, 1.0 + gm, -xstar, gm)
                        - dll(s, p, tt, x, 1.0 + gm, -xstar, gm);
                v2pp = v2pp + expGammaS * gm * hbarr * caux;

                caux = +dvv(s, p, tt, -x, xstar, gm)
                        - dvv(s, p, tt, x, xstar, gm);
                caux = caux + (dvv(s, p, tt, -x, -xstar, gm)
                        - dvv(s, p, tt, x, -xstar, gm));
                v2pp = v2pp + barrierFactor * caux;

                caux = dff(s, p, tt, -x, -1.0 + gm, gm)
                        - dff(s, p, tt, x, -1.0 + gm, gm);
                v2pp = v2pp - (1.0 - gm) * kprice * caux;

                caux = dff(s, p, tt, -x, 1.0 + gm, gm)
                        - dff(s, p, tt, x, 1.0 + gm, gm);
                v2pp = v2pp - expGammaS * gm * hbarr * caux;

                v2pp = v2pp * 0.5 * (1.0 - gm);

                caux = -ddll(s, p, tt, -x, -1.0 + gm, -xstar, gm)
                        + ddll(s, p, tt, x, -1.0 + gm, -xstar, gm);
                v2pp = v2pp + caux * kprice * (1.0 - gm);

                caux = -ddll(s, p, tt, -x, -1.0 - gm, xstar, gm)
                        + ddll(s, p, tt, x, -1.0 - gm, xstar, gm);
                v2pp = v2pp - expGammaS * hbarr * caux;

                caux = -ddll(s, p, tt, -x, 1.0 + gm, -xstar, gm)
                        + ddll(s, p, tt, x, 1.0 + gm, -xstar, gm);
                v2pp = v2pp + expGammaS * gm * hbarr * caux;

                caux = -ddvv(s, p, tt, -x, xstar, gm)
                        + ddvv(s, p, tt, x, xstar, gm);
                caux = caux + (-dvv(s, p, tt, -x, -xstar, gm)
                        + dvv(s, p, tt, x, -xstar, gm));

                v2pp = v2pp + barrierFactor * caux;

                caux = -ddff(s, p, tt, -x, -1 + gm, gm)
                        + ddff(s, p, tt, x, -1 + gm, gm);
                v2pp = v2pp - (1.0 - gm) * kprice * caux;

                caux = -ddff(s, p, tt, -x, 1.0 + gm, gm)
                        + ddff(s, p, tt, x, 1.0 + gm, gm);
                v2pp = v2pp - expGammaS * gm * hbarr * caux;

                v2p = v2p + innerPerturbation * v2pp;
            }

            v2 = v2 + v2p * outerPerturbation * dtp;
        }

        v2 = Math.exp(disc) * et * v2 * dt;

        return v0 + v1 + v2;
    }

    private static double PHID(double Z) {
        double P0, P1, P2, P3, P4, P5, P6;
        double Q0, Q1, Q2, Q3, Q4, Q5, Q6, Q7;
        double P, EXPNTL, CUTOFF, ZABS;

        P0 = 220.2068679123761;
        P1 = 221.2135961699311;
        P2 = 112.0792914978709;
        P3 = 33.91286607838300;
        P4 = 6.373962203531650;
        P5 = 0.7003830644436881;
        P6 = 0.03526249659989109;

        Q0 = 440.4137358247522;
        Q1 = 793.8265125199484;
        Q2 = 637.3336333788311;
        Q3 = 296.5642487796737;
        Q4 = 86.78073220294608;
        Q5 = 16.064177579206950;
        Q6 = 1.7556671631826420;
        Q7 = 0.088388347648318440;
        CUTOFF = 7.071067811865475;

        ZABS = Math.abs(Z);
        if (ZABS > 37)
            P = 0;
        else {
            EXPNTL = Math.exp(-ZABS * ZABS / 2);
            if (ZABS < CUTOFF)
                P = EXPNTL * ((((((P6 * ZABS + P5) * ZABS + P4) * ZABS + P3)
                        * ZABS + P2) * ZABS + P1) * ZABS + P0)
                        / (((((((Q7 * ZABS + Q6) * ZABS + Q5) * ZABS + Q4)
                        * ZABS + Q3) * ZABS + Q2) * ZABS + Q1) * ZABS + Q0);
            else
                P = EXPNTL / (ZABS + 1 / (ZABS + 2 / (ZABS + 3 / (ZABS
                        + 4 / (ZABS + 0.65))))) / sqrtTwoPi;
        }
        if (Z > 0) P = 1 - P;

        return P;
    }

    // Function F(p,tt,a,b,gm)
    private static double ff(double p, double tt, double a, double b, double gm) {
        double phid;
        double aa, caux;

        aa = -(b * p - b * tt + a) / Math.sqrt(2.0 * (tt - p));

        caux = 2.0 * sqrtPi * PHID(aa);
        aa = b * b - (1.0 - gm) * (1.0 - gm);
        aa = aa / 4.0;
        phid = Math.exp(-0.5 * a * b) * Math.exp(aa * (tt - p)) * caux;

        return phid;
    }

    // Function E(p,tt,a,b,gm)
    private static double v(double p, double tt, double a, double b, double gm) {
        double result;
        double aa, caux;

        aa = -(p * (a - b) + b * tt)
                / Math.sqrt(2.0 * p * tt * (tt - p));
        caux = PHID(aa);

        aa = Math.exp((a - b) * (a - b) / (4.0 * tt))
                * Math.exp((1.0 - gm) * (1.0 - gm) * tt / 4.0) * Math.sqrt(tt);
        result = caux / aa;

        return result;
    }

    // Function L(p,tt,a,b,c,gm)
    private static double llold(double p, double tt, double a, double b,
                                double c, double gm) {
        double bvnd;
        double xx, yy, rho, caux;
        double aa;

        xx = (-a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (-a + b * tt + c) / Math.sqrt(2.0 * tt);
        rho = Math.sqrt((tt - p) / tt);
        aa = b * b - (1.0 - gm) * (1.0 - gm);
        aa = aa / 4.0;
        caux = ND2(-xx, -yy, rho);

        bvnd = 2.0 * sqrtPi * Math.exp(-a * b * 0.5)
                * Math.exp(aa * (tt - p)) * caux;
        return bvnd;
    }

    // Function D_E(s,p,tt,a,b,gm)
    private static double dvv(double s, double p, double tt, double a,
                              double b, double gm) {
        double result;
        double aa, caux, caux1, caux2;
        double xx, yy, rho;

        aa = (a * p + b * (tt - p)) / Math.sqrt(2.0 * p * tt * (tt - p));
        caux = PHID(aa);

        aa = Math.exp((a - b) * (a - b) / (4.0 * tt))
                * Math.exp((1.0 - gm) * (1.0 - gm) * tt / 4.0) * Math.sqrt(tt);
        caux = -caux / aa;

        xx = (a * p + b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        yy = (a * s + b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        caux1 = ND2(-xx, -yy, rho);
        caux1 = caux1 / aa;

        aa = Math.exp((a + b) * (a + b) / (4.0 * tt))
                * Math.exp((1.0 - gm) * (1.0 - gm) * tt / 4.0) * Math.sqrt(tt);

        xx = (a * p - b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        yy = (a * s - b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        caux2 = ND2(-xx, -yy, rho);
        caux2 = caux2 / aa;
        result = (caux + caux1 + caux2) / (2.0 * sqrtPi);
        return result;
    }

    // Function D_F(s,p,tt,a,b,gm)
    private static double dff(double s, double p, double tt, double a,
                              double b, double gm) {
        double result;
        double aa, caux, caux1, caux2;
        double xx, yy, rho;

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        caux = -PHID(xx) * Math.exp(-0.5 * a * b);

        xx = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        rho = Math.sqrt((tt - p) / (tt - s));
        caux1 = ND2(-xx, -yy, rho);
        caux1 = Math.exp(0.5 * a * b) * caux1;

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (a - b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        rho = Math.sqrt((tt - p) / (tt - s));
        caux2 = ND2(-xx, -yy, rho);
        caux2 = Math.exp(-0.5 * a * b) * caux2;

        aa = Math.exp((b * b - (1.0 - gm) * (1.0 - gm)) * (tt - s) / 4.0);

        result = (caux + caux1 + caux2) * aa;
        return result;
    }

    // Function D_L(s,p,a,b,c,gm)
    private static double dll(double s, double p, double tt, double a,
                              double b, double c, double gm) {
        double result;
        double aa, caux, caux1;
        double[] sigmarho = new double[4];
        double[] limit = new double[4];
        double epsi;

        epsi = 1.e-12;
        limit[1] = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (a + b * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);

        caux = Math.exp(0.5 * a * b) * tvtl(0, limit, sigmarho, epsi);

        limit[1] = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (-a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (-a + b * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = -Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = -Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);

        caux1 = -Math.exp(-0.5 * a * b) * tvtl(0, limit, sigmarho, epsi);

        aa = Math.exp((b * b - (1.0 - gm) * (1.0 - gm)) * (tt - s) / 4.0);

        result = (caux + caux1) * aa;

        return result;
    }

    // Derivative with respect to a of D_F(s,p,tt,a,b,gm)
    private static double ddff(double s, double p, double tt, double a,
                               double b, double gm) {
        double aa, caux, caux1, caux2, caux3, caux4;
        double xx, yy, rho;
        double result;

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        caux = PHID(xx) * Math.exp(-0.5 * a * b);

        xx = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        rho = Math.sqrt((tt - p) / (tt - s));
        caux1 = ND2(-xx, -yy, rho);
        caux1 = Math.exp(0.5 * a * b) * caux1;

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (a - b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        rho = Math.sqrt((tt - p) / (tt - s));
        caux2 = ND2(-xx, -yy, rho);
        caux2 = -Math.exp(-0.5 * a * b) * caux2;

        caux = 0.5 * b * (caux + caux1 + caux2);

        xx = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = b * Math.sqrt(p - s) / sqrtTwo;
        caux1 = Math.exp(-0.5 * xx * xx) * Math.exp(0.5 * a * b) * PHID(yy)
                / (2.0 * Math.sqrt(PI * (tt - p)));

        xx = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        yy = a * Math.sqrt(p - s) / Math.sqrt(2.0 * (tt - p) * (tt - s));
        caux2 = Math.exp(-0.5 * xx * xx) * Math.exp(0.5 * a * b) * PHID(yy)
                / (2.0 * Math.sqrt(PI * (tt - s)));

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = b * Math.sqrt(p - s) / sqrtTwo;
        caux3 = -Math.exp(-0.5 * xx * xx) * Math.exp(-0.5 * a * b) * PHID(yy)
                / (2.0 * Math.sqrt(PI * (tt - p)));

        xx = (a - b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        yy = a * Math.sqrt(p - s) / Math.sqrt(2.0 * (tt - p) * (tt - s));
        caux4 = Math.exp(-0.5 * xx * xx) * Math.exp(-0.5 * a * b) * PHID(yy)
                / (2.0 * Math.sqrt(PI * (tt - s)));

        aa = Math.exp((b * b - (1.0 - gm) * (1.0 - gm)) * (tt - p) / 4.0);

        result = (caux + caux1 + caux2 + caux3 + caux4) * aa;
        return result;
    }

    // Derivative with respect to a of D_L(s,p,tt,a,b,c,gm)
    private static double ddll(double s, double p, double tt, double ax,
                               double bx, double c, double gm) {
        double result;
        double aa, caux, caux1;
        double[] sigmarho = new double[4];
        double[] sigma = new double[4];
        double[] limit = new double[4];
        int idx;
        double epsi;

        epsi = 1.e-12;
        limit[1] = (ax + bx * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (ax + bx * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (ax + bx * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);
        sigma[1] = Math.sqrt(1.0 - sigmarho[1] * sigmarho[1]);
        sigma[2] = Math.sqrt(1.0 - sigmarho[2] * sigmarho[2]);
        sigma[3] = Math.sqrt(1.0 - sigmarho[3] * sigmarho[3]);

        caux = 0.5 * bx * tvtl(0, limit, sigmarho, epsi);

        idx = 1;
        caux = caux + derivn3(limit, sigmarho, sigma, idx)
                / Math.sqrt(2.0 * (tt - p));

        idx = 2;
        caux = caux + derivn3(limit, sigmarho, sigma, idx)
                / Math.sqrt(2.0 * (tt - s));

        idx = 3;
        caux = caux + derivn3(limit, sigmarho, sigma, idx)
                / Math.sqrt(2.0 * tt);

        caux = Math.exp(0.5 * ax * bx) * caux;

        limit[1] = (ax - bx * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (-ax + bx * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (-ax + bx * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = -Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = -Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);
        sigma[1] = Math.sqrt(1.0 - sigmarho[1] * sigmarho[1]);
        sigma[2] = Math.sqrt(1.0 - sigmarho[2] * sigmarho[2]);
        sigma[3] = Math.sqrt(1.0 - sigmarho[3] * sigmarho[3]);

        caux1 = 0.5 * bx * tvtl(0, limit, sigmarho, epsi);

        idx = 1;
        caux1 = caux1 - derivn3(limit, sigmarho, sigma, idx)
                / Math.sqrt(2.0 * (tt - p));

        idx = 2;
        caux1 = caux1 + derivn3(limit, sigmarho, sigma, idx)
                / Math.sqrt(2.0 * (tt - s));

        idx = 3;
        caux1 = caux1 + derivn3(limit, sigmarho, sigma, idx)
                / Math.sqrt(2.0 * tt);

        caux1 = Math.exp(-0.5 * ax * bx) * caux1;

        aa = Math.exp((bx * bx - (1.0 - gm) * (1.0 - gm)) * (tt - s) / 4.0);

        result = (caux + caux1) * aa;
        return result;
    }

    // Derivative with respect to a of D_E(s,p,tt,a,b,gm)
    private static double ddvv(double s, double p, double tt, double a,
                               double b, double gm) {
        double result;
        double aa, caux, caux1, caux2, caux6;
        double caux3, caux4, caux5, aux;
        double xx, yy, rho;

        aa = (a * p + b * (tt - p)) / Math.sqrt(2.0 * p * tt * (tt - p));
        caux = PHID(aa);

        aa = Math.exp(-(a - b) * (a - b) / (4.0 * tt)) / tt;

        caux = 0.5 * aa * caux * (a - b);

        xx = (a * p + b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        yy = (a * s + b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        caux1 = ND2(-xx, -yy, rho);
        caux1 = -0.5 * aa * caux1 * (a - b);

        aa = Math.exp(-(a + b) * (a + b) / (4.0 * tt)) / tt;

        xx = (a * p - b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        yy = (a * s - b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        caux2 = ND2(-xx, -yy, rho);
        caux2 = -0.5 * aa * caux2 * (a + b);

        aa = -b * Math.sqrt((p - s) / Math.sqrt(2.0 * p * s));
        aux = Math.sqrt(p / (PI * tt * (tt - p))) * PHID(aa);

        xx = (a + b) * (a + b) / (4.0 * tt);
        yy = (a * p - b * (tt - p)) * (a * p - b * (tt - p))
                / (4.0 * p * tt * (tt - p));
        caux3 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        xx = (a - b) * (a - b) / (4.0 * tt);
        yy = (a * p + b * (tt - p)) * (a * p + b * (tt - p))
                / (4.0 * p * tt * (tt - p));
        caux4 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        aa = a * Math.sqrt((p - s) / Math.sqrt(2.0 * (tt - p) * (tt - s)));
        aux = Math.sqrt(s / (PI * tt * (tt - s))) * PHID(aa);

        xx = (a + b) * (a + b) / (4.0 * tt);
        yy = (a * s - b * (tt - s)) * (a * s - b * (tt - s))
                / (4.0 * s * tt * (tt - s));
        caux5 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        xx = (a - b) * (a - b) / (4.0 * tt);
        yy = (a * s + b * (tt - s)) * (a * s + b * (tt - s))
                / (4.0 * s * tt * (tt - s));
        caux6 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        aux = Math.exp((1.0 - gm) * (1.0 - gm) * tt / 4.0) * Math.sqrt(tt);

        result = (caux + caux1 + caux2 + caux3 + caux4 + caux5 + caux6)
                / (aux * 2.0 * sqrtPi);
        return result;
    }

    // Derivatives of the trivariate cumulative normal distribution
    private static double derivn3(double[] limit, double[] sigmarho,
                                  double[] sigma, int idx) {
        double aa;
        double xx, yy, rho;
        double deriv;
        double sc = sqrtTwoPi;

        if (idx == 1) {
            aa = Math.exp(-0.5 * limit[1] * limit[1]);
            xx = (limit[3] - sigmarho[2] * limit[1]) / sigma[2];
            yy = (limit[2] - sigmarho[1] * limit[1]) / sigma[1];
            rho = (sigmarho[3] - sigmarho[1] * sigmarho[2])
                    / (sigma[1] * sigma[2]);
            deriv = aa * ND2(-xx, -yy, rho) / sc;
        } else {
            if (idx == 2) {
                aa = Math.exp(-0.5 * limit[2] * limit[2]);
                xx = (limit[1] - sigmarho[1] * limit[2]) / sigma[1];
                yy = (limit[3] - sigmarho[3] * limit[2]) / sigma[3];
                rho = (sigmarho[2] - sigmarho[1] * sigmarho[3])
                        / (sigma[1] * sigma[3]);
                deriv = aa * ND2(-xx, -yy, rho) / sc;
            } else {
                // idx=3
                aa = Math.exp(-0.5 * limit[3] * limit[3]);

                xx = (limit[1] - sigmarho[2] * limit[3]) / sigma[2];
                yy = (limit[2] - sigmarho[3] * limit[3]) / sigma[3];
                rho = (sigmarho[1] - sigmarho[2] * sigmarho[3])
                        / (sigma[2] * sigma[3]);
                deriv = aa * ND2(-xx, -yy, rho) / sc;
            }
        }
        return deriv;
    }

    private static double tvtl(int NU, double[] limit, double[] sigmarho,
                               double epsi) {
        double result;
        final double ONE = 1.0, ZRO = 0.0;
        double EPS, TVT;
        double PT, H1, H2, H3, R12, R13, R23, RUA, RUB, AR, RUC;
        EPS = Math.max(1.e-14, epsi);
        PT = halfPi;

        H1 = limit[1];
        H2 = limit[2];
        H3 = limit[3];
        R12 = sigmarho[1];
        R13 = sigmarho[2];
        R23 = sigmarho[3];

        // Sort R's and check for special cases
        if (Math.abs(R12) > Math.abs(R13)) {
            H2 = H3;
            H3 = limit[2];
            R12 = R13;
            R13 = sigmarho[1];
        }

        if (Math.abs(R13) > Math.abs(R23)) {
            H1 = H2;
            H2 = limit[1];
            R23 = R13;
            R13 = sigmarho[3];
        }

        TVT = 0.0;
        if ((Math.abs(H1) + Math.abs(H2) + Math.abs(H3)) < EPS)
            TVT = (1 + (Math.asin(R12) + Math.asin(R13) + Math.asin(R23)) / PT) / 8.0;
        else if ((NU < 1) && ((Math.abs(R12) + Math.abs(R13)) < EPS))
            TVT = PHID(H1) * BVTL(NU, H2, H3, R23);
        else if ((NU < 1) && ((Math.abs(R13) + Math.abs(R23)) < EPS))
            TVT = PHID(H3) * BVTL(NU, H1, H2, R12);
        else if ((NU < 1) && ((Math.abs(R12) + Math.abs(R23)) < EPS))
            TVT = PHID(H2) * BVTL(NU, H1, H3, R13);
        else if ((1.0 - R23) < EPS)
            TVT = BVTL(NU, H1, Math.min(H2, H3), R12);
        else if ((R23 + 1.0) < EPS) {
            if (H2 > -H3)
                TVT = BVTL(NU, H1, H2, R12) - BVTL(NU, H1, -H3, R12);
        } else {
            // Compute singular TVT value
            if (NU < 1)
                TVT = BVTL(NU, H2, H3, R23) * PHID(H1);
            else if (R23 > 0)
                TVT = BVTL(NU, H1, Math.min(H2, H3), ZRO);
            else if (H2 > -H3)
                TVT = BVTL(NU, H1, H2, ZRO) - BVTL(NU, H1, -H3, ZRO);

            // Use numerical integration to compute probability
            RUA = Math.asin(R12);
            RUB = Math.asin(R13);
            AR = Math.asin(R23);
            RUC = SIGN(PT, AR) - AR;
            final int H1f = 0; // placeholder not used
            final double fH1 = H1, fH2 = H2, fH3 = H3, fR23 = R23,
                    fRUA = RUA, fRUB = RUB, fAR = AR, fRUC = RUC;
            final int fNU = NU;
            DoubleUnaryOperator integrand = X -> TVTMFN(X, fH1, fH2, fH3,
                    fR23, fRUA, fRUB, fAR, fRUC, fNU);
            TVT = TVT + ADONET(ZRO, ONE, EPS, integrand) / (4.0 * PT);
        }
        result = Math.max(ZRO, Math.min(TVT, ONE));

        return result;
    }

    private static double TVTMFN(double X, double H1, double H2, double H3,
                                 double R23, double RUA, double RUB,
                                 double AR, double RUC, int NUC) {
        double R12 = 0.0, RR2 = 0, R13 = 0.0, RR3 = 0.0, R = 0.0, RR = 0.0;
        final double ZRO = 0.0;
        double result = 0.0;

        double[] sc = SINCS(RUA * X);
        R12 = sc[0];
        RR2 = sc[1];
        sc = SINCS(RUB * X);
        R13 = sc[0];
        RR3 = sc[1];

        if (Math.abs(RUA) > 0)
            result += RUA * PNTGND(NUC, H1, H2, H3, R13, R23, R12, RR2);
        if (Math.abs(RUB) > 0)
            result += RUB * PNTGND(NUC, H1, H3, H2, R12, R23, R13, RR3);
        if (NUC > 0) {
            sc = SINCS(AR + RUC * X);
            R = sc[0];
            RR = sc[1];
            result -= RUC * PNTGND(NUC, H2, H3, H1, ZRO, ZRO, R, RR);
        }
        return result;
    }

    // Computes SIN(X), COS(X)^2, with series approx. for |X| near PI/2
    private static double[] SINCS(double X) {
        final double PT = 1.57079632679489661923132169163975;
        double EE;
        double SX, CS;
        EE = (PT - Math.abs(X)) * (PT - Math.abs(X));

        if (EE < 5e-5) {
            SX = SIGN(1 - EE * (1 - EE / 12) / 2, X);
            CS = EE * (1 - EE * (1 - 2 * EE / 15) / 3);
        } else {
            SX = Math.sin(X);
            CS = 1 - SX * SX;
        }
        return new double[]{SX, CS};
    }

    // One Dimensional Globally Adaptive Integration Function
    private static double ADONET(double A, double B, double TOL,
                                 DoubleUnaryOperator integrand) {
        final int NL = 100;
        int I, IM, IP;
        double[] EI = new double[101];
        double[] AI = new double[101];
        double[] BI = new double[101];
        double[] FI = new double[101];
        double FIN = 0.0;
        double ERR;

        AI[1] = A;
        BI[1] = B;
        ERR = 1;
        IP = 1;
        IM = 1;
        while (((4 * ERR) > TOL) && (IM < NL)) {
            IM = IM + 1;
            BI[IM] = BI[IP];
            AI[IM] = (AI[IP] + BI[IP]) / 2.0;
            BI[IP] = AI[IM];
            double[] e = new double[1];
            FI[IP] = KRNRDT(AI[IP], BI[IP], integrand, e);
            EI[IP] = e[0];
            e[0] = 0.0;
            FI[IM] = KRNRDT(AI[IM], BI[IM], integrand, e);
            EI[IM] = e[0];

            ERR = 0.0;
            FIN = 0.0;
            for (I = 1; I <= IM; I++) {
                if (EI[I] > EI[IP]) IP = I;
                FIN = FIN + FI[I];
                ERR = ERR + EI[I] * EI[I];
            }
            ERR = Math.sqrt(ERR);
        }
        return FIN;
    }

    // Kronrod Rule
    private static double KRNRDT(double A, double B,
                                 DoubleUnaryOperator integrand,
                                 double[] ERR) {
        double T, CEN, FC, WID, RESG, RESK;

        // The abscissae and weights are given for the interval (-1,1);
        // only positive abscissae and corresponding weights are given.
        final int N = 11;

        final double[] WG = {
                0.0, 0.2729250867779007, 0.05566856711617449,
                0.1255803694649048, 0.1862902109277352,
                0.2331937645919914, 0.2628045445102478
        };
        final double[] XGK = {
                0.0, 0.0000000000000000, 0.9963696138895427,
                0.9782286581460570, 0.9416771085780681,
                0.8870625997680953, 0.8160574566562211,
                0.7301520055740492, 0.6305995201619651,
                0.5190961292068118, 0.3979441409523776,
                0.2695431559523450, 0.1361130007993617
        };
        final double[] WGK = {
                0.0, 0.1365777947111183, 0.00976544104596129,
                0.02715655468210443, 0.04582937856442671,
                0.06309742475037484, 0.07866457193222764,
                0.09295309859690074, 0.1058720744813894,
                0.1167395024610472, 0.1251587991003195,
                0.1312806842298057, 0.1351935727998845
        };

        WID = (B - A) / 2.0;
        CEN = (B + A) / 2.0;

        FC = integrand.applyAsDouble(CEN);

        RESG = FC * WG[0 + 1];
        RESK = FC * WGK[0 + 1];

        for (int J = 1; J <= N; J++) {
            T = WID * XGK[J + 1];
            FC = integrand.applyAsDouble(CEN - T)
                    + integrand.applyAsDouble(CEN + T);
            RESK = RESK + WGK[J + 1] * FC;
            if ((J - 2 * (J / 2)) == 0) RESG = RESG + WG[1 + J / 2] * FC;
        }
        double result = WID * RESK;
        ERR[0] = Math.abs(WID * (RESK - RESG));
        return result;
    }

    // Student t Distribution Function
    private static double STUDNT(int NU, double T) {
        final double ZRO = 0.0, ONE = 1.0;
        double CSSTHE, SNTHE, POLYN, TT, TS, RN;
        double result;

        if (NU < 1) result = PHID(T);
        else if (NU == 1) result = (1 + 2.0 * Math.atan(T) / PI) / 2.0;
        else if (NU == 2) result = (1 + T / Math.sqrt(2.0 + T * T)) / 2.0;
        else {
            TT = T * T;
            CSSTHE = 1 / (1 + TT / (double) NU);
            POLYN = 1;
            for (int J = NU - 2; J >= 2; J -= 2) {
                POLYN = 1.0 + (J - 1.0) * CSSTHE * POLYN / (double) J;
            }
            if ((NU - 2 * (NU / 2)) == 1) {
                RN = NU;
                TS = T / Math.sqrt(RN);
                result = (1.0 + 2.0 * (Math.atan(TS)
                        + TS * CSSTHE * POLYN) / PI) / 2.0;
            } else {
                SNTHE = T / Math.sqrt(NU + TT);
                result = (1 + SNTHE * POLYN) / 2.0;
            }
            result = Math.max(ZRO, Math.min(result, ONE));
        }
        return result;
    }

    // A function for computing bivariate t probabilities.
    private static double BVTL(int NU, double DH, double DK, double R) {
        double TPI, ORS, HRK, KRH, BVT, SNU;
        double GMPH, GMPK, XNKH, XNHK, QHRK, HKN, HPK, HKRN;
        double BTNCKH, BTNCHK, BTPDKH, BTPDHK, ONE, EPS;
        double result;
        ONE = 1;
        EPS = 1e-15;
        if (NU < 1) result = ND2(-DH, -DK, R);
        else if ((1 - R) <= EPS) result = STUDNT(NU, Math.min(DH, DK));
        else if ((R + 1) <= EPS) {
            if (DH > -DK) result = STUDNT(NU, DH) - STUDNT(NU, -DK);
            else result = 0.0;
        } else {
            final int HS = (int) SIGN(ONE, DH - R * DK);
            final int KS = (int) SIGN(ONE, DK - R * DH);
            TPI = twoPi;
            SNU = (double) NU;
            SNU = Math.sqrt(SNU);
            ORS = 1.0 - R * R;
            HRK = DH - R * DK;
            KRH = DK - R * DH;
            if ((Math.abs(HRK) + ORS) > 0) {
                XNHK = HRK * HRK / (HRK * HRK + ORS * (NU + DK * DK));
                XNKH = KRH * KRH / (KRH * KRH + ORS * (NU + DH * DH));
            } else {
                XNHK = 0.0;
                XNKH = 0.0;
            }

            if ((NU - 2 * (NU / 2)) == 0) {
                BVT = Math.atan2(Math.sqrt(ORS), -R) / TPI;
                GMPH = DH / Math.sqrt(16 * (NU + DH * DH));
                GMPK = DK / Math.sqrt(16 * (NU + DK * DK));
                BTNCKH = 2 * Math.atan2(Math.sqrt(XNKH), Math.sqrt(1 - XNKH)) / PI;
                BTPDKH = 2 * Math.sqrt(XNKH * (1 - XNKH)) / PI;
                BTNCHK = 2 * Math.atan2(Math.sqrt(XNHK), Math.sqrt(1 - XNHK)) / PI;
                BTPDHK = 2 * Math.sqrt(XNHK * (1 - XNHK)) / PI;
                for (int J = 1; J <= NU / 2; ++J) {
                    BVT = BVT + GMPH * (1 + KS * BTNCKH);
                    BVT = BVT + GMPK * (1 + HS * BTNCHK);
                    BTNCKH = BTNCKH + BTPDKH;
                    BTPDKH = 2 * J * BTPDKH * (1 - XNKH) / (2 * J + 1);
                    BTNCHK = BTNCHK + BTPDHK;
                    BTPDHK = 2 * J * BTPDHK * (1 - XNHK) / (2 * J + 1);
                    GMPH = GMPH * (2 * J - 1) / (2 * J * (1 + DH * DH / NU));
                    GMPK = GMPK * (2 * J - 1) / (2 * J * (1 + DK * DK / NU));
                }
            } else {
                QHRK = Math.sqrt(DH * DH + DK * DK - 2 * R * DH * DK + NU * ORS);
                HKRN = DH * DK + R * NU;
                HKN = DH * DK - NU;
                HPK = DH + DK;
                BVT = Math.atan2(-SNU * (HKN * QHRK + HPK * HKRN),
                        HKN * HKRN - NU * HPK * QHRK) / TPI;
                if (BVT < -EPS) BVT = BVT + 1;
                GMPH = DH / (TPI * SNU * (1 + DH * DH / NU));
                GMPK = DK / (TPI * SNU * (1 + DK * DK / NU));
                BTNCKH = Math.sqrt(XNKH);
                BTPDKH = BTNCKH;
                BTNCHK = Math.sqrt(XNHK);
                BTPDHK = BTNCHK;
                for (int J = 1; J <= (NU - 1) / 2; ++J) {
                    BVT = BVT + GMPH * (1 + KS * BTNCKH);
                    BVT = BVT + GMPK * (1 + HS * BTNCHK);
                    BTPDKH = (2 * J - 1) * BTPDKH * (1 - XNKH) / (2 * J);
                    BTNCKH = BTNCKH + BTPDKH;
                    BTPDHK = (2 * J - 1) * BTPDHK * (1 - XNHK) / (2 * J);
                    BTNCHK = BTNCHK + BTPDHK;
                    GMPH = 2 * J * GMPH / ((2 * J + 1) * (1 + DH * DH / NU));
                    GMPK = 2 * J * GMPK / ((2 * J + 1) * (1 + DK * DK / NU));
                }
            }
            result = BVT;
        }

        return result;
    }

    // Computes Plackett formula integrand
    private static double PNTGND(int NUC, double BA, double BB, double BC,
                                 double RA, double RB, double R, double RR) {
        double DT, FT, BT, result;

        result = 0.0;
        DT = RR * (RR - (RA - RB) * (RA - RB) - 2 * RA * RB * (1 - R));
        if (DT > 0) {
            BT = (BC * RR + BA * (R * RB - RA) + BB * (R * RA - RB))
                    / Math.sqrt(DT);
            FT = (BA - R * BB) * (BA - R * BB) / RR + BB * BB;
            if (NUC < 1) {
                if ((BT > -10) && (FT < 100)) {
                    result = Math.exp(-FT / 2);
                    if (BT < 10) result = result * PHID(BT);
                } else {
                    FT = Math.sqrt(1 + FT / NUC);
                    result = STUDNT(NUC, BT / FT) / Math.pow(FT, NUC);
                }
            }
        }
        return result;
    }

    private static double ND2(double a, double b, double rho) {
        // A function for computing bivariate normal probabilities.
        double result, DK, DH, R;
        int I, IS, LG, NG;

        final double[][] XL = {
                {0.0, 0.0, 0.0, 0.0},
                {0.0, -0.9324695142031522, -0.9815606342467191, -0.9931285991850949},
                {0.0, -0.6612093864662647, -0.9041172563704750, -0.9639719272779138},
                {0.0, -0.2386191860831970, -0.7699026741943050, -0.9122344282513259},
                {0.0, 0.0, -0.5873179542866171, -0.8391169718222188},
                {0.0, 0.0, -0.3678314989981802, -0.7463319064601508},
                {0.0, 0.0, -0.1252334085114692, -0.6360536807265150},
                {0.0, 0.0, 0.0, -0.5108670019508271},
                {0.0, 0.0, 0.0, -0.3737060887154196},
                {0.0, 0.0, 0.0, -0.2277858511416451},
                {0.0, 0.0, 0.0, -0.07652652113349733}
        };
        final double[][] WL = {
                {0.0, 0.0, 0.0, 0.0},
                {0.0, 0.1713244923791705, 0.0471753363865111, 0.01761400713915212},
                {0.0, 0.3607615730481384, 0.1069393259953183, 0.04060142980038694},
                {0.0, 0.4679139345726904, 0.1600783285433464, 0.06267204833410906},
                {0.0, 0.0, 0.2031674267230659, 0.08327674157670475},
                {0.0, 0.0, 0.2334925365383547, 0.1019301198172404},
                {0.0, 0.0, 0.2491470458134029, 0.1181945319615184},
                {0.0, 0.0, 0.0, 0.1316886384491766},
                {0.0, 0.0, 0.0, 0.1420961093183821},
                {0.0, 0.0, 0.0, 0.1491729864726037},
                {0.0, 0.0, 0.0, 0.1527533871307259}
        };
        double AS, AA, BB, C, D, RS, XS, BVN;
        double SN, ASR, H, K, BS, HS, HK;

        R = rho;
        DH = a;
        DK = b;

        if (Math.abs(R) < 0.3) {
            NG = 1;
            LG = 3;
        } else if (Math.abs(R) < 0.75) {
            NG = 2;
            LG = 6;
        } else {
            NG = 3;
            LG = 10;
        }
        H = DH;
        K = DK;
        HK = H * K;

        BVN = 0.0;
        if (Math.abs(R) < 0.925) {
            if (Math.abs(R) > 0) {
                HS = (H * H + K * K) / 2;
                ASR = Math.asin(R);
                for (I = 1; I <= LG; I++) {
                    for (IS = -1; IS <= 1; IS = IS + 2) {
                        SN = Math.sin(ASR * (IS * XL[I][NG] + 1) / 2);
                        BVN = BVN + WL[I][NG]
                                * Math.exp((SN * HK - HS) / (1.0 - SN * SN));
                    }
                }
                BVN = BVN * ASR / (2 * twoPi);
            }

            BVN = BVN + PHID(-H) * PHID(-K);
        } else {
            if (R < 0) {
                K = -K;
                HK = -HK;
            }

            if (Math.abs(R) < 1) {
                AS = (1 - R) * (1 + R);
                AA = Math.sqrt(AS);

                BS = (H - K) * (H - K);
                C = (4 - HK) / 8;
                D = (12 - HK) / 16;
                ASR = -(BS / AS + HK) / 2;
                if (ASR > -100)
                    BVN = AA * Math.exp(ASR) * (1 - C * (BS - AS)
                            * (1 - D * BS / 5) / 3 + C * D * AS * AS / 5);
                if (-HK < 100) {
                    BB = Math.sqrt(BS);
                    BVN = BVN - Math.exp(-HK / 2) * sqrtTwoPi
                            * PHID(-BB / AA) * BB
                            * (1 - C * BS * (1 - D * BS / 5) / 3);
                }
                AA = AA / 2;
                for (I = 1; I <= LG; I++) {
                    for (IS = -1; IS <= 1; IS = IS + 2) {
                        XS = (AA * (IS * XL[I][NG] + 1))
                                * (AA * (IS * XL[I][NG] + 1));
                        RS = Math.pow((1 - XS), 2);
                        ASR = -(BS / XS + HK) / 2;
                        if (ASR > -100) {
                            BVN = BVN + AA * WL[I][NG] * Math.exp(ASR)
                                    * (Math.exp(-HK * (1 - RS) / (2 * (1 + RS))) / RS
                                    - (1 + C * XS * (1 + D * XS)));
                        }
                    }
                }
                BVN = -BVN / twoPi;
            }
            if (R > 0) {
                BVN = BVN + PHID(-Math.max(H, K));
            } else {
                BVN = -BVN;
                if (K > H) BVN = BVN + PHID(K) - PHID(H);
            }
        }

        result = BVN;

        return result;
    }
}
