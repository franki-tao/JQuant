package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.FlatExtrapolatorImpl;

/*! \ingroup interpolations
    Decorator that turns any 1-D interpolation into one that
    extrapolates flat (i.e. clamping to the boundary values)
    outside the original data range.

    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class FlatExtrapolator extends Interpolation {
    public FlatExtrapolator(Interpolation decoratedInterpolation) {
        impl_ = new FlatExtrapolatorImpl(decoratedInterpolation);
    }
}
