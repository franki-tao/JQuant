package jquant.models.volatility.impl;

import jquant.math.Array;
import jquant.math.ReferencePkg;
import jquant.math.optimization.NonLinearLeastSquare;
import jquant.models.volatility.FitAcfConstraint;
import jquant.models.volatility.FitAcfProblem;
import jquant.models.volatility.Garch11Constraint;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.MathUtils.QL_EPSILON;

public class GarchImpl {
    private static final double tol_level = 1.0e-8;
    // Initial guess based on fitting ACF - initial guess for
    // fitting acf is a moment matching estimates for mean(r2),
    // acf(0), and acf(1).
    public static double initialGuess1(final Array acf, double mean_r2,
                                       ReferencePkg<Double> alpha, ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        double A21 = acf.get(1);
        double A4 = acf.get(0) + mean_r2*mean_r2;

        double A = mean_r2*mean_r2/A4; // 1/sigma^2
        double B = A21 / A4; // rho(1)

        double gammaLower = A <= 1./3. - tol_level ? Math.sqrt((1 - 3*A)/(3 - 3*A)) + tol_level : (tol_level);
        Garch11Constraint constraints = new Garch11Constraint(gammaLower, 1.0 - tol_level);

        double gamma = gammaLower + (1 - gammaLower) * 0.5;
        beta.setT(Math.min(gamma, Math.max(gamma * (1 - A) - B, 0.0)));
        alpha.setT(gamma - beta.getT());
        omega.setT(mean_r2 * (1 - gamma));

        if (Math.abs(A-0.5) < QL_EPSILON) {
            gamma = Math.max(gammaLower, -(1+4*B*B)/(4*B));
            beta.setT(Math.min(gamma, Math.max(gamma * (1 - A) - B, 0.0)));
            alpha.setT(gamma - beta.getT());
            omega.setT(mean_r2 * (1 - gamma));
        } else {
            if (A > 1.0 - QL_EPSILON) {
                gamma = Math.max(gammaLower, -(1+B*B)/(2*B));
                beta.setT(Math.min(gamma, Math.max(gamma * (1 - A) - B, 0.0)));
                alpha.setT(gamma - beta.getT());
                omega.setT(mean_r2 * (1 - gamma));
            } else {
                double D = (3*A-1)*(2*B*B+(1-A)*(2*A-1));
                if (D >= 0) {
                    double d = Math.sqrt(D);
                    double b = (B - d)/(2*A-1);
                    double g = 0;
                    if (b >= tol_level && b <= 1.0 - tol_level) {
                        g = (b + B) / (1 - A);
                    }
                    if (g < gammaLower) {
                        b = (B + d)/(2*A-1);
                        if (b >= tol_level && b <= 1.0 - tol_level) {
                            g = (b + B) / (1 - A);
                        }
                    }
                    if (g >= gammaLower) {
                        gamma = g;
                        beta.setT(Math.min(gamma, Math.max(gamma * (1 - A) - B, 0.0)));
                        alpha.setT(gamma - beta.getT());
                        omega.setT(mean_r2 * (1 - gamma));
                    }
                }
            }
        }

        List<Integer> idx = new ArrayList<>();
        int nCov = acf.size() - 1;
        for (int i = 0; i <= nCov; ++i) {
            if (i < 2 || (acf.get(i) > 0 && acf.get(i-1) > 0 && acf.get(i-1) > acf.get(i))) {
                idx.add(i);
            }
        }

        Array x = new Array(2);
        x.set(0, gamma);
        x.set(1, beta.getT());

        try {
            FitAcfConstraint c = new FitAcfConstraint(gammaLower, 1.0 - tol_level);
            NonLinearLeastSquare nnls = new NonLinearLeastSquare(c, 1e-4, 100);
            nnls.setInitialValue(x);
            FitAcfProblem pr = new FitAcfProblem(mean_r2, acf, idx);
            x = nnls.perform(pr);
            Array guess = new Array(3);
            guess.set(0, mean_r2 * (1 - x.get(0)));
            guess.set(1, x.get(0) - x.get(1));
            guess.set(2, x.get(1));
            if (constraints.test(guess)) {
                omega.setT(guess.get(0));
                alpha.setT(guess.get(1));
                beta.setT(guess.get(2));
            }
        } catch (Exception e) {
            // failed -- returning initial values
        }
        return gammaLower;
    }

    // Initial guess based on fitting ACF - initial guess for
    // fitting acf is an estimate of gamma = alpfa+beta based on
    // the property: acf(i+1) = gamma*acf(i) for i > 1.
    public static double initialGuess2 (final Array acf, double mean_r2,
                                        ReferencePkg<Double> alpha, ReferencePkg<Double> beta, ReferencePkg<Double> omega) {
        double A21 = acf.get(1);
        double A4 = acf.get(0) + mean_r2*mean_r2;
        double A = mean_r2*mean_r2/A4; // 1/sigma^2
        double B = A21 / A4; // rho(1)
        double gammaLower = A <= 1./3. - tol_level ? Math.sqrt((1 - 3*A)/(3 - 3*A)) + tol_level : (tol_level);
        Garch11Constraint constraints = new Garch11Constraint(gammaLower, 1.0 - tol_level);

        // ACF
        double gamma = 0;
        int nn = 0;
        List<Integer> idx = new ArrayList<>();
        int nCov = acf.size() - 1;
        for (int i = 0; i <= nCov; ++i) {
            if (i < 2) idx.add(i);
            if (i > 1 && acf.get(i) > 0 && acf.get(i-1) > 0 && acf.get(i-1) > acf.get(i)) {
                gamma += acf.get(i)/acf.get(i-1);
                nn++;
                idx.add(i);
            }
        }
        if (nn > 0)
            gamma /= nn;
        if (gamma < gammaLower) gamma = gammaLower;
        beta.setT(Math.min(gamma, Math.max(gamma * (1 - A) - B, 0.0)));
        omega.setT(mean_r2 * (1 - gamma));

        Array x = new Array(2);
        x.set(0, gamma);
        x.set(1, beta.getT());

        try {
            FitAcfConstraint c = new FitAcfConstraint(gammaLower, 1 - tol_level);
            NonLinearLeastSquare nnls = new NonLinearLeastSquare(c, 1e-4, 100);
            nnls.setInitialValue(x);
            FitAcfProblem pr = new FitAcfProblem(mean_r2, acf, idx);
            x = nnls.perform(pr);
            Array guess = new Array(3);
            guess.set(0, mean_r2 * (1 - x.get(0)));
            guess.set(1, x.get(0) - x.get(1));
            guess.set(2,  x.get(1));
            if (constraints.test(guess)) {
                omega.setT(guess.get(0));
                alpha.setT(guess.get(1));
                beta.setT(guess.get(2));
            }
        } catch (Exception e) {
            // failed -- returning initial values
        }
        return gammaLower;
    }
}
