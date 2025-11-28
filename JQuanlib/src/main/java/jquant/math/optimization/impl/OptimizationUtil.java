package jquant.math.optimization.impl;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.ReferencePkg;
import jquant.math.optimization.SphereCylinderOptimizer;

import java.util.List;

import static jquant.math.CommonUtil.Norm2;
import static jquant.math.CommonUtil.QL_REQUIRE;

public class OptimizationUtil {
    // Computes the size of the simplex
    public static double computeSimplexSize (final List<Array> vertices) {
        Array center = new Array(vertices.get(0).size(),0);
        for (Array vertice : vertices)
            center = center.add(vertice);
        center = center.mutiply(1.0/(vertices.size()));
        double result = 0;
        for (final Array vertice : vertices) {
            Array temp = vertice.subtract(center);
            result += Norm2(temp);
        }
        return result/(vertices.size());
    }

    public static double BrentMinimize(double low,
                                       double mid,
                                       double high,
                                       double tolerance,
                                       int maxIt,
                           Function objectiveFunction) {
        double W = 0.5*(3.0-Math.sqrt(5.0));
        double x = W*low+(1-W)*high;
        if (mid > low && mid < high)
            x = mid;

        double midValue = objectiveFunction.value(x);

        int iterations = 0;
        while (high-low > tolerance && iterations < maxIt) {
            if (x - low > high -x) { // left interval is bigger
                double tentativeNewMid = W*low+(1-W)*x;
                double tentativeNewMidValue =
                        objectiveFunction.value(tentativeNewMid);

                if (tentativeNewMidValue < midValue) { // go left
                    high =x;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else { // go right
                    low = tentativeNewMid;
                }
            } else {
                double tentativeNewMid = W*x+(1-W)*high;
                double tentativeNewMidValue =
                        objectiveFunction.value(tentativeNewMid);

                if (tentativeNewMidValue < midValue) { // go right
                    low =x;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else { // go left
                    high = tentativeNewMid;
                }
            }
            ++iterations;
        }
        return x;
    }

    public static List<Double> sphereCylinderOptimizerClosest(double r,
                                                              double s,
                                                              double alpha,
                                                              double z1,
                                                              double z2,
                                                              double z3,
                                                              int maxIterations,
                                                              double tolerance,
                                                              double zweight)
    {

        SphereCylinderOptimizer optimizer = new SphereCylinderOptimizer(r, s, alpha, z1, z2, z3, zweight);
        List<Double> y = CommonUtil.ArrayInit(3,Double.NaN);
        ReferencePkg<Double> y1 = new ReferencePkg<>(0d);
        ReferencePkg<Double> y2 = new ReferencePkg<>(0d);
        ReferencePkg<Double> y3 = new ReferencePkg<>(0d);

        QL_REQUIRE(optimizer.isIntersectionNonEmpty(),
                "intersection empty so no solution");

        if (maxIterations ==0)
            optimizer.findByProjection(y1, y2, y3);
        else
            optimizer.findClosest(maxIterations, tolerance, y1, y2, y3);
        y.set(0, y1.getT());
        y.set(1, y2.getT());
        y.set(2, y3.getT());
        return y;
    }
}
