package jquant.methods.finitedifferences.meshers.impl;

import jquant.math.ode.AdaptiveRungeKutta;
import jquant.math.ode.OdeFct1d;

import java.util.List;

import static jquant.math.MathUtils.squared;

public class OdeIntegrationFct {
    private AdaptiveRungeKutta rk_;
    private List<Double> points_, betas_;

    public OdeIntegrationFct(List<Double> points, List<Double> betas, double tol) {
        rk_ = new AdaptiveRungeKutta(tol, 1.0e-4, 0.0);
        points_ = points;
        betas_ = betas;
    }

    public double solve(double a, double y0, double x0, double x1) {
        OdeFct1d odeFct = new OdeFct1d() {
            @Override
            public double value(double x, double t) {
                return jac(a, x, t);
            }
        };
        return rk_.value(odeFct, y0, x0, x1);
    }

    private double jac(double a, double b, double y) {
        double s = 0.0;
        for (int i = 0; i < points_.size(); ++i) {
            s += 1.0 / (betas_.get(i) + squared(y - points_.get(i)));
        }
        return a / Math.sqrt(s);
    }
}
