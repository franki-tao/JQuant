package jquant.methods.lattices;

import jquant.StochasticProcess1D;
import jquant.TimeGrid;
import jquant.math.CommonUtil;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Recombining trinomial tree class
/*! This class defines a recombining trinomial tree approximating a
    1-D stochastic process.
    \warning The diffusion term of the SDE must be independent of the
             underlying process.

    \ingroup lattices
*/
public class TrinomialTree {
    public enum Branches {branches}

    protected List<Branching> branchings_;
    protected double x0_;
    protected List<Double> dx_;
    protected TimeGrid timeGrid_;
    private int columns_;

    // isPositive = false
    public TrinomialTree(StochasticProcess1D process, TimeGrid timeGrid, boolean isPositive) {
        columns_ = timeGrid.size();
        dx_ = CommonUtil.ArrayInit(1, 0.0);
        timeGrid_ = timeGrid;
        x0_ = process.x0();
        int nTimeSteps = timeGrid.size() - 1;
        QL_REQUIRE(nTimeSteps > 0, "null time steps for trinomial tree");

        int jMin = 0;
        int jMax = 0;

        for (int i = 0; i < nTimeSteps; i++) {
            double t = timeGrid.get(i);
            double dt = timeGrid.dt(i);

            //Variance must be independent of x
            double v2 = process.variance(t, 0.0, dt);
            double v = Math.sqrt(v2);
            dx_.add(v * Math.sqrt(3.0));

            Branching branching = new Branching();
            for (int j = jMin; j <= jMax; j++) {
                double x = x0_ + j * dx_.get(i);
                double m = process.expectation(t, x, dt);
                int temp = (int) (Math.floor((m - x0_) / dx_.get(i + 1) + 0.5));

                if (isPositive) {
                    while (x0_ + (temp - 1) * dx_.get(i + 1) <= 0) {
                        temp++;
                    }
                }

                double e = m - (x0_ + temp * dx_.get(i + 1));
                double e2 = e * e;
                double e3 = e * Math.sqrt(3.0);

                double p1 = (1.0 + e2 / v2 - e3 / v) / 6.0;
                double p2 = (2.0 - e2 / v2) / 3.0;
                double p3 = (1.0 + e2 / v2 + e3 / v) / 6.0;

                branching.add(temp, p1, p2, p3);
            }
            branchings_.add(branching);

            jMin = branching.jMin();
            jMax = branching.jMax();
        }
    }

    public int columns() {
        return columns_;
    }

    public double dx(int i) {
        return dx_.get(i);
    }

    public final TimeGrid timeGrid() {
        return timeGrid_;
    }

    public int size(int i) {
        return i == 0 ? 1 : branchings_.get(i - 1).size();
    }

    public double underlying(int i, int index) {
        if (i == 0)
            return x0_;
        else
            return x0_ + (branchings_.get(i - 1).jMin() + (index)) * dx(i);
    }

    public int descendant(int i, int index, int branch) {
        return branchings_.get(i).descendant(index, branch);
    }

    public double probability(int i, int j, int b) {
        return branchings_.get(i).probability(j, b);
    }
}
