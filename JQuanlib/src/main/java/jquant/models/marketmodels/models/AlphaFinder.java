package jquant.models.marketmodels.models;

import jquant.math.Quadratic;
import jquant.math.ReferencePkg;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/models/alphafinder.hpp/.cpp}
 * port.
 */
public class AlphaFinder {

    private interface RealValue {
        double value(double x);
    }

    private interface BoolValue {
        boolean value(double x);
    }

    private final AlphaForm parametricform_;
    private int stepindex_;
    private List<Double> rateonevols_;
    private List<Double> ratetwohomogeneousvols_;
    private List<Double> putativevols_;
    private List<Double> correlations_;
    private double w0_;
    private double w1_;
    private double constantPart_;
    private double linearPart_;
    private double quadraticPart_;
    private double totalVar_;
    private double targetVariance_;

    public AlphaFinder(AlphaForm parametricform) {
        parametricform_ = parametricform;
    }

    private static double Bisection(double target,
                                    double low,
                                    double high,
                                    double tolerance,
                                    RealValue value) {
        double x = 0.5 * (low + high);
        double y = value.value(x);

        do {
            if (y < target) low = x;
            else if (y > target) high = x;
            else return x;

            final double previous = x;
            x = 0.5 * (low + high);
            if (x == previous)
                return x;
            y = value.value(x);
        } while (Math.abs(high - low) > tolerance);

        return x;
    }

    private static double FindHighestOK(double low,
                                        double high,
                                        double tolerance,
                                        BoolValue value) {
        double x = 0.5 * (low + high);
        boolean ok = value.value(x);

        do {
            if (ok) low = x;
            else high = x;

            x = 0.5 * (low + high);
            ok = value.value(x);
        } while (Math.abs(high - low) > tolerance);

        return x;
    }

    private static double FindLowestOK(double low,
                                       double high,
                                       double tolerance,
                                       BoolValue value) {
        double x = 0.5 * (low + high);
        boolean ok = value.value(x);

        do {
            if (ok) high = x;
            else low = x;

            x = 0.5 * (low + high);
            ok = value.value(x);
        } while (Math.abs(high - low) > tolerance);

        return x;
    }

    private static double Minimize(double low,
                                   double high,
                                   double tolerance,
                                   RealValue value,
                                   BoolValue condition,
                                   ReferencePkg<Boolean> failed) {
        double leftValue = value.value(low);
        double rightValue = value.value(high);
        double W = 0.5 * (3.0 - Math.sqrt(5.0));
        double x = W * low + (1 - W) * high;
        double midValue = value.value(x);

        failed.setT(true);

        while (high - low > tolerance) {

            if (x - low > high - x) { // left interval is bigger
                double tentativeNewMid = W * low + (1 - W) * x;
                boolean conditioner = condition.value(tentativeNewMid);
                if (!conditioner) {
                    if (condition.value(x))
                        return x;
                    else if (leftValue < rightValue)
                        return low;
                    else
                        return high;
                }

                double tentativeNewMidValue = value.value(tentativeNewMid);
                if (tentativeNewMidValue < midValue) { // go left
                    high = x;
                    rightValue = midValue;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else { // go right
                    low = tentativeNewMid;
                    leftValue = tentativeNewMidValue;
                }
            } else {
                double tentativeNewMid = W * x + (1 - W) * high;
                boolean conditioner = condition.value(tentativeNewMid);
                if (!conditioner) {
                    if (condition.value(x))
                        return x;
                    else if (leftValue < rightValue)
                        return low;
                    else
                        return high;
                }

                double tentativeNewMidValue = value.value(tentativeNewMid);
                if (tentativeNewMidValue < midValue) { // go right
                    low = x;
                    leftValue = midValue;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else { // go left
                    high = tentativeNewMid;
                    rightValue = tentativeNewMidValue;
                }
            }
        }
        failed.setT(false);
        return x;
    }

    private double computeLinearPart(double alpha) {
        double cov = 0.0;
        parametricform_.setAlpha(alpha);

        for (int i = 0; i < stepindex_ + 1; ++i) {
            double vol1 = ratetwohomogeneousvols_.get(i) * parametricform_.value(i);
            cov += vol1 * rateonevols_.get(i) * correlations_.get(i);
        }

        cov *= 2 * w0_ * w1_;
        return cov;
    }

    private double computeQuadraticPart(double alpha) {
        double var = 0.0;
        parametricform_.setAlpha(alpha);

        for (int i = 0; i < stepindex_ + 1; ++i) {
            double vol = ratetwohomogeneousvols_.get(i) * parametricform_.value(i);
            var += vol * vol;
        }

        var *= w1_ * w1_;
        return var;
    }

    private double homogeneityfailure(double alpha) {
        ReferencePkg<Double> dum1 = new ReferencePkg<>(0.0);
        ReferencePkg<Double> dum2 = new ReferencePkg<>(0.0);
        ReferencePkg<Double> dum3 = new ReferencePkg<>(0.0);
        finalPart(alpha,
                stepindex_,
                ratetwohomogeneousvols_,
                computeQuadraticPart(alpha),
                computeLinearPart(alpha),
                constantPart_,
                dum1,
                dum2,
                dum3,
                putativevols_);

        double result = 0.0;
        for (int i = 0; i <= stepindex_ + 1; ++i) {
            double val = putativevols_.get(i) - ratetwohomogeneousvols_.get(i);
            result += val * val;
        }

        return result;
    }

    private boolean finalPart(double alphaFound,
                              int stepindex,
                              final List<Double> ratetwohomogeneousvols,
                              double quadraticPart,
                              double linearPart,
                              double constantPart,
                              ReferencePkg<Double> alpha,
                              ReferencePkg<Double> a,
                              ReferencePkg<Double> b,
                              List<Double> ratetwovols) {
        alpha.setT(alphaFound);
        Quadratic q2 = new Quadratic(quadraticPart, linearPart,
                constantPart - targetVariance_);
        parametricform_.setAlpha(alpha.getT());
        ReferencePkg<Double> y = new ReferencePkg<>(0.0); // dummy
        q2.roots(a, y);

        double varSoFar = 0.0;
        for (int i = 0; i < stepindex + 1; ++i) {
            ratetwovols.set(i, ratetwohomogeneousvols.get(i)
                    * parametricform_.value(i) * a.getT());
            varSoFar += ratetwovols.get(i) * ratetwovols.get(i);
        }

        double VarToFind = totalVar_ - varSoFar;
        if (VarToFind < 0)
            return false;
        double requiredSd = Math.sqrt(VarToFind);
        b.setT(requiredSd / (ratetwohomogeneousvols.get(stepindex + 1)
                * parametricform_.value(stepindex)));
        ratetwovols.set(stepindex + 1, requiredSd);
        return true;
    }

    private double valueAtTurningPoint(double alpha) {
        linearPart_ = computeLinearPart(alpha);
        quadraticPart_ = computeQuadraticPart(alpha);

        Quadratic q = new Quadratic(quadraticPart_, linearPart_, constantPart_);
        return q.valueAtTurningPoint();
    }

    private double minusValueAtTurningPoint(double alpha) {
        return -valueAtTurningPoint(alpha);
    }

    private boolean testIfSolutionExists(double alpha) {
        boolean aExists = valueAtTurningPoint(alpha) < targetVariance_;
        if (!aExists)
            return false;

        ReferencePkg<Double> dum1 = new ReferencePkg<>(0.0);
        ReferencePkg<Double> dum2 = new ReferencePkg<>(0.0);
        ReferencePkg<Double> dum3 = new ReferencePkg<>(0.0);
        return finalPart(alpha,
                stepindex_,
                ratetwohomogeneousvols_,
                computeQuadraticPart(alpha),
                computeLinearPart(alpha),
                constantPart_,
                dum1,
                dum2,
                dum3,
                putativevols_);
    }

    public boolean solve(double alpha0,
                         int stepindex, // caplet index
                         final List<Double> rateonevols,
                         final List<Double> ratetwohomogeneousvols,
                         final List<Double> correlations,
                         double w0,
                         double w1,
                         double targetVariance,
                         double tolerance,
                         double alphaMax,
                         double alphaMin,
                         int steps,
                         ReferencePkg<Double> alpha,
                         ReferencePkg<Double> a,
                         ReferencePkg<Double> b,
                         List<Double> ratetwovols) {
        stepindex_ = stepindex;
        rateonevols_ = new ArrayList<>(rateonevols);
        ratetwohomogeneousvols_ = new ArrayList<>(ratetwohomogeneousvols);
        correlations_ = new ArrayList<>(correlations);
        w0_ = w0;
        w1_ = w1;
        totalVar_ = 0;
        for (int i = 0; i <= stepindex + 1; ++i)
            totalVar_ += ratetwohomogeneousvols.get(i) * ratetwohomogeneousvols.get(i);
        targetVariance_ = targetVariance;

        // constant part will not depend on alpha
        constantPart_ = 0.0;
        for (int i = 0; i < stepindex + 1; ++i)
            constantPart_ += rateonevols.get(i) * rateonevols.get(i);

        constantPart_ *= w0 * w0;

        // compute linear part with initial alpha
        double valueAtTP = valueAtTurningPoint(alpha0);

        if (valueAtTP <= targetVariance) {
            return finalPart(alpha0,
                    stepindex,
                    ratetwohomogeneousvols,
                    quadraticPart_,
                    linearPart_,
                    constantPart_,
                    alpha,
                    a,
                    b,
                    ratetwovols);
        }

        // we now have to solve
        double bottomValue = valueAtTurningPoint(alphaMin);
        double bottomAlpha = alphaMin;
        double topValue = valueAtTurningPoint(alphaMax);
        double topAlpha = alphaMax;
        double bilimit = alpha0;

        if (bottomValue > targetVariance && topValue > targetVariance) {
            // see if ok at some intermediate point by stepping through
            int i = 1;
            while (i < steps && topValue > targetVariance) {
                topAlpha = alpha0 + (alphaMax - alpha0) * (i + 0.0) / (steps + 0.0);
                topValue = valueAtTurningPoint(topAlpha);
                ++i;
            }

            if (topValue <= targetVariance)
                bilimit = alpha0 + (topAlpha - alpha0) * (i - 2.0) / (steps + 0.0);
        }

        if (bottomValue > targetVariance && topValue > targetVariance) {
            // see if ok at some intermediate point by stepping through
            int i = 1;
            while (i < steps && bottomValue > targetVariance) {
                bottomAlpha = alpha0 + (alphaMin - alpha0) * (i + 0.0) / (steps + 0.0);
                bottomValue = valueAtTurningPoint(bottomAlpha);
                ++i;
            }

            if (bottomValue <= targetVariance)
                bilimit = alpha0 + (bottomAlpha - alpha0) * (i - 2.0) / (steps + 0.0);
        }

        if (bottomValue > targetVariance && topValue > targetVariance)
            return false;

        if (bottomValue <= targetVariance) {
            // then find root of increasing function
            // (or as if increasing function)
            alpha.setT(Bisection(targetVariance,
                    bottomAlpha,
                    bilimit,
                    tolerance,
                    this::valueAtTurningPoint));
        } else {
            // find root of decreasing function (or as if decreasing function)
            alpha.setT(Bisection(-targetVariance,
                    bilimit,
                    topAlpha,
                    tolerance,
                    this::minusValueAtTurningPoint));
        }
        return finalPart(alpha.getT(),
                stepindex,
                ratetwohomogeneousvols,
                quadraticPart_,
                linearPart_,
                constantPart_,
                alpha,
                a,
                b,
                ratetwovols);
    }

    public boolean solveWithMaxHomogeneity(
            double alpha0,
            int stepindex, // caplet index
            final List<Double> rateonevols,
            final List<Double> ratetwohomogeneousvols,
            final List<Double> correlations,
            double w0,
            double w1,
            double targetVariance,
            double tolerance,
            double alphaMax,
            double alphaMin,
            int steps,
            ReferencePkg<Double> alpha,
            ReferencePkg<Double> a,
            ReferencePkg<Double> b,
            List<Double> ratetwovols) {

        stepindex_ = stepindex;
        rateonevols_ = new ArrayList<>(rateonevols);
        ratetwohomogeneousvols_ = new ArrayList<>(ratetwohomogeneousvols);
        putativevols_ = filledList(ratetwohomogeneousvols_.size(), 0.0);
        correlations_ = new ArrayList<>(correlations);
        w0_ = w0;
        w1_ = w1;
        totalVar_ = 0;
        for (int i = 0; i <= stepindex + 1; ++i)
            totalVar_ += ratetwohomogeneousvols.get(i) * ratetwohomogeneousvols.get(i);
        targetVariance_ = targetVariance;

        // constant part will not depend on alpha
        constantPart_ = 0.0;
        for (int i = 0; i < stepindex + 1; ++i)
            constantPart_ += rateonevols.get(i) * rateonevols.get(i);

        constantPart_ *= w0 * w0;

        double alpha1 = alphaMin;
        double alpha2 = alphaMax;

        // compute linear part with initial alpha
        boolean alpha0OK = testIfSolutionExists(alpha0);
        boolean alphaMaxOK = testIfSolutionExists(alphaMax);
        boolean alphaMinOK = testIfSolutionExists(alphaMin);

        boolean foundOKPoint = alpha0OK || alphaMaxOK || alphaMinOK;

        if (foundOKPoint) {
            if (!alphaMinOK) {
                // lower alpha is bad
                if (alpha0OK) {
                    // must die somewhere in between
                    alpha1 = FindLowestOK(alphaMin,
                            alpha0,
                            tolerance,
                            this::testIfSolutionExists);
                } else {
                    // alphaMaxOK must be true to get here
                    alpha1 = FindLowestOK(alpha0,
                            alphaMax,
                            tolerance,
                            this::testIfSolutionExists);
                }
            }

            if (!alphaMaxOK) {
                // higher alpha is bad
                alpha2 = FindHighestOK(alpha1,
                        alphaMax,
                        tolerance,
                        this::testIfSolutionExists);
            } else
                alpha2 = alphaMax;
        } else {
            // ok let's see if we can find a value of alpha that works
            boolean foundUpOK = false;
            boolean foundDownOK = false;
            double alphaUp = alpha0, alphaDown = alpha0;
            double stepSize = (alphaMax - alpha0) / steps;

            for (int j = 0; j < steps && !foundUpOK && !foundDownOK; ++j) {
                alphaUp = alpha0 + j * stepSize;
                foundUpOK = testIfSolutionExists(alphaUp);
                alphaDown = alpha0 - j * stepSize;
                foundDownOK = testIfSolutionExists(alphaDown);
            }
            foundOKPoint = foundUpOK || foundDownOK;
            if (!foundOKPoint)
                return false;

            if (foundUpOK) {
                alpha1 = alphaUp;
                alpha2 = FindHighestOK(alpha1,
                        alphaMax,
                        tolerance,
                        this::testIfSolutionExists);
            } else {
                alpha2 = alphaDown;
                alpha1 = FindLowestOK(alphaMin,
                        alpha2,
                        tolerance,
                        this::testIfSolutionExists);
            }
        }

        // we have now found alpha1, alpha2 such that solution exists
        // at endpoints. we now want to minimize within that interval
        ReferencePkg<Boolean> failed = new ReferencePkg<>(false);
        alpha.setT(Minimize(alpha1,
                alpha2,
                tolerance,
                this::homogeneityfailure,
                this::testIfSolutionExists,
                failed));

        return finalPart(alpha.getT(),
                stepindex,
                ratetwohomogeneousvols,
                computeQuadraticPart(alpha.getT()),
                computeLinearPart(alpha.getT()),
                constantPart_,
                alpha,
                a,
                b,
                ratetwovols);
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }
}
