package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.BackwardFlatInterpolationImpl;

public class BackwardFlatInterpolation extends Interpolation {
    public BackwardFlatInterpolation(double[] x, double[] y) {
        impl_ = new BackwardFlatInterpolationImpl(x, y);
        impl_.update();
    }
}
