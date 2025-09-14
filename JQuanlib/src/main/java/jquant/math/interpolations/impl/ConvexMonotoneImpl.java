package jquant.math.interpolations.impl;

import jquant.math.CommonUtil;
import jquant.math.templateImpl;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.lang.Math.abs;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

public class ConvexMonotoneImpl extends templateImpl {

    public enum SectionType {
        EverywhereConstant,
        ConstantGradient,
        QuadraticMinimum,
        QuadraticMaximum
    }

    ;

    private TreeMap<Double, SectionHelper> sectionHelpers_;
    private TreeMap<Double, SectionHelper> preSectionHelpers_;
    private SectionHelper extrapolationHelper_;
    private boolean forcePositive_, constantLastPeriod_;
    private double quadraticity_;
    private double monotonicity_;
    private int length_;

    public ConvexMonotoneImpl(double[] x, double[] y,
                              double quadraticity,
                              double monotonicity,
                              boolean forcePositive,
                              boolean constantLastPeriod,
                              TreeMap<Double, SectionHelper> preExistingHelpers) {
        super(x, y, ConvexMonotone.requiredPoints);
        preSectionHelpers_ = preExistingHelpers;
        forcePositive_ = forcePositive;
        constantLastPeriod_ = constantLastPeriod;
        quadraticity_ = quadraticity;
        monotonicity_ = monotonicity;
        length_ = x.length;
        QL_REQUIRE(monotonicity_ >= 0 && monotonicity_ <= 1,
                "Monotonicity must lie between 0 and 1");
        QL_REQUIRE(quadraticity_ >= 0 && quadraticity_ <= 1,
                "Quadraticity must lie between 0 and 1");
        QL_REQUIRE(length_ >= 2,
                "Single point provided, not supported by convex " +
                        "monotone method as first point is ignored");
        QL_REQUIRE((length_ - preExistingHelpers.size()) > 1,
                "Too many existing helpers have been supplied");
    }


    @Override
    public void update() {
        sectionHelpers_.clear();
        if (length_ == 2) { //single period
            SectionHelper singleHelper = new EverywhereConstantHelper(yValue[1], 0.0, xValue[0]);
            sectionHelpers_.put(xValue[1], singleHelper);
            extrapolationHelper_ = singleHelper;
            return;
        }

        List<Double> f = CommonUtil.ArrayInit(length_);
        sectionHelpers_ = preSectionHelpers_;
        int startPoint = sectionHelpers_.size() + 1;

        //first derive the boundary forwards.
        for (int i = startPoint; i < length_ - 1; ++i) {
            double dxPrev = xValue[i] - xValue[i - 1];
            double dx = xValue[i + 1] - xValue[i];
            f.set(i, dx / (dx + dxPrev) * yValue[i] + dxPrev / (dx + dxPrev) * yValue[i + 1]);
        }

        if (startPoint > 1) {
            f.set(startPoint - 1, preSectionHelpers_.lastEntry().getValue().fNext());
        }
        if (startPoint == 1) {
            f.set(0, 1.5 * yValue[1] - 0.5 * f.get(1));
        }
        f.set(length_ - 1, 1.5 * yValue[length_ - 1] - 0.5 * f.get(length_ - 2));
        //f[length_-1] = 1.5 * this->yBegin_[length_-1] - 0.5 * f[length_-2];

        if (forcePositive_) {
            if (f.get(0) < 0)
                f.set(0, 0.0);
            if (f.get(length_ - 1) < 0.0)
                f.set(length_ - 1, 0.0);
        }

        double primitive = 0.0;
        for (int i = 0; i < startPoint - 1; ++i)
            primitive += yValue[i + 1] * (xValue[i + 1] - xValue[i]);

        int endPoint = length_;
        //constantLastPeriod_ = false;
        if (constantLastPeriod_)
            endPoint = endPoint - 1;

        for (int i = startPoint; i < endPoint; ++i) {
            double gPrev = f.get(i - 1) - yValue[i];
            double gNext = f.get(i) - yValue[i];
            //first deal with the zero gradient case
            if (abs(gPrev) < 1.0E-14 && abs(gNext) < 1.0E-14) {
                SectionHelper singleHelper = new ConstantGradHelper(f.get(i - 1), primitive, xValue[i - 1], xValue[i], f.get(i));
                sectionHelpers_.put(xValue[i], singleHelper);
            } else {
                double quadraticity = quadraticity_;
                SectionHelper quadraticHelper = null;
                SectionHelper convMonotoneHelper = null;
                if (quadraticity_ > 0.0) {
                    if (gPrev >= -2.0 * gNext && gPrev > -0.5 * gNext && forcePositive_) {
                        quadraticHelper = new QuadraticMinHelper(xValue[i - 1],
                                xValue[i],
                                f.get(i - 1), f.get(i),
                                yValue[i],
                                primitive);
                    } else {
                        quadraticHelper = new QuadraticHelper(xValue[i - 1],
                                xValue[i],
                                f.get(i - 1), f.get(i),
                                yValue[i],
                                primitive);
                    }
                }
                if (quadraticity_ < 1.0) {

                    if ((gPrev > 0.0 && -0.5 * gPrev >= gNext && gNext >= -2.0 * gPrev) ||
                            (gPrev < 0.0 && -0.5 * gPrev <= gNext && gNext <= -2.0 * gPrev)) {
                        quadraticity = 1.0;
                        if (quadraticity_ == 0) {
                            if (forcePositive_) {
                                quadraticHelper = new QuadraticMinHelper(
                                        xValue[i - 1],
                                        xValue[i],
                                        f.get(i - 1), f.get(i),
                                        yValue[i],
                                        primitive);
                            } else {
                                quadraticHelper = new QuadraticHelper(
                                        xValue[i - 1],
                                        xValue[i],
                                        f.get(i - 1), f.get(i),
                                        yValue[i],
                                        primitive);
                            }
                        }
                    } else if ((gPrev < 0.0 && gNext > -2.0 * gPrev) ||
                            (gPrev > 0.0 && gNext < -2.0 * gPrev)) {

                        double eta = (gNext + 2.0 * gPrev) / (gNext - gPrev);
                        double b2 = (1.0 + monotonicity_) / 2.0;
                        if (eta < b2) {
                            convMonotoneHelper = new ConvexMonotone2Helper(
                                    xValue[i - 1],
                                    xValue[i],
                                    gPrev, gNext,
                                    yValue[i],
                                    eta, primitive);
                        } else {
                            if (forcePositive_) {
                                convMonotoneHelper = new ConvexMonotone4MinHelper(
                                        xValue[i - 1],
                                        xValue[i],
                                        gPrev, gNext,
                                        yValue[i],
                                        b2, primitive);
                            } else {
                                convMonotoneHelper = new ConvexMonotone4Helper(
                                        xValue[i - 1],
                                        xValue[i],
                                        gPrev, gNext,
                                        yValue[i],
                                        b2, primitive);
                            }
                        }
                    } else if ((gPrev > 0.0 && gNext < 0.0 && gNext > -0.5 * gPrev) ||
                            (gPrev < 0.0 && gNext > 0.0 && gNext < -0.5 * gPrev)) {
                        double eta = gNext / (gNext - gPrev) * 3.0;
                        double b3 = (1.0 - monotonicity_) / 2.0;
                        if (eta > b3) {
                            convMonotoneHelper = new ConvexMonotone3Helper(
                                    xValue[i - 1],
                                    xValue[i],
                                    gPrev, gNext,
                                    yValue[i],
                                    eta, primitive);
                        } else {
                            if (forcePositive_) {
                                convMonotoneHelper = new ConvexMonotone4MinHelper(
                                        xValue[i - 1],
                                        xValue[i],
                                        gPrev, gNext,
                                        yValue[i],
                                        b3, primitive);
                            } else {
                                convMonotoneHelper = new ConvexMonotone4Helper(
                                        xValue[i - 1],
                                        xValue[i],
                                        gPrev, gNext,
                                        yValue[i],
                                        b3, primitive);
                            }
                        }
                    } else {
                        double eta = gNext / (gPrev + gNext);
                        double b2 = (1.0 + monotonicity_) / 2.0;
                        double b3 = (1.0 - monotonicity_) / 2.0;
                        if (eta > b2)
                            eta = b2;
                        if (eta < b3)
                            eta = b3;
                        if (forcePositive_) {
                            convMonotoneHelper = new ConvexMonotone4MinHelper(
                                    xValue[i - 1],
                                    xValue[i],
                                    gPrev, gNext,
                                    yValue[i],
                                    eta, primitive);
                        } else {
                            convMonotoneHelper = new ConvexMonotone4Helper(
                                    xValue[i - 1],
                                    xValue[i],
                                    gPrev, gNext,
                                    yValue[i],
                                    eta, primitive);
                        }
                    }
                }

                if (quadraticity == 1.0) {
                    sectionHelpers_.put(xValue[i], quadraticHelper);
                    //sectionHelpers_[this->xBegin_[i]] =quadraticHelper;
                } else if (quadraticity == 0.0) {
                    sectionHelpers_.put(xValue[i], convMonotoneHelper);
                    //sectionHelpers_[this->xBegin_[i]] =convMonotoneHelper;
                } else {
                    sectionHelpers_.put(xValue[i], new ComboHelper(quadraticHelper,
                            convMonotoneHelper,
                            quadraticity));
                }

            }
            primitive += yValue[i] * (xValue[i] - xValue[i - 1]);
        }

        if (constantLastPeriod_) {
            sectionHelpers_.put(xValue[length_-1], new EverywhereConstantHelper(yValue[length_ - 1],
                    primitive,
                    xValue[length_ - 2]));
            extrapolationHelper_ = sectionHelpers_.get(xValue[length_ - 1]);
        } else {
            extrapolationHelper_ = new EverywhereConstantHelper(
                    sectionHelpers_.lastEntry().getValue().value(xValue[xValue.length-1]),
                    primitive,
                    xValue[xValue.length-1]
            );

        }
    }

    @Override
    public double value(double x) {
        if (x >= xValue[xValue.length-1]) {
            return extrapolationHelper_.value(x);
        }
        return upper_bound(sectionHelpers_, x).value(x);
    }

    @Override
    public double primitive(double x) {
        if (x >= xValue[xValue.length-1]) {
            return extrapolationHelper_.primitive(x);
        }

        return upper_bound(sectionHelpers_,x).primitive(x);
    }

    @Override
    public double derivative(double v) {
        QL_FAIL("Convex-monotone spline derivative not implemented");
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        QL_FAIL("Convex-monotone spline second derivative " +
                "not implemented");
        return 0;
    }

    public TreeMap<Double, SectionHelper> getExistingHelpers() {
        TreeMap<Double, SectionHelper> retArray = (sectionHelpers_);
        if (constantLastPeriod_)
            retArray.remove(xValue[xValue.length-1]);
        return retArray;
    }

    private SectionHelper upper_bound(Map<Double, SectionHelper> m, double x) {
        double k = Double.MAX_VALUE;
        for(double d : m.keySet()) {
            if (d >= x && d < k) {
                k = d;
            }
        }
        return m.get(k);
    }
}
