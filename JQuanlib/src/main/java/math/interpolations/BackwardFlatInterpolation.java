package math.interpolations;

import math.Interpolation;
import math.interpolations.impl.BackwardFlatInterpolationImpl;

public class BackwardFlatInterpolation extends Interpolation {
    public BackwardFlatInterpolation(double[] x, double[] y) {
        impl_ = new BackwardFlatInterpolationImpl(x, y);
        impl_.update();
    }
}
