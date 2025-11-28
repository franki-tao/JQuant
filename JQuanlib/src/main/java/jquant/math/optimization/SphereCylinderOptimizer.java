package jquant.math.optimization;

import jquant.math.Function;
import jquant.math.ReferencePkg;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.optimization.impl.OptimizationUtil.BrentMinimize;

/**
 * ! - we are in r^3 sphere centred at O radius r
 *   - vertical cylinder centred at (alpha,0) radius s
 *   - Z some point in R3
 *   - find point on intersection that is closest to Z
 *
 *   the intersection may be empty!
 */
public class SphereCylinderOptimizer {
    private double r_, s_, alpha_, z1_, z2_, z3_;
    private double topValue_;
    private double bottomValue_;
    private boolean nonEmpty_;
    private double zweight_;

    //zweight = 1.0
    public SphereCylinderOptimizer(double r,
                                   double s,
                                   double alpha,
                                   double z1,
                                   double z2,
                                   double z3,
                                   double zweight) {
        r_ = r;
        s_ = s;
        alpha_ = alpha;
        z1_ = z1;
        z2_ = z2;
        z3_ = z3;
        zweight_ = zweight;
        QL_REQUIRE(r > 0, "sphere must have positive radius");

        s = Math.max(s, 0.0);
        QL_REQUIRE(alpha > 0, "cylinder centre must have positive coordinate");

        nonEmpty_ = Math.abs(alpha - s) <= r;

        double cylinderInside = r * r - (s + alpha) * (s + alpha);

        if (cylinderInside > 0.0) {
            topValue_ = alpha + s;
            bottomValue_ = alpha - s;
        } else {
            bottomValue_ = alpha - s;
            double tmp = r * r - (s * s + alpha * alpha);

            if (tmp <= 0) { // max to left of maximum
                double topValue2 = Math.sqrt(s * s - tmp * tmp / (4 * alpha * alpha));
                topValue_ = alpha - Math.sqrt(s * s - topValue2 * topValue2);
            } else {
                topValue_ = alpha + tmp / (2.0 * alpha);
            }
        }
    }

    public boolean isIntersectionNonEmpty() {
        return nonEmpty_;
    }

    public void findClosest(int maxIterations,
                            double tolerance,
                            ReferencePkg<Double> y1,
                            ReferencePkg<Double> y2,
                            ReferencePkg<Double> y3) {
        ReferencePkg<Double> x1 = new ReferencePkg<>(0d);
        ReferencePkg<Double> x2 = new ReferencePkg<>(0d);
        ReferencePkg<Double> x3 = new ReferencePkg<>(0d);
        findByProjection(x1,x2,x3);

        y1.setT(BrentMinimize(bottomValue_, x1.getT(), topValue_,tolerance, maxIterations,new Function() {
            @Override
            public double value(double x) {
                return objectiveFunction(x);
            }
        }));
        y2.setT(Math.sqrt(s_*s_ - (y1.getT()-alpha_)*(y1.getT()-alpha_)));
        y3.setT(Math.sqrt(r_*r_ - y1.getT()*y1.getT()-y2.getT()*y2.getT()));
    }

    public boolean findByProjection(
            ReferencePkg<Double> y1,
            ReferencePkg<Double> y2,
            ReferencePkg<Double> y3) {
        double z1moved = z1_-alpha_;
        double distance = Math.sqrt( z1moved*z1moved + z2_*z2_);
        double scale = s_/distance;
        double y1moved = z1moved*scale;
        y1.setT(alpha_+ y1moved);
        y2.setT(scale*z2_);
        double residual = r_*r_ - y1.getT()*y1.getT() -y2.getT()*y2.getT();
        if (residual >=0.0) {
            y3.setT(Math.sqrt(residual));
            return true;
        }
        // we are outside the sphere
        if (!isIntersectionNonEmpty()) {
            y3.setT(0.0);
            return false;
        }

        // intersection is non-empty but projection point is outside sphere
        // so take rightmost point
        y3.setT(0.0);
        y1.setT(topValue_);
        y2.setT(Math.sqrt(r_*r_ -y1.getT()*y1.getT()));

        return true;
    }

    private double objectiveFunction(double x1)
    {
        //     Real x1 = alpha_ - std::sqrt(s_*s_-x2*x2);

        double x2sq = s_*s_ - (x1-alpha_)*(x1-alpha_);
        // a negative number will be minuscule and a result of rounding error
        double x2 = x2sq >= 0.0 ? (Math.sqrt(x2sq)) : 0.0;
        double x3= Math.sqrt(r_*r_ - x1*x1-x2*x2);

        double err=0.0;
        err+= (x1-z1_)*(x1-z1_);
        err+= (x2-z2_)*(x2-z2_);
        err+= (x3-z3_)*(x3-z3_)*zweight_;

        return err;
    }
}
