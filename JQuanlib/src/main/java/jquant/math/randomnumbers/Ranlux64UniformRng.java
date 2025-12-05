package jquant.math.randomnumbers;

import jquant.math.randomnumbers.impl.DiscardBlockEngine;
import jquant.math.randomnumbers.impl.Ranlux64Base01;
import jquant.methods.montecarlo.SampleReal;

//! Uniform random number generator
/*! M. Luescher's "luxury" random number generator

    Implementation is a proxy for the corresponding boost random
    number generator. For more detail see the boost documentation and:
      M.Luescher, A portable high-quality random number generator for
      lattice field theory simulations, Comp. Phys. Comm. 79 (1994) 100

    Available luxury levels:
    Ranlux3: Any theoretically possible correlations have very small change
             of being observed.
    Ranlux4: highest possible luxury.
*/
public class Ranlux64UniformRng {
    private static final double NX = 1.0 / (1L << 48);
    private final DiscardBlockEngine engine;

    public Ranlux64UniformRng(long seed, int P, int R) {
        engine = new DiscardBlockEngine(new Ranlux64Base01(seed), P, R);
    }

    public SampleReal next() {
        return new SampleReal(engine.next() * NX, 1.0);
    }

    public static void main(String[] args) {
        Ranlux64UniformRng rng = new Ranlux64UniformRng(12345, 3, 10);
        for (int i = 0; i < 10; i++) {
            System.out.println(rng.next().value);
        }
    }
}
