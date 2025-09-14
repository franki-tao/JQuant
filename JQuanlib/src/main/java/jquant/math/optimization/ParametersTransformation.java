package jquant.math.optimization;


import jquant.math.Array;

public interface ParametersTransformation {
    Array direct(final Array x);

    Array inverse(final Array x);
}
