package jquant.processes;

import jquant.StochasticProcess;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.matrixutilities.SVD;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.time.Date;

import java.util.List;
import java.util.Map;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.transpose;
import static jquant.math.MathUtils.NULL_SIZE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.matrixutilities.MatrixUtil.*;

public abstract class JointStochasticProcess extends StochasticProcess {
    private static class CachingKey {
        public double t0_;
        public double dt_;

        public CachingKey(final double t0, final double dt) {
            t0_ = t0;
            dt_ = dt;
        }

        public boolean letter(final CachingKey key) {
            return t0_ < key.t0_
                    || (t0_ == key.t0_ && dt_ < key.dt_);
        }
    }

    private int size_;
    private int factors_;
    private int modelFactors_;
    private List<Integer> vsize_, vfactors_;
    private Map<CachingKey, Matrix> correlationCache_;
    protected List<StochasticProcess> l_;

    public JointStochasticProcess(List<StochasticProcess> l, int factors) {
        size_ = 0;
        modelFactors_ = 0;
        l_ = l;
        factors_ = factors;
        for (final StochasticProcess i : l_) {
            registerWith(i);
        }

        vsize_ = CommonUtil.ArrayInit(l_.size() + 1);
        vfactors_ = CommonUtil.ArrayInit(l_.size() + 1);
        int index = 0;
        for (final StochasticProcess i : l_) {
            vsize_.set(index, size_);
            size_ += i.size();
            vfactors_.set(index, modelFactors_);
            modelFactors_ += i.factors();
            index++;
        }
        vsize_.set(index, size_);
        vfactors_.set(index, modelFactors_);
        if (factors == NULL_SIZE) {
            factors_ = modelFactors_;
        } else {
            QL_REQUIRE(factors_ <= size_, "too many factors given");
        }
    }

    @Override
    public int size() {
        return size_;
    }

    @Override
    public int factors() {
        return factors_;
    }

    @Override
    public Array initialValues() {
        Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final Array pInitValues = l_.get(i).initialValues();
            int dest_idx = vsize_.get(i);
            for (int j = 0; j < pInitValues.size(); j++) {
                retVal.set(j + dest_idx, pInitValues.get(j));
            }
        }
        return retVal;
    }

    @Override
    public Array drift(double t, final Array x) {
        Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); ++i) {
            final Array pDrift = l_.get(i).drift(t, slice(x, i));
            int dest_idx = vsize_.get(i);
            for (int j = 0; j < pDrift.size(); j++) {
                retVal.set(j + dest_idx, pDrift.get(j));
            }
        }
        return retVal;
    }

    @Override
    public Array expectation(double t0, final Array x0, double dt) {
        Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); ++i) {
            final Array pExpectation = l_.get(i).expectation(t0, slice(x0, i), dt);
            int dest_idx = vsize_.get(i);
            for (int j = 0; j < pExpectation.size(); j++) {
                retVal.set(j + dest_idx, pExpectation.get(j));
            }
        }
        return retVal;
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        // might need some improvement in the future
        final double dt = 0.001;
        return pseudoSqrt(covariance(t, x, dt).multiply(1 / dt), SalvagingAlgorithm.Type.None);
    }

    @Override
    public Matrix covariance(double t0, final Array x0, double dt) {
        // get the model intrinsic covariance matrix
        Matrix retVal = new Matrix(size(), size(), 0.0);

        for (int j = 0; j < l_.size(); ++j) {

            final int vs = vsize_.get(j);
            final Matrix pCov = l_.get(j).covariance(t0, slice(x0, j), dt);

            for (int i = 0; i < pCov.rows(); ++i) {
                for (int k = 0; k < pCov.cols(); k++) {
                    retVal.set(vs + i, k + vs, pCov.get(i, k));
                }
            }
        }

        // add the cross model covariance matrix
        final Array volatility = Sqrt(retVal.diagonal());
        Matrix crossModelCovar = this.crossModelCorrelation(t0, x0);

        for (int i = 0; i < size(); ++i) {
            for (int j = 0; j < size(); ++j) {
                crossModelCovar.multipyEq(i, j, volatility.get(i) * volatility.get(j));
            }
        }

        retVal = retVal.add(crossModelCovar);

        return retVal;
    }

    @Override
    public Matrix stdDeviation(double t0, final Array x0, double dt) {
        return pseudoSqrt(covariance(t0, x0, dt), SalvagingAlgorithm.Type.None);
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final Array pArray = l_.get(i).apply(slice(x0, i), slice(dx, i));
            int dest_idx = vsize_.get(i);
            for (int j = 0; j < pArray.size(); j++) {
                retVal.set(j + dest_idx, pArray.get(j));
            }
        }
        return retVal;
    }

    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        Array dv = new Array(modelFactors_);

        if (correlationIsStateDependent() || !correlationCache_.containsKey(new CachingKey(t0, dt))) {
            Matrix cov = covariance(t0, x0, dt);
            final Array sqrtDiag = Sqrt(cov.diagonal());
            for (int i = 0; i < cov.rows(); ++i) {
                for (int j = i; j < cov.cols(); ++j) {
                    final double div = sqrtDiag.get(i) * sqrtDiag.get(j);
                    double tp = (div > 0) ? (cov.get(i, j) / div) : 0.0;
                    cov.set(i, j, tp);
                    cov.set(j, i, tp);
                }
            }

            Matrix diff = new Matrix(size(), modelFactors_, 0.0);

            for (int j = 0; j < l_.size(); ++j) {
                final int vs = vsize_.get(j);
                final int vf = vfactors_.get(j);

                Matrix stdDev = l_.get(j).stdDeviation(t0, slice(x0, j), dt);

                for (int i = 0; i < stdDev.rows(); ++i) {
                    double tmp = 0d;
                    for (int k = 0; k < stdDev.cols(); k++) {
                        tmp += stdDev.get(i, k) * stdDev.get(i, k);
                    }
                    final double vol = Math.sqrt(tmp);
                    if (vol > 0.0) {
                        for (int k = 0; k < stdDev.cols(); k++) {
                            stdDev.multipyEq(i, k, 1d / vol);
                        }
                    } else {
                        // keep the svd happy
                        for (int k = 0; k < stdDev.cols(); k++) {
                            stdDev.set(i, k, 100 * i * QL_EPSILON);
                        }
                    }
                }

                SVD svd = new SVD(stdDev);
                final Array s = svd.singularValues();
                Matrix w = new Matrix(s.size(), s.size(), 0.0);
                for (int i = 0; i < s.size(); ++i) {
                    if (Math.abs(s.get(i)) > Math.sqrt(QL_EPSILON)) {
                        w.set(i, i, 1.0 / s.get(i));
                    }
                }

                final Matrix inv = svd.U().multipy(w).multipy(transpose(svd.V()));

                for (int i = 0; i < stdDev.rows(); ++i) {
                    for (int k = 0; k < inv.cols(); k++) {
                        diff.set(i + vs, k + vf, inv.get(i, k));
                    }
                }
            }

            Matrix rs = rankReducedSqrt(cov, factors_, 1.0,
                    SalvagingAlgorithm.Type.Spectral);

            if (rs.cols() < factors_) {
                // less eigenvalues than expected factors.
                // fill the rest with zero's.
                Matrix tmp = new Matrix(cov.rows(), factors_, 0.0);
                for (int i = 0; i < cov.rows(); ++i) {
                    for (int j = 0; j < rs.cols(); j++) {
                        tmp.set(i, j, rs.get(i, j));
                    }
                }
                rs = tmp;
            }

            final Matrix m = transpose(diff).multipy(rs);

            if (!correlationIsStateDependent()) {
                correlationCache_.put(new CachingKey(t0, dt), m);
            }
            dv = m.mutiply(dw);
        } else {
            if (!correlationIsStateDependent()) {
                dv = correlationCache_.get(new CachingKey(t0, dt)).mutiply(dw);
            }
        }
        this.preEvolve(t0, x0, dt, dv);


        Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            Array dz = new Array(l_.get(i).factors());
            int dx = vfactors_.get(i);
            for (int j = dx, index = 0; j < dx + l_.get(i).factors(); j++, index++) {
                dz.set(index, dv.get(j));
            }
            Array x = new Array(l_.get(i).factors());
            dx = vsize_.get(i);
            for (int j = dx, index = 0; j < dx + l_.get(i).factors(); j++, index++) {
                x.set(index, x0.get(j));
            }
            final Array r = l_.get(i).evolve(t0, x, dt, dz);
            for (int j = 0; j < r.size(); j++) {
                retVal.set(j + dx, r.get(j));
            }
        }

        return this.postEvolve(t0, x0, dt, dv, retVal);
    }

    public abstract void preEvolve(double t0, final Array x0, double dt, final Array dw);

    public abstract Array postEvolve(double t0, final Array x0,
                                     double dt, final Array dw,
                                     final Array y0);

    public abstract double numeraire(double t, final Array x);

    public abstract boolean correlationIsStateDependent();

    public abstract Matrix crossModelCorrelation(double t0, final Array x0);

    public final List<StochasticProcess> constituents() {
        return l_;
    }

    @Override
    public void update() {
        // clear all caches
        correlationCache_.clear();

        super.update();
    }

    @Override
    public double time(final Date date) {
        QL_REQUIRE(!l_.isEmpty(), "process list is empty");

        return l_.get(0).time(date);
    }

    protected Array slice(final Array x, int i) {
        // cut out the ith process' variables
        int n = vsize_.get(i + 1) - vsize_.get(i);
        Array y = new Array(n);
        int start = vsize_.get(i);
        int end = vsize_.get(i + 1);
        for (int j = start, index = 0; j < end; j++, index++) {
            y.set(index, x.get(j));
        }
        return y;
    }
}
