package jquant.math.interpolations;

import jquant.math.Interpolation;

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
}
