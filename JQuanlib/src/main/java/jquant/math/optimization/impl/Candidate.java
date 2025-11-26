package jquant.math.optimization.impl;

import jquant.math.Array;

public class Candidate {
    public Array values;
    public double cost = 0;
    public Candidate(int size) {
        values = new Array(size, 0.0);
    }
}
