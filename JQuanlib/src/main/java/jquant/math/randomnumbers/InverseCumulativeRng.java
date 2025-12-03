package jquant.math.randomnumbers;

import jquant.math.Function;
import jquant.math.randomnumbers.impl.RngImpl;
import jquant.methods.montecarlo.SampleReal;

//! Inverse cumulative random number generator
/*! It uses a uniform deviate in (0, 1) as the source of cumulative
    distribution values.
    Then an inverse cumulative distribution is used to calculate
    the distribution deviate.

    The uniform deviate is supplied by RNG.

    Class RNG must implement the following interface:
    \code
        RNG::sample_type RNG::next() const;
    \endcode

    The inverse cumulative distribution is supplied by IC.

    Class IC must implement the following interface:
    \code
        IC::IC();
        Real IC::operator() const;
    \endcode
*/
public class InverseCumulativeRng {
    private RngImpl uniformGenerator_;
    private Function ICND_;

    public InverseCumulativeRng(final RngImpl ug) {
        uniformGenerator_ = ug;
    }

    //! returns a sample from a Gaussian distribution
    public SampleReal next() {
        SampleReal sample = uniformGenerator_.next();
        return new SampleReal(ICND_.value(sample.value), sample.weight);
    }
}
