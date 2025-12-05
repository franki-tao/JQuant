package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.RandomUtil;
import jquant.math.randomnumbers.impl.UsgImpl;
import jquant.methods.montecarlo.SampleVector;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! Scrambled sobol sequence according to Burley, 2020
 * ! Reference: Brent Burley: Practical Hash-based Owen Scrambling,
 * Journal of Computer Graphics Techniques, Vol. 9, No. 4, 2020
 */
public class Burley2020SobolRsg implements UsgImpl {
    private int dimensionality_;
    private long seed_;
    private SobolRsg.DirectionIntegers directionIntegers_;
    private SobolRsg sobolRsg_;
    private List<Long> integerSequence_;
    private SampleVector sequence_;
    private int nextSequenceCounter_;
    private List<Integer> group4Seeds_;

    /**
     *
     * @param dimensionality    dimension
     * @param seed              default = 42
     * @param directionIntegers default = Jaeckel
     * @param scrambleSeed      default = 43
     */
    public Burley2020SobolRsg(int dimensionality,
                              long seed,
                              SobolRsg.DirectionIntegers directionIntegers,
                              long scrambleSeed) {
        dimensionality_ = dimensionality;
        seed_ = seed;
        directionIntegers_ = directionIntegers;
        integerSequence_ = CommonUtil.ArrayInit(dimensionality);
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality,0d), 1.0);
        reset();
        group4Seeds_ = CommonUtil.ArrayInit((dimensionality_ - 1) / 4 + 1);
        MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(scrambleSeed);
        group4Seeds_.replaceAll(ignored -> (int) mt.nextInt32());
        integerSequence_ = new ArrayList<>();
    }

    public final List<Long> skipTo(int n) {
        reset();
        for (int k = 0; k < n + 1; ++k) {
            nextInt32Sequence();
        }
        return integerSequence_;
    }

    public final List<Long> nextInt32Sequence() {
        int n = RandomUtil.nested_uniform_scramble(nextSequenceCounter_, group4Seeds_.get(0));
        List<Long> seq = sobolRsg_.skipTo(n);
        integerSequence_.addAll(seq);
        int i = 0, group = 0;
        do {
            long seed = group4Seeds_.get(group++);
            for (int g = 0; g < 4 && i < dimensionality_; ++g, ++i) {
                seed = RandomUtil.local_hash_combine(seed, g);
                long l = integerSequence_.get(i);
                integerSequence_.set(i,
                        RandomUtil.nested_uniform_scramble((int)l, (int) seed) & 0xFFFFFFFFL) ;
            }
        } while (i < dimensionality_);
        QL_REQUIRE(++nextSequenceCounter_ != 0,
                "Burley2020SobolRsg::nextIn32Sequence(): period exceeded");
        integerSequence_.replaceAll(aLong -> aLong & 0xFFFFFFFFL);
        return integerSequence_;
    }

    @Override
    public final SampleVector nextSequence() {
        final List<Long> v = nextInt32Sequence();
        // normalize to get a double in (0,1)
        for (int k = 0; k < dimensionality_; ++k) {
            sequence_.value.set(k, (double) (v.get(k)) / 4294967296.0);
        }
        return sequence_;
    }

    public final SampleVector lastSequence() { return sequence_; }

    @Override
    public int dimension() { return dimensionality_; }


    private void reset() {
        sobolRsg_ = new SobolRsg(dimensionality_, seed_, directionIntegers_, false);
        nextSequenceCounter_ = 0;
    }

    public static void main(String[] args) {
        Burley2020SobolRsg rsg = new Burley2020SobolRsg(3, 42, SobolRsg.DirectionIntegers.Jaeckel, 43);
        for (int i = 0; i < 10; i++) {
            System.out.println(rsg.nextInt32Sequence());
        }
    }
}
