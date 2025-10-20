package jquant.math.matrixutilities;

import jquant.math.CommonUtil;
import jquant.math.Matrix;

import java.util.List;

/*! Given a collection of vectors, w_i, find a collection of vectors x_i such that
x_i is orthogonal to w_j for i != j, and <x_i, w_i> = <w_i, w_i>

This is done by performing GramSchmidt on the other vectors and then projecting onto
the orthogonal space.

This class is tested in

    MatricesTest::testOrthogonalProjection();
*/
public class OrthogonalProjections {
    //! inputs
    private Matrix originalVectors_;
    private double multiplierCutoff_;
    private int numberVectors_;
    private int numberValidVectors_;
    private int dimension_;

    //!outputs
    private List<Boolean> validVectors_;
    private List<List<Double>> projectedVectors_;

    //!workspace
    private Matrix orthoNormalizedVectors_;

    public OrthogonalProjections(final Matrix originalVectors, double multiplierCutOff, double tolerance) {
        originalVectors_ = originalVectors;
        multiplierCutoff_ = multiplierCutOff;
        numberVectors_ = originalVectors.rows();
        dimension_ = originalVectors.cols();
        validVectors_ = CommonUtil.ArrayInit(originalVectors.rows(), true); // opposite way round from vector constructor
        orthoNormalizedVectors_ = new Matrix(originalVectors.rows(), originalVectors.cols(), Double.NaN);

        List<Double> currentVector = CommonUtil.ArrayInit(dimension_);
        for (int j = 0; j < numberVectors_; ++j) {
            if (validVectors_.get(j)) {
                for (int k = 0; k < numberVectors_; ++k) // create an orthormal basis not containing j
                {
                    for (int m = 0; m < dimension_; ++m)
                        orthoNormalizedVectors_.set(k, m, originalVectors.get(k, m));

                    if (k != j && validVectors_.get(k)) {
                        for (int l = 0; l < k; ++l) {
                            if (validVectors_.get(l) && l != j) {
                                double dotProduct = innerProduct(orthoNormalizedVectors_, k,
                                        orthoNormalizedVectors_, l);
                                for (int n = 0; n < dimension_; ++n)
                                    orthoNormalizedVectors_.substractEq(k, n, dotProduct * orthoNormalizedVectors_.get(l, n));
                                // orthoNormalizedVectors_[k][n] -= dotProduct * orthoNormalizedVectors_[l][n];
                            }
                        }

                        double normBeforeScaling = norm(orthoNormalizedVectors_, k);

                        if (normBeforeScaling < tolerance) {
                            validVectors_.set(k, false);
                        } else {
                            double normBeforeScalingRecip = 1.0 / normBeforeScaling;
                            for (int m = 0; m < dimension_; ++m)
                                orthoNormalizedVectors_.multipyEq(k, m, normBeforeScalingRecip);
                        } // end of else (norm < tolerance)

                    } // end of if k !=j && validVectors_[k])

                } // end of  for (Size k=0; k< numberVectors_; ++k)

                // we now have an o.n. basis for everything except  j

                double prevNormSquared = normSquared(originalVectors_, j);


                for (int r = 0; r < numberVectors_; ++r)
                    if (validVectors_.get(r) && r != j) {
                        double dotProduct =
                                innerProduct(orthoNormalizedVectors_, j, orthoNormalizedVectors_, r);

                        for (int s = 0; s < dimension_; ++s)
                            orthoNormalizedVectors_.substractEq(j, s, dotProduct * orthoNormalizedVectors_.get(r, s));
                        // orthoNormalizedVectors_[j][s] -= dotProduct * orthoNormalizedVectors_[r][s];
                    }

                double projectionOnOriginalDirection =
                        innerProduct(originalVectors_, j, orthoNormalizedVectors_, j);
                double sizeMultiplier = prevNormSquared / projectionOnOriginalDirection;

                if (Math.abs(sizeMultiplier) < multiplierCutoff_) {
                    for (int t = 0; t < dimension_; ++t)
                        currentVector.set(t, orthoNormalizedVectors_.get(j, t) * sizeMultiplier);
                    // currentVector[t] = orthoNormalizedVectors_[j][t] * sizeMultiplier;

                } else
                    validVectors_.set(j, false);


            } // end of  if (validVectors_[j])

            projectedVectors_.add(currentVector);


        } // end of j loop

        numberValidVectors_ = 0;
        for (int i = 0; i < numberVectors_; ++i)
            numberValidVectors_ += validVectors_.get(i) ? 1 : 0;

    }

    public final List<Boolean> validVectors() {
        return validVectors_;
    }

    public final List<Double> GetVector(int index) {
        return projectedVectors_.get(index);
    }

    public int numberValidVectors() {
        return numberValidVectors_;
    }

    private double innerProduct(final Matrix v, int row1, final Matrix w, int row2) {

        double x = 0.0;
        for (int i = 0; i < v.cols(); ++i)
            x += v.get(row1, i) * w.get(row2, i);

        return x;
    }

    private double normSquared(final Matrix v, int row) {
        double x = 0.0;
        for (int i = 0; i < v.cols(); ++i)
            x += v.get(row, i) * v.get(row, i);
        return x;
    }

    private double norm(final Matrix v, int row) {
        return Math.sqrt(normSquared(v, row));
    }
}
