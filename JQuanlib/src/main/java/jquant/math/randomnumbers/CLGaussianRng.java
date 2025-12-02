package jquant.math.randomnumbers;

import jquant.math.randomnumbers.impl.RngImpl;
import jquant.methods.montecarlo.SampleReal;

/**
 * ! Gaussian random number generator
 * ! It uses the well-known fact that the sum of 12 uniform deviate
 * in (-.5,.5) is approximately a Gaussian deviate with average 0
 * and standard deviation 1.  The uniform deviate is supplied by
 * RNG.
 * <p>
 * Class RNG must implement the following interface:
 * \code
 * RNG::sample_type RNG::next() const;
 * \endcode
 */
public class CLGaussianRng {
    private RngImpl uniformGenerator_;

    public CLGaussianRng(RngImpl uniformGenerator) {
        uniformGenerator_ = uniformGenerator;
    }

    /**
     * ! returns a sample from a Gaussian distribution
     */
    public SampleReal next() {
        double gaussPoint = -6.0, gaussWeight = 1.0;
        for (int i = 1; i <= 12; i++) {
            SampleReal sample = uniformGenerator_.next();
            gaussPoint += sample.value;
            gaussWeight *= sample.weight;
        }
        return new SampleReal(gaussPoint, gaussWeight);
    }
}
