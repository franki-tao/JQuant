package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.methods.montecarlo.SampleReal;

import java.util.List;

import static jquant.math.MathUtils.QL_EPSILON;

/**
 * ! Uniform random number generator
 * ! Random number generator of L'Ecuyer with added Bays-Durham
 * shuffle (know as ran2 in Numerical recipes)
 * <p>
 * For more details see Section 7.1 of Numerical Recipes in C, 2nd
 * Edition, Cambridge University Press (available at
 * http://www.nr.com/)
 */
public class LecuyerUniformRng {
    private long temp1, temp2;
    private long y;
    private List<Long> buffer;
    private static final long m1 = 2147483563L;
    private static final long a1 = 40014L;
    private static final long q1 = 53668L;
    private static final long r1 = 12211L;
    private static final long m2 = 2147483399L;
    private static final long a2 = 40692L;
    private static final long q2 = 52774L;
    private static final long r2 = 3791L;
    private static final int bufferSize = 32;
    private static final long bufferNormalizer = 67108862L;
    private static final double maxRandom = 1.0 - QL_EPSILON;

    /**
     * ! if the given seed is 0, a random seed will be chosen
     * based on clock()
     */
    public LecuyerUniformRng(long seed) {
        buffer = CommonUtil.ArrayInit(bufferSize);
        // Need to prevent seed=0, so use seed=0 to have a "random" seed
        temp2 = temp1 = (seed != 0 ? seed : SeedGenerator.INSTANCE.get());
        // Load the shuffle table (after 8 warm-ups)
        for (int j = bufferSize + 7; j >= 0; j--) {
            long k = temp1 / q1;
            temp1 = a1 * (temp1 - k * q1) - k * r1;
            if (temp1 < 0)
                temp1 += m1;
            if (j < bufferSize)
                buffer.set(j, temp1);
        }
        y = buffer.get(0);
    }

    /**
     * ! returns a sample with weight 1.0 containing a random number
     * uniformly chosen from (0.0,1.0)
     */
    public SampleReal next() {
        long k = temp1 / q1;
        // Compute temp1=(a1*temp1) % m1
        // without overflows (Schrage's method)
        temp1 = a1 * (temp1 - k * q1) - k * r1;
        if (temp1 < 0)
            temp1 += m1;
        k = temp2 / q2;
        // Compute temp2=(a2*temp2) % m2
        // without overflows (Schrage's method)
        temp2 = a2 * (temp2 - k * q2) - k * r2;
        if (temp2 < 0)
            temp2 += m2;
        // Will be in the range 0..bufferSize-1
        int j = (int) (y / bufferNormalizer);
        // Here temp1 is shuffled, temp1 and temp2 are
        // combined to generate output
        y = buffer.get(j) - temp2;
        buffer.set(j, temp1);
        if (y < 1)
            y += m1 - 1;
        double result = y / (double) (m1);
        // users don't expect endpoint values
        if (result > maxRandom)
            result = (double) maxRandom;
        return new SampleReal(result, 1.0);
    }

    public static void main(String[] args) {
        LecuyerUniformRng rng = new LecuyerUniformRng(1234);
        for (int i = 0; i < 10; i++) {
            System.out.println(rng.next().value);
        }
    }
}
