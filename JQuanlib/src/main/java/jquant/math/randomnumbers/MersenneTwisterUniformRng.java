package jquant.math.randomnumbers;

import jquant.math.randomnumbers.impl.RngImpl;
import jquant.methods.montecarlo.SampleReal;

import java.util.List;

//! Uniform random number generator
/*! Mersenne Twister random number generator of period 2**19937-1

    For more details see http://www.math.keio.ac.jp/matumoto/emt.html

    \test the correctness of the returned values is tested by
          checking them against known good results.
*/
public class MersenneTwisterUniformRng extends RngImpl {
    private static final int N = 624; // state size
    private static final int M = 397; // shift size
    private static final long MATRIX_A = 0x9908b0dfL;
    private static final long UPPER_MASK=0x80000000L;
    private static final long LOWER_MASK=0x7fffffffL;
    private long[] mt = new long[N];
    private int mti;

    /*! if the given seed is 0, a random seed will be chosen
            based on clock() */
    public MersenneTwisterUniformRng(long seed) {
        seedInitialization(seed);
    }

    public MersenneTwisterUniformRng(List<Long> seeds) {
        seedInitialization(19650218L);
        int i=1, j=0, k = (Math.max(N, seeds.size()));
        for (; k != 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i-1] ^ (mt[i-1] >>> 30)) * 1664525L))
            + seeds.get(j) + j; /* non linear */
            mt[i] &= 0xffffffffL; /* for WORDSIZE > 32 machines */
            i++; j++;
            if (i>=N) { mt[0] = mt[N-1]; i=1; }
            if (j>=seeds.size()) j=0;
        }
        for (k = N - 1; k != 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i-1] ^ (mt[i-1] >>> 30)) * 1566083941L))
            - i; /* non linear */
            mt[i] &= 0xffffffffL; /* for WORDSIZE > 32 machines */
            i++;
            if (i>=N) { mt[0] = mt[N-1]; i=1; }
        }

        mt[0] = UPPER_MASK; /*MSB is 1; assuring non-zero initial array*/
    }

    private void seedInitialization(long seed) {
        long s = (seed != 0 ? seed : new SeedGenerator().get());
        mt[0]= s & 0xffffffffL;
        for (mti=1; mti<N; mti++) {
            mt[mti] =
                    (1812433253L * (mt[mti-1] ^ (mt[mti-1] >>> 30)) + mti);
            /* See Knuth TAOCP Vol2. 3rd Ed. P.106 for multiplier. */
            /* In the previous versions, MSBs of the seed affect   */
            /* only MSBs of the array mt[].                        */
            /* 2002/01/09 modified by Makoto Matsumoto             */
            mt[mti] &= 0xffffffffL;
            /* for >32 bit machines */
        }
    }

    private void twist() {
        final long[] mag01={0x0L, MATRIX_A};
        /* mag01[x] = x * MATRIX_A  for x=0,1 */
        int kk;
        long y;

        for (kk=0;kk<N-M;kk++) {
            y = (mt[kk]&UPPER_MASK)|(mt[kk+1]&LOWER_MASK);
            mt[kk] = mt[kk+M] ^ (y >>> 1) ^ mag01[(int) (y & 0x1L)];
        }
        for (;kk<N-1;kk++) {
            y = (mt[kk]&UPPER_MASK)|(mt[kk+1]&LOWER_MASK);
            mt[kk] = mt[(kk+M)-N] ^ (y >>> 1) ^ mag01[(int) (y & 0x1L)];
        }
        y = (mt[N-1]&UPPER_MASK)|(mt[0]&LOWER_MASK);
        mt[N-1] = mt[M-1] ^ (y >>> 1) ^ mag01[(int) (y & 0x1L)];

        mti = 0;
    }
    //! return a random number in the (0.0, 1.0)-interval
    public double nextReal() {
        return ((nextInt32()) + 0.5)/4294967296.0;
    }

    /*! returns a sample with weight 1.0 containing a random number
            in the (0.0, 1.0) interval  */
    @Override
    public SampleReal next() {
        return new SampleReal(nextReal(), 1d);
    }
    //! return a random integer in the [0,0xffffffff]-interval
    @Override
    public long nextInt32() {
        if (mti==N)
            twist(); /* generate N words at a time */

        long y = mt[mti++];

        /* Tempering */
        y ^= (y >>> 11);
        y ^= (y << 7) & 0x9d2c5680L;
        y ^= (y << 15) & 0xefc60000L;
        y ^= (y >>> 18);
        return y;
    }

    public static void main(String[] args) {
        List<Long> init = List.of((long)0x123, (long)0x234,(long)0x345,(long)0x456);
        MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(init);
        for (int i = 0; i < 10; i++) {
            System.out.println(rng.nextInt32());
        }
    }
}
