package math.optimization;


import math.Array;

public interface ParametersTransformation {
    Array direct(final Array x);

    Array inverse(final Array x);
}
