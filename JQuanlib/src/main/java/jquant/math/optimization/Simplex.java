package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.ReferencePkg;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.optimization.impl.OptimizationUtil.computeSimplexSize;

/**
 * ! Multi-dimensional simplex class
 * ! This method is rather raw and requires quite a lot of
 *   computing resources, but it has the advantage that it does not
 *   need any evaluation of the cost function's gradient, and that
 *   it is quite easily implemented. First, we choose N+1
 *   starting points, given here by a starting point \f$
 *   \mathbf{P}_{0} \f$ and N points such that
 *   \f[
 *      \mathbf{P}_{\mathbf{i}}=\mathbf{P}_{0}+\lambda \mathbf{e}_{\mathbf{i}},
 *   \f]
 *   where \f$ \lambda \f$ is the problem's characteristic length scale). These
 *   points will form a geometrical form called simplex.
 *   The principle of the downhill simplex method is, at each
 *   iteration, to move the worst point (highest cost function value)
 *   through the opposite face to a better point. When the simplex
 *   seems to be constrained in a valley, it will be contracted
 *   downhill, keeping the best point unchanged.
 *
 *   \ingroup optimizers
 */
public class Simplex extends OptimizationMethod {
    private double lambda_;
    private List<Array> vertices_;
    private Array values_, sum_;
    /*! Constructor taking as input the characteristic length */
    public Simplex(double lambda) {lambda_ = lambda;}

    public double lambda()  { return lambda_; }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        // set up of the problem
        //Real ftol = endCriteria.functionEpsilon();    // end criteria on f(x) (see Numerical Recipes in C++, p.410)
        double xtol = endCriteria.rootEpsilon();          // end criteria on x (see GSL v. 1.9, http://www.gnu.org/software/gsl/)
        int maxStationaryStateIterations_
                = endCriteria.maxStationaryStateIterations();
        ReferencePkg<EndCriteria.Type> ecType = new ReferencePkg<>(EndCriteria.Type.None);
        P.reset();

        Array x_ = P.currentValue();
        if (!P.constraint().test(x_))
            QL_FAIL("Initial guess " + x_ + " is not in the feasible region.");

        int iterationNumber_ = 0;

        // Initialize vertices of the simplex
        int n = x_.size();
        vertices_ = CommonUtil.ArrayInit(n+1);
        for (int i = 0; i < n+1; i++) {
            vertices_.set(i, new Array(x_.toArray()));
        }
        for (int i=0; i<n; ++i) {
            Array direction = new Array(n, 0.0);
            direction.set(i, 1.0);
            P.constraint().update(vertices_.get(i+1), direction, lambda_);
        }
        // Initialize function values at the vertices of the simplex
        values_ = new Array(n+1, 0.0);
        for (int i=0; i<=n; ++i)
            values_.set(i, P.value(vertices_.get(i)));
        // Loop looking for minimum
        do {
            sum_ = new Array(n, 0.0);
            int i;
            for (i=0; i<=n; i++)
                sum_ = sum_.add(vertices_.get(i));
            // Determine the best (iLowest), worst (iHighest)
            // and 2nd worst (iNextHighest) vertices
            int iLowest = 0;
            int iHighest, iNextHighest;
            if (values_.get(0)<values_.get(1)) {
                iHighest = 1;
                iNextHighest = 0;
            } else {
                iHighest = 0;
                iNextHighest = 1;
            }
            for (i=1;i<=n; i++) {
                if (values_.get(i)>values_.get(iHighest)) {
                    iNextHighest = iHighest;
                    iHighest = i;
                } else {
                    if ((values_.get(i)>values_.get(iNextHighest)) && i!=iHighest)
                        iNextHighest = i;
                }
                if (values_.get(i)<values_.get(iLowest))
                    iLowest = i;
            }
            // Now compute accuracy, update iteration number and check end criteria
            //// Numerical Recipes exit strategy on fx (see NR in C++, p.410)
            //Real low = values_[iLowest];
            //Real high = values_[iHighest];
            //Real rtol = 2.0*std::fabs(high - low)/
            //    (std::fabs(high) + std::fabs(low) + QL_EPSILON);
            //++iterationNumber_;
            //if (rtol < ftol ||
            //    endCriteria.checkMaxIterations(iterationNumber_, ecType)) {
            // GSL exit strategy on x (see GSL v. 1.9, http://www.gnu.org/software/gsl
            double simplexSize = computeSimplexSize(vertices_);
            ++iterationNumber_;
            if (simplexSize < xtol ||
                    endCriteria.checkMaxIterations(iterationNumber_, ecType)) {
                // 装箱
                ReferencePkg<Integer> mssi = new ReferencePkg<>(maxStationaryStateIterations_);
                endCriteria.checkStationaryPoint(0.0, 0.0,
                        mssi, ecType);
                maxStationaryStateIterations_ = mssi.getT();
                endCriteria.checkMaxIterations(iterationNumber_, ecType);
                x_ = vertices_.get(iLowest);
                double low = values_.get(iLowest);
                P.setFunctionValue(low);
                P.setCurrentValue(x_);
                return ecType.getT();
            }
            // If end criteria is not met, continue
            ReferencePkg<Double> factor = new ReferencePkg<>(-1.0);
            double vTry = extrapolate(P, iHighest, factor);
            if ((vTry <= values_.get(iLowest)) && (factor.getT() == -1.0)) {
                factor.setT(2.0);
                extrapolate(P, iHighest, factor);
            } else if (Math.abs(factor.getT()) > QL_EPSILON) {
                if (vTry >= values_.get(iNextHighest)) {
                    double vSave = values_.get(iHighest);
                    factor.setT(0.5);
                    vTry = extrapolate(P, iHighest, factor);
                    if (vTry >= vSave && Math.abs(factor.getT()) > QL_EPSILON) {
                        for (i=0; i<=n; i++) {
                            if (i!=iLowest) {
                                vertices_.set(i, (vertices_.get(i).add(vertices_.get(iLowest))).mutiply(0.5));
                                values_.set(i, P.value(vertices_.get(i)));
                            }
                        }
                    }
                }
            }
            // If can't extrapolate given the constraints, exit
            if (Math.abs(factor.getT()) <= QL_EPSILON) {
                x_ = vertices_.get(iLowest);
                double low = values_.get(iLowest);
                P.setFunctionValue(low);
                P.setCurrentValue(x_);
                return EndCriteria.Type.StationaryFunctionValue;
            }
        } while (true);
    }

    private double extrapolate(Problem P,
                               int iHighest,
                               ReferencePkg<Double> factor) {
        Array pTry;
        do {
            int dimensions = values_.size() - 1;
            double factor1 = (1.0 - factor.getT())/dimensions;
            double factor2 = factor1 - factor.getT();
            pTry = sum_.mutiply(factor1).subtract(vertices_.get(iHighest).mutiply(factor2));
            factor.setT(0.5 * factor.getT());
        } while (!P.constraint().test(pTry) && Math.abs(factor.getT()) > QL_EPSILON);
        if (Math.abs(factor.getT()) <= QL_EPSILON) {
            return values_.get(iHighest);
        }
        factor.setT(2.0 * factor.getT());
        double vTry = P.value(pTry);
        if (vTry < values_.get(iHighest)) {
            values_.set(iHighest , vTry);
            sum_ =sum_.add(pTry.subtract(vertices_.get(iHighest)));
            vertices_.set(iHighest , pTry);
        }
        return vTry;
    }
}
