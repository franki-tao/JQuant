package jquant.models.marketmodels.browniangenerators;

import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.SobolRsg;
import jquant.methods.montecarlo.SampleVector;

public class SobolBrownianGenerator extends SobolBrownianGeneratorBase {
    private InverseCumulativeRsg generator_;
    public SobolBrownianGenerator(int factors,
                                  int steps,
                                  Ordering ordering,
                                  long seed,
                                  SobolRsg.DirectionIntegers directionIntegers) {
        super(factors,steps,ordering);
        generator_ = new InverseCumulativeRsg(new SobolRsg(factors * steps, seed, directionIntegers, true),
                new InverseCumulativeNormal());
    }
    @Override
    protected SampleVector nextSequence() {
        return generator_.nextSequence();
    }
}
