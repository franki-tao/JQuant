package jquant.models.marketmodels.browniangenerators;

import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.randomnumbers.Burley2020SobolRsg;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.SobolRsg;
import jquant.methods.montecarlo.SampleVector;

public class Burley2020SobolBrownianGenerator extends SobolBrownianGeneratorBase {
    private InverseCumulativeRsg generator_;

    /**
     *
     * @param factors           factors
     * @param steps             steps
     * @param ordering          ordering
     * @param seed              = 42
     * @param directionIntegers = SobolRsg::Jaeckel
     * @param scrambleSeed      = 43
     */
    public Burley2020SobolBrownianGenerator(int factors,
                                            int steps,
                                            Ordering ordering,
                                            long seed,
                                            SobolRsg.DirectionIntegers directionIntegers,
                                            long scrambleSeed) {
        super(factors, steps, ordering);
        generator_ = new InverseCumulativeRsg(new Burley2020SobolRsg(factors * steps, seed, directionIntegers, scrambleSeed),
                new InverseCumulativeNormal());
    }

    @Override
    protected SampleVector nextSequence() {
        return generator_.nextSequence();
    }
}
