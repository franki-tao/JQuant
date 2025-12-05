package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.RandomUtil;
import jquant.methods.montecarlo.SampleVector;
import jquant.models.marketmodels.browniangenerators.Burley2020SobolBrownianGenerator;
import jquant.models.marketmodels.browniangenerators.SobolBrownianGenerator;

import static jquant.models.marketmodels.browniangenerators.SobolBrownianGeneratorBase.Ordering.Diagonal;

public class Burley2020SobolBrownianBridgeRsg {
    private SampleVector seq_;
    private Burley2020SobolBrownianGenerator gen_;

    /**
     *
     * @param factors factors
     * @param steps steps
     * @param ordering  = SobolBrownianGenerator::Diagonal
     * @param seed  = 42
     * @param directionIntegers  = SobolRsg::JoeKuoD7
     * @param scrambleSeed  = 43
     */
    public Burley2020SobolBrownianBridgeRsg(int factors,
                                            int steps,
                                            SobolBrownianGenerator.Ordering ordering,
                                            long seed,
                                            SobolRsg.DirectionIntegers directionIntegers,
                                            long scrambleSeed) {
        seq_ = new SampleVector(CommonUtil.ArrayInit(factors * steps, 0d), 1.0);
        gen_ = new Burley2020SobolBrownianGenerator(factors, steps, ordering, seed, directionIntegers, scrambleSeed);
    }

    public final SampleVector nextSequence() {
        RandomUtil.setNextSequence(gen_, seq_.value);
        return seq_;
    }

    public final SampleVector lastSequence() {
        return seq_;
    }

    public int dimension() {
        return gen_.numberOfFactors() * gen_.numberOfSteps();
    }

    public static void main(String[] args) {
        Burley2020SobolBrownianBridgeRsg rsg = new Burley2020SobolBrownianBridgeRsg(2,3,
                Diagonal,42, SobolRsg.DirectionIntegers.JoeKuoD7, 43);
        for (int i = 0; i < 10; i++) {
            System.out.println(rsg.nextSequence().value);
        }

    }
}
