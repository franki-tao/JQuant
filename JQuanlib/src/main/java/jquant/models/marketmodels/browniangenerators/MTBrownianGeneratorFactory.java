package jquant.models.marketmodels.browniangenerators;

import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;

/**
 * Official QuantLib {@code ql/models/marketmodels/browniangenerators/mtbrowniangenerator.hpp/.cpp} factory.
 */
public class MTBrownianGeneratorFactory extends BrownianGeneratorFactory {

    private final long seed_;

    public MTBrownianGeneratorFactory() {
        this(0);
    }

    public MTBrownianGeneratorFactory(long seed) {
        seed_ = seed;
    }

    @Override
    public BrownianGenerator create(int factors, int steps) {
        return new MTBrownianGenerator(factors, steps, seed_);
    }
}
