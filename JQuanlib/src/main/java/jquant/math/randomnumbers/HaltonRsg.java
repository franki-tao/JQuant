package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.PrimeNumbers;
import jquant.methods.montecarlo.SampleVector;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Halton low-discrepancy sequence generator
/*! Halton algorithm for low-discrepancy sequence.  For more
    details see chapter 8, paragraph 2 of "Monte Carlo Methods in
    Finance", by Peter Jäckel

    \test
    - the correctness of the returned values is tested by
      reproducing known good values.
    - the correctness of the returned values is tested by checking
      their discrepancy against known good values.
*/
public class HaltonRsg {
    private int dimensionality_;
    private long sequenceCounter_ = 0;
    private SampleVector sequence_;
    private List<Long> randomStart_;
    private List<Double> randomShift_;

    /**
     * @param dimensionality dimension
     * @param seed           = 0
     * @param randomStart    = true
     * @param randomShift    = false
     */
    public HaltonRsg(int dimensionality,
                     long seed,
                     boolean randomStart,
                     boolean randomShift) {
        dimensionality_ = dimensionality;
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality), 1.0);
        randomStart_ = CommonUtil.ArrayInit(dimensionality, 0L);
        randomShift_ = CommonUtil.ArrayInit(dimensionality, 0.0);
        QL_REQUIRE(dimensionality>0,
                "dimensionality must be greater than 0");

        if (randomStart || randomShift) {
            RandomSequenceGenerator uniformRsg = new RandomSequenceGenerator(dimensionality_,
                    RandomSequenceGenerator.RNG.MersenneTwisterUniformRng, seed);
            if (randomStart)
                randomStart_ = uniformRsg.nextInt32Sequence();
            if (randomShift)
                randomShift_ = uniformRsg.nextSequence().value;
        }
    }

    public SampleVector nextSequence() {
        ++sequenceCounter_;
        for (int i=0; i<dimensionality_; ++i) {
            double h = 0.0;
            long b = PrimeNumbers.get(i);
            double f = 1.0;
            long k = sequenceCounter_+randomStart_.get(i);
            while (k != 0) {
                f /= b;
                h += (k%b)*f;
                k /= b;
            }
            sequence_.value.set(i, h+randomShift_.get(i));
            sequence_.value.set(i, sequence_.value.get(i) - (long)((double)sequence_.value.get(i)));
        }
        return sequence_;
    }

    public SampleVector lastSequence() {
        return sequence_;
    }

    public int dimension() {return dimensionality_;}

    public static void main(String[] args) {
        HaltonRsg rsg = new HaltonRsg(3, 0, true, false);
        for (int i = 0; i < 10; i++) {
            System.out.println(rsg.nextSequence().value);
        }
    }
}
