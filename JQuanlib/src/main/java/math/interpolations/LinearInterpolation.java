package math.interpolations;

import math.Interpolation;
import math.interpolations.impl.LinearInterpolationImpl;

public class LinearInterpolation extends Interpolation {
    public LinearInterpolation(double[] x, double[] y) {
        super.impl_ = new LinearInterpolationImpl(x, y);
        super.impl_.update();
    }
}
