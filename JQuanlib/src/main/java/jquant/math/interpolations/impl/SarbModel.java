package jquant.math.interpolations.impl;

import jquant.math.Array;

import java.util.List;

public interface SarbModel {
    int dimension();

    void defaultValues(List<Double> params, List<Boolean> temp,
                       final double forward, final double expiryTime,
                       final List<Double> addParams);

    void guess(Array values, final List<Boolean> paramIsFixed,
               final double forward, final double expiryTime,
               final List<Double> r, final List<Double> addParams);

    double eps1();

    double eps2();

    double dilationFactor();

    Array inverse(final Array y, final List<Boolean> temp,
                  final List<Double> tmp, final double tp);

    Array direct(final Array x, final List<Boolean> temp,
                 final List<Double> tmp, final double tp);

    double weight(final double strike, final double forward, final double stdDev,
                  final List<Double> addParams);

    SABRWrapper instance(final double t, final double forward,
                         final List<Double> params,
                         final List<Double> addParams);
}
