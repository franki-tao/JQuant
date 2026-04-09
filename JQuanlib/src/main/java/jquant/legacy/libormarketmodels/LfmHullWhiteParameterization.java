package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.Date;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.*;
import static jquant.math.matrixutilities.MatrixUtil.pseudoSqrt;

//! %Libor market model parameterization based on Hull White paper
/*! Hull, John, White, Alan, 1999, Forward Rate Volatilities, Swap Rate
    Volatilities and the Implementation of the Libor Market Model
    (<http://www.rotman.utoronto.ca/~amackay/fin/libormktmodel2.pdf>)

    \test the correctness is tested by Monte-Carlo reproduction of
          caplet & ratchet npvs and comparison with Black pricing.
*/
public class LfmHullWhiteParameterization extends LfmCovarianceParameterization {
    protected Matrix diffusion_, covariance_;
    protected List<Double> fixingTimes_;

    public LfmHullWhiteParameterization(final LiborForwardModelProcess process,
                                        final OptionletVolatilityStructure capletVol,
                                        final Matrix correlation, //= Matrix(),
                                        int factors //= 1
    ) {
        super(process.size(), factors);
        diffusion_ = new Matrix(size_ - 1, factors_);
        fixingTimes_ = process.fixingTimes();
        Matrix sqrtCorr = new Matrix(size_ - 1, factors_, 1.0);
        if (correlation.rows() == 0) {
            QL_REQUIRE(factors_ == 1,
                    "correlation matrix must be given for " +
                            "multi factor models");
        } else {
            QL_REQUIRE(correlation.rows() == size_ - 1
                            && correlation.rows() == correlation.cols(),
                    "wrong dimesion of the correlation matrix");

            QL_REQUIRE(factors_ <= size_ - 1,
                    "too many factors for given LFM process");

            Matrix tmpSqrtCorr = pseudoSqrt(correlation,
                    SalvagingAlgorithm.Type.Spectral);

            // reduce to n factor model
            // "Reconstructing a valid correlation matrix from invalid data"
            // (<http://www.quarchome.org/correlationmatrix.pdf>)
            for (int i = 0; i < size_ - 1; ++i) {
                double temp = 0d;
                for (int j = 0; j < factors_; j++) {
                    temp += tmpSqrtCorr.get(i, j) * tmpSqrtCorr.get(i, j);
                }
                double p = FastMath.sqrt(temp);
                for (int j = 0; j < factors_; j++) {
                    sqrtCorr.set(i, j, tmpSqrtCorr.get(i, j) / p);
                }
            }
        }
        List<Double> lambda = new ArrayList<>();
        final List<Double> fixingTimes = process.fixingTimes();
        final List<Date> fixingDates = process.fixingDates();

        for (int i = 1; i < size_; ++i) {
            double cumVar = 0.0;
            for (int j = 1; j < i; ++j) {
                cumVar += lambda.get(i - j - 1) * lambda.get(i - j - 1)
                        * (fixingTimes.get(j + 1) - fixingTimes.get(j));
            }

            final double vol = capletVol.volatility(fixingDates.get(i), 0.0, false);
            final double var = vol * vol
                    * capletVol.dayCounter().yearFraction(fixingDates.get(0),
                    fixingDates.get(i), new Date(), new Date());

            lambda.add(Math.sqrt((var - cumVar)
                    / (fixingTimes.get(1) - fixingTimes.get(0))));

            for (int q = 0; q < factors_; ++q) {
                diffusion_.set(i - 1, q, sqrtCorr.get(i - 1, q) * lambda.get(lambda.size() - 1));
            }
        }

        covariance_ = diffusion_.multipy(transpose(diffusion_));
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        Matrix tmp = new Matrix(size_, factors_, 0.0);
        final int m = nextIndexReset(t);

        for (int k = m; k < size_; ++k) {
            for (int q = 0; q < factors_; ++q) {
                tmp.set(k, q, diffusion_.get(k - m, q));
            }
        }
        return tmp;
    }

    @Override
    public Matrix covariance(double t, final Array x) {
        Matrix tmp = new Matrix(size_, size_, 0.0);
        final int m = nextIndexReset(t);

        for (int k = m; k < size_; ++k) {
            for (int i = m; i < size_; ++i) {
                tmp.set(k, i, covariance_.get(k - m, i - m));
            }
        }

        return tmp;
    }

    @Override
    public Matrix integratedCovariance(double t, final Array x) {
        Matrix tmp = new Matrix(size_, size_, 0.0);

        int last = lower_bound(fixingTimes_, t);
        for (int i = 0; i < last; ++i) {
            final double dt = ((i + 1 < last) ? fixingTimes_.get(i + 1) : t)
                    - fixingTimes_.get(i);

            for (int k = i; k < size_ - 1; ++k) {
                for (int l = i; l < size_ - 1; ++l) {
                    tmp.addEq(k + 1, l + 1, covariance_.get(k - i, l - i) * dt);
                }
            }
        }

        return tmp;
    }

    protected int nextIndexReset(double t) {
        return upper_bound(fixingTimes_, t);
    }
}
