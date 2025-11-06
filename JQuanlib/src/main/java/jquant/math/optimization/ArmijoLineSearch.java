package jquant.math.optimization;

//! Armijo line search.
/*! Let \f$ \alpha \f$ and \f$ \beta \f$ be 2 scalars in \f$ [0,1]
   \f$.  Let \f$ x \f$ be the current value of the unknown, \f$ d
   \f$ the search direction and \f$ t \f$ the step. Let \f$ f \f$
   be the function to minimize.  The line search stops when \f$ t
   \f$ verifies
   \f[ f(x + t \cdot d) - f(x) \leq -\alpha t f'(x+t \cdot d) \f]
   and
   \f[ f(x+\frac{t}{\beta} \cdot d) - f(x) > -\frac{\alpha}{\beta}
       t f'(x+t \cdot d) \f]

   (see Polak, Algorithms and consistent approximations, Optimization,
   volume 124 of Applied Mathematical Sciences, Springer-Verlag, NY,
   1997)
*/
public class ArmijoLineSearch extends LineSearch{
}
