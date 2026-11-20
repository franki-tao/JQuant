package jquant.experimental.barrieroption;

import jquant.Option;
import jquant.math.Interpolation;
import jquant.math.distributions.NormalDistribution;
import jquant.math.templateImpl;
import jquant.pricingengines.BlackFormula;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Vanna/Volga interpolation between discrete points.
 *
 * <p>Java port of {@code VannaVolgaInterpolation} from
 * {@code ql/experimental/barrieroption/vannavolgainterpolation.hpp}.
 * It interpolates three volatilities in strike space.
 */
public class VannaVolgaInterpolation extends Interpolation {

    public VannaVolgaInterpolation(double[] x,
                                   double[] y,
                                   double spot,
                                   double dDiscount,
                                   double fDiscount,
                                   double T) {
        impl_ = new VannaVolgaInterpolationImpl(x, y, spot, dDiscount, fDiscount, T);
        impl_.update();
    }

    /** Interpolation implementation. */
    private static class VannaVolgaInterpolationImpl extends templateImpl {
        private final double[] premiaBS = new double[3];
        private final double[] premiaMKT = new double[3];
        private final double[] vegas = new double[3];
        private double atmVol_;
        private final double spot_;
        private double fwd_;
        private final double dDiscount_;
        private final double fDiscount_;
        private final double T_;

        VannaVolgaInterpolationImpl(double[] x,
                                    double[] y,
                                    double spot,
                                    double dDiscount,
                                    double fDiscount,
                                    double T) {
            super(x, y, VannaVolga.requiredPoints);
            spot_ = spot;
            dDiscount_ = dDiscount;
            fDiscount_ = fDiscount;
            T_ = T;

            QL_REQUIRE(xValue.length == 3,
                    "Vanna Volga Interpolator only interpolates 3 volatilities in strike space");
        }

        @Override
        public void update() {
            // atmVol should be the second vol
            atmVol_ = yValue[1];
            fwd_ = spot_ * fDiscount_ / dDiscount_;
            for (int i = 0; i < 3; i++) {
                premiaBS[i] = BlackFormula.blackFormula(Option.Type.Call,
                        xValue[i], fwd_, atmVol_ * Math.sqrt(T_), dDiscount_, 0.0);
                premiaMKT[i] = BlackFormula.blackFormula(Option.Type.Call,
                        xValue[i], fwd_, yValue[i] * Math.sqrt(T_), dDiscount_, 0.0);
                vegas[i] = vega(xValue[i]);
            }
        }

        @Override
        public double value(double k) {
            double x1 = vega(k) / vegas[0]
                    * (Math.log(xValue[1] / k) * Math.log(xValue[2] / k))
                    / (Math.log(xValue[1] / xValue[0])
                    * Math.log(xValue[2] / xValue[0]));
            double x2 = vega(k) / vegas[1]
                    * (Math.log(k / xValue[0]) * Math.log(xValue[2] / k))
                    / (Math.log(xValue[1] / xValue[0])
                    * Math.log(xValue[2] / xValue[1]));
            double x3 = vega(k) / vegas[2]
                    * (Math.log(k / xValue[0]) * Math.log(k / xValue[1]))
                    / (Math.log(xValue[2] / xValue[0])
                    * Math.log(xValue[2] / xValue[1]));

            double cBS = BlackFormula.blackFormula(Option.Type.Call, k, fwd_,
                    atmVol_ * Math.sqrt(T_), dDiscount_, 0.0);
            double c = cBS + x1 * (premiaMKT[0] - premiaBS[0])
                    + x2 * (premiaMKT[1] - premiaBS[1])
                    + x3 * (premiaMKT[2] - premiaBS[2]);
            double std = BlackFormula.blackFormulaImpliedStdDev(
                    Option.Type.Call, k, fwd_, c, dDiscount_, 0.0,
                    Double.NaN, 1.0e-6, 100);
            return std / Math.sqrt(T_);
        }

        @Override
        public double primitive(double v) {
            QL_FAIL("Vanna Volga primitive not implemented");
            return 0.0;
        }

        @Override
        public double derivative(double v) {
            QL_FAIL("Vanna Volga derivative not implemented");
            return 0.0;
        }

        @Override
        public double secondDerivative(double v) {
            QL_FAIL("Vanna Volga secondDerivative not implemented");
            return 0.0;
        }

        private double vega(double k) {
            double d1 = (Math.log(fwd_ / k) + 0.5 * atmVol_ * atmVol_ * T_)
                    / (atmVol_ * Math.sqrt(T_));
            NormalDistribution norm = new NormalDistribution();
            return spot_ * dDiscount_ * Math.sqrt(T_) * norm.value(d1);
        }
    }

    /**
     * VannaVolga-interpolation factory and traits.
     */
    public static class VannaVolga {
        public static final int requiredPoints = 3;

        private final double spot_;
        private final double dDiscount_;
        private final double fDiscount_;
        private final double T_;

        public VannaVolga(double spot,
                          double dDiscount,
                          double fDiscount,
                          double T) {
            spot_ = spot;
            dDiscount_ = dDiscount;
            fDiscount_ = fDiscount;
            T_ = T;
        }

        public Interpolation interpolate(double[] x, double[] y) {
            return new VannaVolgaInterpolation(x, y, spot_, dDiscount_, fDiscount_, T_);
        }
    }
}
