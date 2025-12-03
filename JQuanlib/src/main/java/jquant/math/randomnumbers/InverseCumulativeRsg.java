package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.randomnumbers.impl.UsgImpl;
import jquant.methods.montecarlo.SampleVector;

//! Inverse cumulative random sequence generator
/*! It uses a sequence of uniform deviate in (0, 1) as the
    source of cumulative distribution values.
    Then an inverse cumulative distribution is used to calculate
    the distribution deviate.

    The uniform deviate sequence is supplied by USG.

    Class USG must implement the following interface:
    \code
        USG::sample_type USG::nextSequence() const;
        Size USG::dimension() const;
    \endcode

    The inverse cumulative distribution is supplied by IC.

    Class IC must implement the following interface:
    \code
        IC::IC();
        Real IC::operator() const;
    \endcode
*/
public class InverseCumulativeRsg {
    private UsgImpl uniformSequenceGenerator_;
    private int dimension_;
    private SampleVector x_;
    private Function ICD_;

    public InverseCumulativeRsg(UsgImpl usg) {
        uniformSequenceGenerator_ = usg;
        dimension_ = uniformSequenceGenerator_.dimension();
        x_ = new SampleVector(CommonUtil.ArrayInit(dimension_, 0d), 1.0);
    }

    public InverseCumulativeRsg(UsgImpl usg, Function inverseCum) {
        uniformSequenceGenerator_ = usg;
        dimension_ = uniformSequenceGenerator_.dimension();
        x_ = new SampleVector(CommonUtil.ArrayInit(dimension_, 0d), 1.0);
        ICD_ = inverseCum;
    }

    //! returns next sample from the inverse cumulative distribution
    public final SampleVector nextSequence() {
        SampleVector sample = uniformSequenceGenerator_.nextSequence();
        x_.weight = sample.weight;
        for (int i = 0; i < dimension_; i++) {
            x_.value.set(i, ICD_.value(sample.value.get(i)));
        }
        return x_;
    }

    public final SampleVector lastSequence() { return x_; }
    public int dimension() { return dimension_; }
}
