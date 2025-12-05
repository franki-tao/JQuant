package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.RandomUtil;
import jquant.math.randomnumbers.impl.UsgImpl;
import jquant.methods.montecarlo.SampleVector;
import jquant.models.marketmodels.browniangenerators.SobolBrownianGenerator;

import static jquant.math.randomnumbers.SobolRsg.DirectionIntegers.JoeKuoD7;
import static jquant.models.marketmodels.browniangenerators.SobolBrownianGeneratorBase.Ordering.Diagonal;

public class SobolBrownianBridgeRsg implements UsgImpl {
    private SampleVector seq_;
    private SobolBrownianGenerator gen_;

    /**
     *
     * @param factors
     * @param steps
     * @param ordering default SobolBrownianGenerator::Diagonal
     * @param seed default 0
     * @param directionIntegers default SobolRsg::JoeKuoD7
     */
    public SobolBrownianBridgeRsg(int factors,
                                  int steps,
                                  SobolBrownianGenerator.Ordering ordering,
                                  long seed,
                                  SobolRsg.DirectionIntegers directionIntegers) {
        seq_ = new SampleVector(CommonUtil.ArrayInit(factors*steps, 0d), 1.0);
        gen_ = new SobolBrownianGenerator(factors, steps, ordering, seed, directionIntegers);
    }

    @Override
    public final SampleVector nextSequence() {
        RandomUtil.setNextSequence(gen_, seq_.value);
        return seq_;
    }

    public final SampleVector lastSequence() {
        return seq_;
    }

    @Override
    public int dimension() {
        return gen_.numberOfFactors() * gen_.numberOfSteps();
    }

    public static void main(String[] args) {
        SobolBrownianBridgeRsg rsg = new SobolBrownianBridgeRsg(10,2, Diagonal, 0, JoeKuoD7);
        for (int i = 0; i < 20; i++) {
            System.out.println(rsg.nextSequence().value);
        }
    }
}
