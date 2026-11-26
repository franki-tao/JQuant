package jquant.models.marketmodels;

import jquant.math.Matrix;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/swapforwardmappings.hpp/.cpp} port.
 *
 * <p>Utility functions for mapping between swap rate and forward rate.
 */
public final class SwapForwardMappings {

    private SwapForwardMappings() {
    }

    /** compute annuity of arbitrary swap-rate */
    public static double annuity(final CurveState cs,
                                 int startIndex,
                                 int endIndex,
                                 int numeraireIndex) {
        double annuity = 0.0;
        for (int i = startIndex; i < endIndex; ++i)
            annuity += cs.rateTaus().get(i) * cs.discountRatio(i + 1, numeraireIndex);
        return annuity;
    }

    /** compute derivative of swap-rate to underlying forward rate */
    public static double swapDerivative(final CurveState cs,
                                        int startIndex,
                                        int endIndex,
                                        int forwardIndex) {
        if (forwardIndex < startIndex)
            return 0.0;
        if (forwardIndex >= endIndex)
            return 0.0;

        double numerator = cs.discountRatio(startIndex, endIndex) - 1;
        double swapAnnuity = annuity(cs, startIndex, endIndex, endIndex);

        double ratio = cs.rateTaus().get(forwardIndex) /
                (1 + cs.rateTaus().get(forwardIndex) * cs.forwardRate(forwardIndex));

        double part1 = ratio * (numerator + 1) / swapAnnuity;
        double part2 = numerator / (swapAnnuity * swapAnnuity);

        if (forwardIndex >= 1)
            part2 *= ratio * annuity(cs, startIndex, forwardIndex, endIndex);
        else
            part2 = 0.0;

        return part1 - part2;
    }

    /** Returns the dsr[i]/df[j] jacobian between coterminal swap rates and forward rates */
    public static Matrix coterminalSwapForwardJacobian(final CurveState cs) {
        int n = cs.numberOfRates();
        List<Double> f = cs.forwardRates();
        List<Double> tau = cs.rateTaus();

        // coterminal floating leg values
        List<Double> a = new ArrayList<>();
        for (int k = 0; k < n; ++k)
            a.add(cs.discountRatio(k, n) - 1.0);

        Matrix jacobian = new Matrix(n, n, 0.0);
        for (int i = 0; i < n; ++i) {     // i = swap rate index
            for (int j = i; j < n; ++j) { // j = forward rate index
                double bi = cs.coterminalSwapAnnuity(n, i);
                double bj = cs.coterminalSwapAnnuity(n, j);
                jacobian.set(i, j,
                        tau.get(j) / cs.coterminalSwapAnnuity(j + 1, i)
                                + tau.get(j) / (1.0 + f.get(j) * tau.get(j))
                                * (-a.get(j) * bi + a.get(i) * bj) / (bi * bi));
            }
        }
        return jacobian;
    }

    /** Returns the Z matrix to switch base from forward to coterminal swap rates */
    public static Matrix coterminalSwapZedMatrix(final CurveState cs,
                                                 double displacement) {
        int n = cs.numberOfRates();
        Matrix zMatrix = coterminalSwapForwardJacobian(cs);
        List<Double> f = cs.forwardRates();
        List<Double> sr = cs.coterminalSwapRates();
        for (int i = 0; i < n; ++i)
            for (int j = i; j < n; ++j)
                zMatrix.set(i, j, zMatrix.get(i, j)
                        * (f.get(j) + displacement) / (sr.get(i) + displacement));
        return zMatrix;
    }

    /** Returns the dsr[i]/df[j] jacobian between coinitial swap rates and forward rates */
    public static Matrix coinitialSwapForwardJacobian(final CurveState cs) {
        int n = cs.numberOfRates();

        Matrix jacobian = new Matrix(n, n, 0.0);
        for (int i = 0; i < n; ++i)      // i = swap rate index
            for (int j = 0; j < n; ++j)  // j = forward rate index
                jacobian.set(i, j, swapDerivative(cs, 0, i + 1, j));

        return jacobian;
    }

    /** Returns the dsr[i]/df[j] jacobian between constant maturity swap rates and forward rates */
    public static Matrix cmSwapForwardJacobian(final CurveState cs,
                                               int spanningForwards) {
        int n = cs.numberOfRates();

        Matrix jacobian = new Matrix(n, n, 0.0);
        for (int i = 0; i < n; ++i)      // i = swap rate index
            for (int j = 0; j < n; ++j)  // j = forward rate index
                jacobian.set(i, j,
                        swapDerivative(cs, i, Math.min(n, i + spanningForwards), j));

        return jacobian;
    }

    /** Returns the Z matrix to switch base from forward to coinitial swap rates */
    public static Matrix coinitialSwapZedMatrix(final CurveState cs,
                                                double displacement) {
        int n = cs.numberOfRates();
        Matrix zMatrix = coinitialSwapForwardJacobian(cs);
        List<Double> f = cs.forwardRates();
        List<Double> sr = new ArrayList<>();

        for (int i = 0; i < n; ++i)
            sr.add(cs.cmSwapRate(0, i + 1));

        for (int i = 0; i < n; ++i)
            for (int j = i; j < n; ++j)
                zMatrix.set(i, j, zMatrix.get(i, j)
                        * (f.get(j) + displacement) / (sr.get(i) + displacement));
        return zMatrix;
    }

    /** Returns the Z matrix to switch base from forward to constant maturity swap rates */
    public static Matrix cmSwapZedMatrix(final CurveState cs,
                                         int spanningForwards,
                                         double displacement) {
        int n = cs.numberOfRates();
        Matrix zMatrix = cmSwapForwardJacobian(cs, spanningForwards);
        List<Double> f = cs.forwardRates();
        List<Double> sr = new ArrayList<>();

        for (int i = 0; i < n; ++i)
            sr.add(cs.cmSwapRate(i, spanningForwards));

        for (int i = 0; i < n; ++i)
            for (int j = i; j < n; ++j)
                zMatrix.set(i, j, zMatrix.get(i, j)
                        * (f.get(j) + displacement) / (sr.get(i) + displacement));
        return zMatrix;
    }

    /**
     * computes the implied vol of a swaption specified by two indices
     * using the freezing coefficients methodology.
     */
    public static double swaptionImpliedVolatility(final MarketModel volStructure,
                                                   int startIndex,
                                                   int endIndex) {
        QL_REQUIRE(startIndex < endIndex,
                "start index must be before end index in swaptionImpliedVolatility");

        LMMCurveState cs = new LMMCurveState(volStructure.evolution().rateTimes());
        cs.setOnForwardRates(volStructure.initialRates());
        double displacement = volStructure.displacements().get(0);

        Matrix cmsZed = cmSwapZedMatrix(cs, endIndex - startIndex, displacement);

        double variance = 0.0;

        int index = 0;

        final EvolutionDescription evolution = volStructure.evolution();
        int factors = volStructure.numberOfFactors();

        while (index < evolution.numberOfSteps()
                && startIndex >= evolution.firstAliveRate().get(index)) {
            final Matrix thisPseudo = volStructure.pseudoRoot(index);
            double thisVariance = 0.0;

            for (int f = 0; f < factors; ++f) {
                double sum = 0.0;

                for (int j = startIndex; j < endIndex; ++j) {
                    sum += cmsZed.get(startIndex, j) * thisPseudo.get(j, f);
                }
                thisVariance += sum * sum;
            }
            variance += thisVariance;
            ++index;
        }

        double expiry = evolution.rateTimes().get(startIndex);
        return Math.sqrt(variance / expiry);
    }
}
