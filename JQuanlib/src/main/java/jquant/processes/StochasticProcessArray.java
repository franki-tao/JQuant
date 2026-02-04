package jquant.processes;

import jquant.StochasticProcess;
import jquant.StochasticProcess1D;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.time.Date;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.transpose;
import static jquant.math.matrixutilities.MatrixUtil.pseudoSqrt;

//! %Array of correlated 1-D stochastic processes
/*! \ingroup processes */
public class StochasticProcessArray extends StochasticProcess {
    protected List<StochasticProcess1D> processes_;
    protected Matrix sqrtCorrelation_;

    public StochasticProcessArray(
            final List<StochasticProcess1D> processes,
            final Matrix correlation) {
        processes_ = processes;
        sqrtCorrelation_ = pseudoSqrt(correlation, SalvagingAlgorithm.Type.Spectral);
        QL_REQUIRE(!processes.isEmpty(), "no processes given");
        QL_REQUIRE(correlation.rows() == processes.size(),
                "mismatch between number of processes " +
                        "and size of correlation matrix");
        for (StochasticProcess1D process : processes_) {
            QL_REQUIRE(process != null, "null 1-D stochastic process");
            registerWith(process);
        }
    }

    @Override
    public int size() {
        return processes_.size();
    }

    @Override
    public Array initialValues() {
        Array tmp = new Array(size());
        for (int i = 0; i < size(); ++i)
            tmp.set(i, processes_.get(i).x0());
        return tmp;
    }

    @Override
    public Array drift(double t, final Array x) {
        Array tmp = new Array(size());
        for (int i = 0; i < size(); ++i)
            tmp.set(i, processes_.get(i).drift(t, x.get(i)));
        return tmp;
    }

    @Override
    public Array expectation(double t0, final Array x0, double dt) {
        Array tmp = new Array(size());
        for (int i = 0; i < size(); ++i)
            tmp.set(i, processes_.get(i).expectation(t0, x0.get(i), dt));
        return tmp;
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        Matrix tmp = new Matrix(sqrtCorrelation_.matrix);
        for (int i = 0; i < size(); ++i) {
            double sigma = processes_.get(i).diffusion(t, x.get(i));
            for (int j = 0; j < tmp.cols(); j++) {
                tmp.multipyEq(i, j, sigma);
            }
        }
        return tmp;
    }

    @Override
    public Matrix covariance(double t0, final Array x0, double dt) {
        Matrix tmp = stdDeviation(t0, x0, dt);
        return tmp.multipy(transpose(tmp));
    }

    @Override
    public Matrix stdDeviation(double t0, final Array x0, double dt) {
        Matrix tmp = new Matrix(sqrtCorrelation_.matrix);
        for (int i = 0; i < size(); ++i) {
            double sigma = processes_.get(i).stdDeviation(t0, x0.get(i), dt);
            for (int j = 0; j < tmp.cols(); j++) {
                tmp.multipyEq(i, j, sigma);
            }
        }
        return tmp;
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        Array tmp = new Array(size());
        for (int i = 0; i < size(); ++i)
            tmp.set(i, processes_.get(i).apply(x0.get(i), dx.get(i)));
        return tmp;
    }

    @Override
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        final Array dz = sqrtCorrelation_.mutiply(dw);

        Array tmp = new Array(size());
        for (int i = 0; i < size(); ++i)
            tmp.set(i, processes_.get(i).evolve(t0, x0.get(i), dt, dz.get(i)));
        return tmp;
    }

    @Override
    public double time(final Date date) {
        return processes_.get(0).time(date);
    }

    public final StochasticProcess1D process(int i) {
        return processes_.get(i);
    }

    public Matrix correlation() {
        return sqrtCorrelation_.multipy(transpose(sqrtCorrelation_));
    }
}
