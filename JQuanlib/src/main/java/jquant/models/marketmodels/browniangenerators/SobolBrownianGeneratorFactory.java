package jquant.models.marketmodels.browniangenerators;

import jquant.math.randomnumbers.SobolRsg;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/browniangenerators/sobolbrowniangenerator.hpp/.cpp} factory.
 *
 * <p>Official defaults: seed 0, {@link SobolRsg.DirectionIntegers#Jaeckel}.
 */
public class SobolBrownianGeneratorFactory extends BrownianGeneratorFactory {

    private final SobolBrownianGeneratorBase.Ordering ordering_;
    private final long seed_;
    private final SobolRsg.DirectionIntegers integers_;

    public SobolBrownianGeneratorFactory(SobolBrownianGeneratorBase.Ordering ordering) {
        this(ordering, 0);
    }

    public SobolBrownianGeneratorFactory(SobolBrownianGeneratorBase.Ordering ordering,
                                         long seed) {
        this(ordering, seed, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGeneratorFactory(SobolBrownianGeneratorBase.Ordering ordering,
                                         long seed,
                                         SobolRsg.DirectionIntegers directionIntegers) {
        ordering_ = ordering;
        seed_ = seed;
        integers_ = directionIntegers;
    }

    @Override
    public BrownianGenerator create(int factors, int steps) {
        return new SobolBrownianGenerator(factors, steps, ordering_, seed_, integers_);
    }
}
