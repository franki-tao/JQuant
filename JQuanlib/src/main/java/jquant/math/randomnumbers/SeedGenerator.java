package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.patterns.Singleton;

import java.util.List;
import java.util.Locale;

public class SeedGenerator implements Singleton<SeedGenerator> {

    public static SeedGenerator INSTANCE = new SeedGenerator();

    private MersenneTwisterUniformRng rng_;

    private SeedGenerator() {
        rng_ = new MersenneTwisterUniformRng(42L);
        initialize();
    }

    private void initialize() {

        // firstSeed is chosen based on clock() and used for the first rng
        long firstSeed = System.currentTimeMillis();
        MersenneTwisterUniformRng first = new MersenneTwisterUniformRng(firstSeed);

        // secondSeed is as random as it could be
        // feel free to suggest improvements
        long secondSeed = first.nextInt32();

        MersenneTwisterUniformRng second = new MersenneTwisterUniformRng(secondSeed);

        // use the second rng to initialize the final one
        long skip = second.nextInt32() % 1000;
        List<Long> init = CommonUtil.ArrayInit(4);
        init.set(0, second.nextInt32());
        init.set(1, second.nextInt32());
        init.set(2, second.nextInt32());
        init.set(3, second.nextInt32());

        rng_ = new MersenneTwisterUniformRng(init);

        for (long i = 0; i < skip; i++)
            rng_.nextInt32();
    }

    public long get() {
        return rng_.nextInt32();
    }
}
