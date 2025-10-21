package jquant.math.matrixutilities.impl;

import jquant.math.Array;

public class BiCGStabResult {
    public int iterations;
    public double error;
    public Array x;

    public BiCGStabResult() {}

    public BiCGStabResult(int iterations, double error, Array x) {
        this.iterations = iterations;
        this.error = error;
        this.x = x;
    }
}
