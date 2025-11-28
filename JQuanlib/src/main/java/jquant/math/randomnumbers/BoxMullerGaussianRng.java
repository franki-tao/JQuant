package jquant.math.randomnumbers;

import jquant.math.randomnumbers.impl.RngImpl;
import jquant.methods.montecarlo.SampleReal;

/**
 * ! Gaussian random number generator
 * ! It uses the well-known Box-Muller transformation to return a
 *   normal distributed Gaussian deviate with average 0.0 and
 *   standard deviation of 1.0, from a uniform deviate in (0,1)
 *   supplied by RNG.
 *
 *   Class RNG must implement the following interface:
 *   \code
 *       RNG::sample_type RNG::next() const;
 *   \endcode
 */
public class BoxMullerGaussianRng {
    private RngImpl uniformGenerator_;
    private boolean returnFirst_ = true;
    private double firstValue_,secondValue_;
    private double firstWeight_,secondWeight_;
    private double weight_ = 0.0;

    public BoxMullerGaussianRng(RngImpl uniformGenerator) {
        this.uniformGenerator_ = uniformGenerator;
    }

    /**
     *
     * @return ! returns a sample from a Gaussian distribution
     */
    public SampleReal next() {
        if (returnFirst_) {
            double x1,x2,r,ratio;
            do {
                SampleReal s1 = uniformGenerator_.next();
                x1 = s1.value*2.0-1.0;
                firstWeight_ = s1.weight;
                SampleReal s2 = uniformGenerator_.next();
                x2 = s2.value*2.0-1.0;
                secondWeight_ = s2.weight;
                r = x1*x1+x2*x2;
            } while (r>=1.0 || r==0.0);

            ratio = Math.sqrt(-2.0*Math.log(r)/r);
            firstValue_ = x1*ratio;
            secondValue_ = x2*ratio;
            weight_ = firstWeight_*secondWeight_;

            returnFirst_ = false;
            return new SampleReal(firstValue_, weight_);
        } else {
            returnFirst_ = true;
            return new SampleReal(secondValue_, weight_);
        }
    }
}
