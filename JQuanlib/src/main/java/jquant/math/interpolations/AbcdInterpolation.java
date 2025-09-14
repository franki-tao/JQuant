package jquant.math.interpolations;

import jquant.math.CommonUtil;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.AbcdInterpolationImpl;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;

import java.util.List;

public class AbcdInterpolation extends Interpolation {
    /*
      Real a = -0.06,
      Real b =  0.17,
      Real c =  0.54,
      Real d =  0.17,
      bool aIsFixed = false,
      bool bIsFixed = false,
      bool cIsFixed = false,
      bool dIsFixed = false,
      bool vegaWeighted = false,
      const ext::shared_ptr<EndCriteria>& endCriteria
          = ext::shared_ptr<EndCriteria>(),
      const ext::shared_ptr<OptimizationMethod>& optMethod
          = ext::shared_ptr<OptimizationMethod>()
     */
    public AbcdInterpolation(double[] x, double[] y, double a, double b, double c, double d,
                             boolean aIsFixed, boolean bIsFixed, boolean cIsFixed, boolean dIsFixed,
                             boolean vegaWeighted, EndCriteria endCriteria, OptimizationMethod optMethod) {
        impl_ = new AbcdInterpolationImpl(x, y, a, b, c, d,
                aIsFixed, bIsFixed, cIsFixed, dIsFixed,
                vegaWeighted, endCriteria, optMethod);
        impl_.update();
    }

    /*
    Real a() const { return coeffs().a_; }
        Real b() const { return coeffs().b_; }
        Real c() const { return coeffs().c_; }
        Real d() const { return coeffs().d_; }
        std::vector<Real> k() const { return coeffs().k_; }
        Real rmsError() const { return coeffs().error_; }
        Real maxError() const { return coeffs().maxError_; }
        EndCriteria::Type endCriteria(){ return coeffs().abcdEndCriteria_; }
        template <class I1>
        Real k(Time t, const I1& xBegin, const I1& xEnd) const {
            LinearInterpolation li(xBegin, xEnd, (coeffs().k_).begin());
            return li(t);
        }
     */
    public double a() {return coeffs().a_;}
    public double b() {return coeffs().b_;}
    public double c() {return coeffs().c_;}

    public double d() {return coeffs().d_;}

    public List<Double> k() {return coeffs().k_;}

    public double rmsError() {return coeffs().error_;}
    public double maxError() {return coeffs().maxError_;}

    public EndCriteria.Type endCriteria(){ return coeffs().abcdEndCriteria_; }

    public double k(double t, double[] xx) {
        LinearInterpolation li = new LinearInterpolation(xx, CommonUtil.toArray(coeffs().k_));
        return li.value(t, false);
    }


    private final AbcdInterpolationImpl coeffs() {
        return (AbcdInterpolationImpl) impl_;
    }
}
