package jquant.models.volatility;

import jquant.math.Array;
import jquant.math.optimization.CostFunction;

import java.util.List;

public class Garch11CostFunction extends CostFunction {
    private List<Double> r2_;

    public Garch11CostFunction(final List<Double> r2) {
        r2_ = r2;
    }

    @Override
    public double value(final Array x) {
        double retval = 0.0;
        double sigma2 = 0;
        double u2 = 0;
        for (double r2 : r2_) {
            sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
            u2 = r2;
            retval += Math.log(sigma2) + u2 / sigma2;
        }
        return retval / (2.0 * r2_.size());
    }

    @Override
    public Array values(final Array x) {
        Array retval = new Array(r2_.size());
        double sigma2 = 0;
        double u2 = 0;
        int i = 0;
        for (double r2 : r2_) {
            sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
            u2 = r2;
            retval.set(i++, (Math.log(sigma2) + u2 / sigma2) / (2.0 * r2_.size()));
        }
        return retval;
    }

    @Override
    public void gradient(Array grad, final Array x) {
        grad.fill(0, grad.size(), 0d);
        double sigma2 = 0;
        double u2 = 0;
        double sigma2prev = sigma2;
        double u2prev = u2;
        double norm = 2.0 * r2_.size();
        for (double r2 : r2_) {
            sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
            u2 = r2;
            double w = (sigma2 - u2) / (sigma2 * sigma2);
            grad.addEq(0, w);
            grad.addEq(1, u2prev * w);
            grad.addEq(2, sigma2prev * w);
            u2prev = u2;
            sigma2prev = sigma2;
        }
        grad.transform(xx -> xx / norm);
    }

    @Override
    public double valueAndGradient(Array grad, final Array x) {
        grad.fill(0, grad.size(), 0d);
        double retval = 0.0;
        double sigma2 = 0;
        double u2 = 0;
        double sigma2prev = sigma2;
        double u2prev = u2;
        double norm = 2.0 * r2_.size();
        for (double r2 : r2_) {
            sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
            u2 = r2;
            retval += Math.log(sigma2) + u2 / sigma2;
            double w = (sigma2 - u2) / (sigma2 * sigma2);
            grad.addEq(0, w);
            grad.addEq(1, u2prev * w);
            grad.addEq(2, sigma2prev * w);
            u2prev = u2;
            sigma2prev = sigma2;
        }
        grad.transform(xx -> xx / norm);
        return retval / norm;
    }
}
