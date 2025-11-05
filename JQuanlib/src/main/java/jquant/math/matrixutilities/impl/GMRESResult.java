package jquant.math.matrixutilities.impl;

import jquant.math.Array;

import java.util.List;

public class GMRESResult {
    public List<Double> errors;
    public Array x;

    public GMRESResult(List<Double> errors, Array x) {
        this.errors = errors;
        this.x = x;
    }

    public double back() {
        return errors.get(errors.size() - 1);
    }
}
