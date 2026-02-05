package jquant.methods.finitedifferences;

import jquant.math.TransformedGrid;

/*! \deprecated Part of the old FD framework; copy this function
                in your codebase if needed.
                Deprecated in version 1.37.
*/
public interface PdeSecondOrderParabolic {
    double diffusion(double t, double x);

    double drift(double t, double x);

    double discount(double t, double x);

    default void generateOperator(double t, final TransformedGrid tg, TridiagonalOperator L) {
        for (int i = 1; i < tg.size() - 1; i++) {
            double sigma = diffusion(t, tg.grid(i));
            double nu = drift(t, tg.grid(i));
            double r = discount(t, tg.grid(i));
            double sigma2 = sigma * sigma;

            double pd = -(sigma2 / tg.dxm(i) - nu) / tg.dx(i);
            double pu = -(sigma2 / tg.dxp(i) + nu) / tg.dx(i);
            double pm = sigma2 / (tg.dxm(i) * tg.dxp(i)) + r;
            L.setMidRow(i, pd, pm, pu);
        }
    }
}
