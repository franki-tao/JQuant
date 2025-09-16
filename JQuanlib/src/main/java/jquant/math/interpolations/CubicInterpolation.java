package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.CubicInterpolationImpl;

import java.util.List;

//! %Cubic interpolation between discrete points.
    /*! Cubic interpolation is fully defined when the ${f_i}$ function values
        at points ${x_i}$ are supplemented with ${f^'_i}$ function derivative
        values.

        Different type of first derivative approximations are implemented,
        both local and non-local. Local schemes (Fourth-order, Parabolic,
        Modified Parabolic, Fritsch-Butland, Akima, Kruger) use only $f$ values
        near $x_i$ to calculate each $f^'_i$. Non-local schemes (Spline with
        different boundary conditions) use all ${f_i}$ values and obtain
        ${f^'_i}$ by solving a linear system of equations. Local schemes
        produce $C^1$ interpolants, while the spline schemes generate $C^2$
        interpolants.

        Hyman's monotonicity constraint filter is also implemented: it can be
        applied to all schemes to ensure that in the regions of local
        monotoniticity of the input (three successive increasing or decreasing
        values) the interpolating cubic remains monotonic. If the interpolating
        cubic is already monotonic, the Hyman filter leaves it unchanged
        preserving all its original features.

        In the case of $C^2$ interpolants the Hyman filter ensures local
        monotonicity at the expense of the second derivative of the interpolant
        which will no longer be continuous in the points where the filter has
        been applied.

        While some non-linear schemes (Modified Parabolic, Fritsch-Butland,
        Kruger) are guaranteed to be locally monotonic in their original
        approximation, all other schemes must be filtered according to the
        Hyman criteria at the expense of their linearity.

        See R. L. Dougherty, A. Edelman, and J. M. Hyman,
        "Nonnegativity-, Monotonicity-, or Convexity-Preserving CubicSpline and
        Quintic Hermite Interpolation"
        Mathematics Of Computation, v. 52, n. 186, April 1989, pp. 471-494.

        \todo implement missing schemes (FourthOrder and ModifiedParabolic) and
              missing boundary conditions (Periodic and Lagrange).

        \test to be adapted from old ones.

        \ingroup interpolations
        \warning See the Interpolation class for information about the
                 required lifetime of the underlying data.
    */
public class CubicInterpolation extends Interpolation {
    public enum DerivativeApprox {
        /*! Spline approximation (non-local, non-monotonic, linear[?]).
            Different boundary conditions can be used on the left and right
            boundaries: see BoundaryCondition.
        */
        Spline,

        //! Overshooting minimization 1st derivative
        SplineOM1,

        //! Overshooting minimization 2nd derivative
        SplineOM2,

        //! Fourth-order approximation (local, non-monotonic, linear)
        FourthOrder,

        //! Parabolic approximation (local, non-monotonic, linear)
        Parabolic,

        //! Fritsch-Butland approximation (local, monotonic, non-linear)
        FritschButland,

        //! Akima approximation (local, non-monotonic, non-linear)
        Akima,

        //! Kruger approximation (local, monotonic, non-linear)
        Kruger,

        //! Weighted harmonic mean approximation (local, monotonic, non-linear)
        Harmonic,
    };
    public enum BoundaryCondition {
        //! Make second(-last) point an inactive knot
        NotAKnot,

        //! Match value of end-slope
        FirstDerivative,

        //! Match value of second derivative at end
        SecondDerivative,

        //! Match first and second derivative at either end
        Periodic,

        /*! Match end-slope to the slope of the cubic that matches
            the first four data at the respective end
        */
        Lagrange
    };

    private CubicInterpolationImpl temp;

    public CubicInterpolation(double[] x,
                              double[] y,
                              DerivativeApprox da,
                              boolean monotonic,
                              BoundaryCondition leftCond,
                              double leftConditionValue,
                              BoundaryCondition rightCond,
                              double rightConditionValue) {
        temp = new CubicInterpolationImpl(x,y,
                da,
                monotonic,
                leftCond,
                leftConditionValue,
                rightCond,
                rightConditionValue);
        impl_ = temp;
        impl_.update();
    }

    public final List<Double> primitiveConstants() {
        return temp.primitiveConst_;
    }
    public final List<Double> aCoefficients() { return temp.a_; }
    public final List<Double> bCoefficients() { return temp.b_; }
    public final List<Double> cCoefficients() { return temp.c_; }
    public final List<Boolean> monotonicityAdjustments() {
        return temp.monotonicityAdjustments_;
    }
}
