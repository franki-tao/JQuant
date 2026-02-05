package jquant.models.volatility;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.optimization.impl.LeastSquareProblem;

import java.util.List;

public class FitAcfProblem extends LeastSquareProblem {
    private double A2_;
    private Array acf_;
    private List<Integer> idx_;

    public FitAcfProblem(double A2, Array acf, List<Integer> idx) {
        A2_ = A2;
        acf_ = new Array(acf);
        idx_ = idx;
    }

    @Override
    public int size() {
        return idx_.size();
    }

    @Override
    public void targetAndValue(final Array x, Array target, Array fct2fit) {
        double A4 = acf_.get(0) + A2_ * A2_;
        double gamma = x.get(0);
        double beta = x.get(1);
        target.set(0, A2_ * A2_ / A4);
        fct2fit.set(0,
                (1 - 3 * gamma * gamma - 2 * beta * beta + 4 * beta * gamma)
                        / (3 * (1 - gamma * gamma)));
        target.set(1, acf_.get(1) / A4);
        fct2fit.set(1, gamma * (1 - fct2fit.get(0)) - beta);
        for (int i = 2; i < idx_.size(); ++i) {
            target.set(i, acf_.get(idx_.get(i)) / A4);
            fct2fit.set(i, Math.pow(gamma, (int) idx_.get(i) - 1) * fct2fit.get(1));
        }
    }

    @Override
    public void targetValueAndGradient(final Array x,
                                       Matrix grad_fct2fit,
                                       Array target,
                                       Array fct2fit) {
        double A4 = acf_.get(0) + A2_ * A2_;
        double gamma = x.get(0);
        double beta = x.get(1);
        target.set(0, A2_ * A2_ / A4);
        double w1 = (1 - 3 * gamma * gamma - 2 * beta * beta + 4 * beta * gamma);
        double w2 = (1 - gamma * gamma);
        fct2fit.set(0, w1 / (3 * w2));
        grad_fct2fit.set(0, 0, (2.0 / 3.0) * ((2 * beta - 3 * gamma) * w2 + 2 * w1 * gamma) / (w2 * w2));
        grad_fct2fit.set(0, 1, (4.0 / 3.0) * (gamma - beta) / w2);
        target.set(1, acf_.get(1) / A4);
        fct2fit.set(1, gamma * (1 - fct2fit.get(0)) - beta);
        grad_fct2fit.set(1, 0, (1 - fct2fit.get(0)) - gamma * grad_fct2fit.get(0, 0));
        grad_fct2fit.set(1, 1, -gamma * grad_fct2fit.get(0, 1) - 1);
        for (int i = 2; i < idx_.size(); ++i) {
            target.set(i, acf_.get(idx_.get(i)) / A4);
            w1 = Math.pow(gamma, (int) idx_.get(i) - 1);
            fct2fit.set(i, w1 * fct2fit.get(1));
            grad_fct2fit.set(i, 0, (idx_.get(i) - 1) * (w1 / gamma) * fct2fit.get(1) + w1 * grad_fct2fit.get(1, 0));
            grad_fct2fit.set(i, 1, w1 * grad_fct2fit.get(1, 1));
        }
    }
}
