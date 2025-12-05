package jquant.math.randomnumbers;

import jquant.math.randomnumbers.impl.RandomUtil;
import jquant.math.randomnumbers.impl.SplitMix64;
import jquant.methods.montecarlo.SampleReal;

/**
 * ! Uniform random number generator
 * ! xoshiro256** random number generator of period 2**256-1
 * <p>
 * For more details see
 * https://prng.di.unimi.it/
 * and its reference implementation
 * https://prng.di.unimi.it/xoshiro256starstar.c
 * <p>
 * \test the correctness of the returned values is tested by checking them
 * against the reference implementation in c.
 */
public class Xoshiro256StarStarUniformRng {
    private long s0_, s1_, s2_, s3_;

    /**
     * ! If the given seed is 0, a random seed will be chosen based on clock().
     *
     * @param seed default 0
     */
    public Xoshiro256StarStarUniformRng(long seed) {
        SplitMix64 splitMix64 = new SplitMix64(seed != 0 ? seed : SeedGenerator.INSTANCE.get());
        s0_ = splitMix64.next();
        s1_ = splitMix64.next();
        s2_ = splitMix64.next();
        s3_ = splitMix64.next();
    }

    /**
     * ! Make sure that s0, s1, s2 and s3 are chosen randomly.
     * Otherwise, the results of the first random numbers might not be well distributed.
     * Especially s0 = s1 = s2 = s3 = 0 does not work and will always return 0.
     */
    public Xoshiro256StarStarUniformRng(long s0, long s1, long s2, long s3) {
        s0_ = s0;
        s1_ = s1;
        s2_ = s2;
        s3_ = s3;
    }

    /**
     * !returns a sample with weight 1.0 containing a random number
     * in the (0.0, 1.0) interval
     */
    public SampleReal next() {
        return new SampleReal(nextReal(), 1.0);
    }

    //! return a random number in the (0.0, 1.0)-interval
    public double nextReal() {
        return ((double) (nextInt64() >> 11L) + 0.5) * (1.0 / (double) (1L << 53));
    }

    //! return a random integer in the [0,0xffffffffffffffffULL]-interval
    public long nextInt64() {
        long result = rotl(s1_ * 5, 7) * 9;

        long t = s1_ << 17L;

        s2_ ^= s0_;
        s3_ ^= s1_;
        s1_ ^= s2_;
        s0_ ^= s3_;

        s2_ ^= t;

        s3_ = rotl(s3_, 45);

        return result;
    }


    private static long rotl(long x, long k) {
        return (x << k) | (x >>> (64 - k));
    }

    public static void main(String[] args) {
        Xoshiro256StarStarUniformRng rng = new Xoshiro256StarStarUniformRng(1234);
        for (int i = 0; i < 10; i++) {
            System.out.println(rng.next().value);
        }
    }
}
