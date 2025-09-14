package jquant.math.interpolations.impl;

import jquant.math.Array;

public interface UpdatedYInterpolation {
    double value(Array yValues, double x);
}
