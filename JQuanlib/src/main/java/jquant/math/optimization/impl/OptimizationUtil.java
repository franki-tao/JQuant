package jquant.math.optimization.impl;

import jquant.math.Array;

import java.util.List;

import static jquant.math.CommonUtil.Norm2;

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
}
