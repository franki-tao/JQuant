package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.ConvexMonotoneImpl;
import jquant.math.interpolations.impl.SectionHelper;

import java.util.TreeMap;

public class ConvexMonotoneInterpolation extends Interpolation {
    private ConvexMonotoneImpl derived;

    //flatFinalPeriod默认为false
    public ConvexMonotoneInterpolation(double[] x, double[] y, double quadraticity,
                                       double monotonicity, boolean forcePositive,
                                       boolean flatFinalPeriod,
                                    TreeMap<Double, SectionHelper> preExistingHelpers) {
        this(x, y, x.length, quadraticity, monotonicity, forcePositive,
                flatFinalPeriod, preExistingHelpers);
    }

    /**
     * Uses only the first {@code length} entries of {@code x}/{@code y}, whose
     * arrays stay referenced so that {@link #update()} sees node changes.
     */
    public ConvexMonotoneInterpolation(double[] x, double[] y, int length,
                                       double quadraticity,
                                       double monotonicity, boolean forcePositive,
                                       boolean flatFinalPeriod,
                                    TreeMap<Double, SectionHelper> preExistingHelpers) {
        derived = new ConvexMonotoneImpl(x,y,length,quadraticity,
                monotonicity,
                forcePositive,
                flatFinalPeriod,
                preExistingHelpers);
        impl_ = derived;
        impl_.update();
    }

    public TreeMap<Double, SectionHelper> getExistingHelpers() {
        return derived.getExistingHelpers();
    }
}
