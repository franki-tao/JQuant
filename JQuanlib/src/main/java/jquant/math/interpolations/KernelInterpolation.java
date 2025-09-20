package jquant.math.interpolations;

import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.KernelInterpolationImpl;

//! Kernel interpolation between discrete points
/*! Implementation of the kernel interpolation approach, which can
    be found in "Foreign Exchange Risk" by Hakala, Wystup page
    256.

    The kernel in the implementation is kept general, although a Gaussian
    is considered in the cited text.

    \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class KernelInterpolation extends Interpolation {
    /*! \pre the \f$ x \f$ values must be sorted.
    \pre kernel needs a Real operator()(Real x) implementation

    The calculation will solve \f$ y = Ma \f$ for \f$a\f$.
    Due to singularity or rounding errors the recalculation
    \f$ Ma \f$ may not give \f$ y\f$. Here, a failure will
    be thrown if
    \f[
    \left\| Ma-y \right\|_\infty \geq \epsilon
    \f] */
    // epsilon = 1.0E-7(默认)
    public KernelInterpolation(double[] x, double[] y, Function kernel, double epsilon) {
        impl_ = new KernelInterpolationImpl(x, y, kernel, epsilon);
        impl_.update();
    }
}
