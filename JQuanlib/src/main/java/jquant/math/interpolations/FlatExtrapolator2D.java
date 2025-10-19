package jquant.math.interpolations;

import jquant.math.interpolations.impl.FlatExtrapolator2DImpl;

/*! \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class FlatExtrapolator2D extends Interpolation2D {
    public FlatExtrapolator2D(Interpolation2D decoratedInterpolation) {
        impl_ = new FlatExtrapolator2DImpl(decoratedInterpolation);
    }
}
