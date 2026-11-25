package jquant.cashflows;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.indexes.SwapIndex;
import jquant.instruments.VanillaSwap;
import jquant.math.Function;
import jquant.math.solvers1d.Newton;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Factory for the G functions used by Hagan's CMS pricers.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp/.cpp}
 * ({@code GFunctionFactory}). The yield-curve models are
 * {@link YieldCurveModel#Standard}, {@link YieldCurveModel#ExactYield},
 * {@link YieldCurveModel#ParallelShifts} and
 * {@link YieldCurveModel#NonParallelShifts}.
 *
 * <p>Java {@link Date} is mutable; dates stored by the G functions are copied.
 */
public class GFunctionFactory {

    /** Official {@code GFunctionFactory::YieldCurveModel}. */
    public enum YieldCurveModel {
        Standard, ExactYield, ParallelShifts, NonParallelShifts
    }

    public static GFunction newGFunctionStandard(int q,
                                                 double delta,
                                                 int swapLength) {
        return new GFunctionStandard(q, delta, swapLength);
    }

    public static GFunction newGFunctionExactYield(final CmsCoupon coupon) {
        return new GFunctionExactYield(coupon);
    }

    public static GFunction newGFunctionWithShifts(
            final CmsCoupon coupon,
            Handle<Quote> meanReversion) {
        return new GFunctionWithShifts(coupon, meanReversion);
    }

    /**
     * Official {@code GFunctionFactory::GFunctionStandard}. The formulas are
     * copied term by term from {@code conundrumpricer.cpp}.
     */
    public static class GFunctionStandard extends GFunction {

        private final int q_;
        private final double delta_;
        private final int swapLength_;

        public GFunctionStandard(int q, double delta, int swapLength) {
            q_ = q;
            delta_ = delta;
            swapLength_ = swapLength;
        }

        @Override
        public double value(double x) {
            double n = (double) swapLength_ * q_;
            return x / Math.pow(1.0 + x / q_, delta_) * 1.0
                    / (1.0 - 1.0 / Math.pow(1.0 + x / q_, n));
        }

        @Override
        public double firstDerivative(double x) {
            double n = (double) swapLength_ * q_;
            double a = 1.0 + x / q_;
            double aa = a - delta_ / q_ * x;
            double b = Math.pow(a, n - delta_ - 1.0) / (Math.pow(a, n) - 1.0);

            double secNum = n * x * Math.pow(a, n - 1.0);
            double secDen = q_ * Math.pow(a, delta_)
                    * (Math.pow(a, n) - 1.0) * (Math.pow(a, n) - 1.0);
            double sec = secNum / secDen;

            return aa * b - sec;
        }

        @Override
        public double secondDerivative(double x) {
            double n = (double) swapLength_ * q_;
            double a = 1.0 + x / q_;
            double aa = a - delta_ / q_ * x;
            double a1 = (1.0 - delta_) / q_;
            double b = Math.pow(a, n - delta_ - 1.0) / (Math.pow(a, n) - 1.0);
            double num = (1.0 + delta_ - n) * Math.pow(a, n - delta_ - 2.0)
                    - (1.0 + delta_) * Math.pow(a, 2.0 * n - delta_ - 2.0);
            double den = (Math.pow(a, n) - 1.0) * (Math.pow(a, n) - 1.0);
            double b1 = 1.0 / q_ * num / den;

            double c = x / Math.pow(a, delta_);
            double c1 = (Math.pow(a, delta_)
                    - delta_ / q_ * x * Math.pow(a, delta_ - 1.0))
                    / Math.pow(a, 2.0 * delta_);

            double d = Math.pow(a, n - 1.0)
                    / ((Math.pow(a, n) - 1.0) * (Math.pow(a, n) - 1.0));
            double d1 = ((n - 1.0) * Math.pow(a, n - 2.0) * (Math.pow(a, n) - 1.0)
                    - 2.0 * n * Math.pow(a, 2.0 * (n - 1.0)))
                    / (q_ * (Math.pow(a, n) - 1.0) * (Math.pow(a, n) - 1.0)
                    * (Math.pow(a, n) - 1.0));

            return a1 * b + aa * b1 - n / q_ * (c1 * d + c * d1);
        }
    }

    /**
     * Official {@code GFunctionFactory::GFunctionExactYield}.
     */
    public static class GFunctionExactYield extends GFunction {

        private final double delta_;
        private final List<Double> accruals_ = new ArrayList<>();

        public GFunctionExactYield(final CmsCoupon coupon) {
            SwapIndex swapIndex = coupon.swapIndex();
            VanillaSwap swap = swapIndex.underlyingSwap(coupon.fixingDate());

            Schedule schedule = swap.fixedSchedule();
            Handle<YieldTermStructure> rateCurve =
                    swapIndex.forwardingTermStructure();

            DayCounter dc = swapIndex.dayCounter();
            Date referenceDate = rateCurve.currentLink().referenceDate();

            double swapStartTime = dc.yearFraction(referenceDate,
                    schedule.startDate());
            double swapFirstPaymentTime = dc.yearFraction(referenceDate,
                    schedule.date(1));
            double paymentTime = dc.yearFraction(referenceDate, coupon.date());

            delta_ = (paymentTime - swapStartTime)
                    / (swapFirstPaymentTime - swapStartTime);

            for (CashFlow cashFlow : swap.fixedLeg()) {
                Coupon cpn = (Coupon) cashFlow;
                accruals_.add(cpn.accrualPeriod());
            }
        }

        @Override
        public double value(double x) {
            double product = 1.0;
            for (double accrual : accruals_)
                product *= 1.0 / (1.0 + accrual * x);
            return x * Math.pow(1.0 + accruals_.get(0) * x, -delta_)
                    * (1.0 / (1.0 - product));
        }

        @Override
        public double firstDerivative(double x) {
            double c = -1.0;
            double derC = 0.0;
            List<Double> b = new ArrayList<>(accruals_.size());
            for (double accrual : accruals_) {
                double temp = 1.0 / (1.0 + accrual * x);
                b.add(temp);
                c *= temp;
                derC += accrual * temp;
            }
            c += 1.0;
            c = 1.0 / c;
            derC *= (c - c * c);

            return -delta_ * accruals_.get(0) * Math.pow(b.get(0), delta_ + 1.0)
                    * x * c
                    + Math.pow(b.get(0), delta_) * c
                    + Math.pow(b.get(0), delta_) * x * derC;
        }

        @Override
        public double secondDerivative(double x) {
            double c = -1.0;
            double sum = 0.0;
            double sumOfSquare = 0.0;
            List<Double> b = new ArrayList<>(accruals_.size());
            for (double accrual : accruals_) {
                double temp = 1.0 / (1.0 + accrual * x);
                b.add(temp);
                c *= temp;
                sum += accrual * temp;
                sumOfSquare += (accrual * temp) * (accrual * temp);
            }
            c += 1.0;
            c = 1.0 / c;
            double derC = sum * (c - c * c);

            return (-delta_ * accruals_.get(0)
                    * Math.pow(b.get(0), delta_ + 1.0) * c
                    + Math.pow(b.get(0), delta_) * derC)
                    * (-delta_ * accruals_.get(0) * b.get(0) * x
                    + 1.0 + x * (1.0 - c) * sum)
                    + Math.pow(b.get(0), delta_) * c
                    * (delta_ * (accruals_.get(0) * b.get(0))
                    * (accruals_.get(0) * b.get(0)) * x
                    - delta_ * accruals_.get(0) * b.get(0)
                    - x * derC * sum + (1.0 - c) * sum
                    - x * (1.0 - c) * sumOfSquare);
        }
    }

    /**
     * Official {@code GFunctionFactory::GFunctionWithShifts}. The calibrated
     * shift is found with the official Newton setup.
     */
    public static class GFunctionWithShifts extends GFunction {

        private final Handle<Quote> meanReversion_;

        private final List<Double> shapedSwapPaymentTimes_ = new ArrayList<>();
        private final List<Double> accruals_ = new ArrayList<>();
        private final List<Double> swapPaymentDiscounts_ = new ArrayList<>();

        private double swapStartTime_;
        private double shapedPaymentTime_;
        private double discountAtStart_;
        private double discountRatio_;
        private double swapRateValue_;
        private double calibratedShift_ = 0.03;
        private double tmpRs_ = 1.0e7;
        private final double accuracy_ = 1.0e-14;

        private final ObjectiveFunction objectiveFunction_;

        public GFunctionWithShifts(final CmsCoupon coupon,
                                   Handle<Quote> meanReversion) {
            meanReversion_ = meanReversion == null
                    ? new Handle<Quote>(null, false) : meanReversion;

            SwapIndex swapIndex = coupon.swapIndex();
            VanillaSwap swap = swapIndex.underlyingSwap(coupon.fixingDate());

            swapRateValue_ = swap.fairRate();
            objectiveFunction_ = new ObjectiveFunction(swapRateValue_);

            Schedule schedule = swap.fixedSchedule();
            Handle<YieldTermStructure> rateCurve =
                    swapIndex.forwardingTermStructure();
            DayCounter dc = swapIndex.dayCounter();
            Date referenceDate = rateCurve.currentLink().referenceDate();

            swapStartTime_ = dc.yearFraction(referenceDate,
                    schedule.startDate());
            discountAtStart_ = rateCurve.currentLink().discount(
                    schedule.startDate());

            double paymentTime = dc.yearFraction(referenceDate, coupon.date());
            shapedPaymentTime_ = shapeOfShift(paymentTime);

            for (CashFlow cashFlow : swap.fixedLeg()) {
                Coupon cpn = (Coupon) cashFlow;
                accruals_.add(cpn.accrualPeriod());
                Date paymentDate = cpn.date();
                double swapPaymentTime = dc.yearFraction(referenceDate,
                        paymentDate);
                shapedSwapPaymentTimes_.add(shapeOfShift(swapPaymentTime));
                swapPaymentDiscounts_.add(
                        rateCurve.currentLink().discount(paymentDate));
            }
            discountRatio_ = swapPaymentDiscounts_.get(
                    swapPaymentDiscounts_.size() - 1) / discountAtStart_;
        }

        @Override
        public double value(double Rs) {
            double calibratedShift = calibrationOfShift(Rs);
            return Rs * functionZ(calibratedShift);
        }

        private double functionZ(double x) {
            return Math.exp(-shapedPaymentTime_ * x)
                    / (1.0 - discountRatio_
                    * Math.exp(-shapedSwapPaymentTimes_.get(
                    shapedSwapPaymentTimes_.size() - 1) * x));
        }

        private double derRsDerX(double x) {
            double sqrtDenominator = 0.0;
            double derSqrtDenominator = 0.0;
            for (int i = 0; i < accruals_.size(); ++i) {
                sqrtDenominator += accruals_.get(i) * swapPaymentDiscounts_.get(i)
                        * Math.exp(-shapedSwapPaymentTimes_.get(i) * x);
                derSqrtDenominator -= shapedSwapPaymentTimes_.get(i)
                        * accruals_.get(i) * swapPaymentDiscounts_.get(i)
                        * Math.exp(-shapedSwapPaymentTimes_.get(i) * x);
            }
            double denominator = sqrtDenominator * sqrtDenominator;

            int last = shapedSwapPaymentTimes_.size() - 1;
            double numerator = 0.0;
            numerator += shapedSwapPaymentTimes_.get(last)
                    * swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x)
                    * sqrtDenominator;
            numerator -= (discountAtStart_
                    - swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x))
                    * derSqrtDenominator;
            QL_REQUIRE(denominator != 0.0,
                    "GFunctionWithShifts::derRs_derX: denominator == 0");
            return numerator / denominator;
        }

        private double der2RsDerX2(double x) {
            double denOfRfunction = 0.0;
            double derDenOfRfunction = 0.0;
            double der2DenOfRfunction = 0.0;
            for (int i = 0; i < accruals_.size(); ++i) {
                denOfRfunction += accruals_.get(i) * swapPaymentDiscounts_.get(i)
                        * Math.exp(-shapedSwapPaymentTimes_.get(i) * x);
                derDenOfRfunction -= shapedSwapPaymentTimes_.get(i)
                        * accruals_.get(i) * swapPaymentDiscounts_.get(i)
                        * Math.exp(-shapedSwapPaymentTimes_.get(i) * x);
                der2DenOfRfunction += shapedSwapPaymentTimes_.get(i)
                        * shapedSwapPaymentTimes_.get(i) * accruals_.get(i)
                        * swapPaymentDiscounts_.get(i)
                        * Math.exp(-shapedSwapPaymentTimes_.get(i) * x);
            }

            double denominator = Math.pow(denOfRfunction, 4.0);

            int last = shapedSwapPaymentTimes_.size() - 1;
            double numOfDerR = 0.0;
            numOfDerR += shapedSwapPaymentTimes_.get(last)
                    * swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x)
                    * denOfRfunction;
            numOfDerR -= (discountAtStart_
                    - swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x))
                    * derDenOfRfunction;

            double denOfDerR = Math.pow(denOfRfunction, 2.0);

            double derNumOfDerR = 0.0;
            derNumOfDerR -= shapedSwapPaymentTimes_.get(last)
                    * shapedSwapPaymentTimes_.get(last)
                    * swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x)
                    * denOfRfunction;
            derNumOfDerR += shapedSwapPaymentTimes_.get(last)
                    * swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x)
                    * derDenOfRfunction;
            derNumOfDerR -= (shapedSwapPaymentTimes_.get(last)
                    * swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x))
                    * derDenOfRfunction;
            derNumOfDerR -= (discountAtStart_
                    - swapPaymentDiscounts_.get(last)
                    * Math.exp(-shapedSwapPaymentTimes_.get(last) * x))
                    * der2DenOfRfunction;

            double derDenOfDerR = 2.0 * denOfRfunction * derDenOfRfunction;

            double numerator = derNumOfDerR * denOfDerR
                    - numOfDerR * derDenOfDerR;
            QL_REQUIRE(denominator != 0.0,
                    "GFunctionWithShifts::der2Rs_derX2: denominator == 0");
            return numerator / denominator;
        }

        private double derZDerX(double x) {
            double last = shapedSwapPaymentTimes_.get(
                    shapedSwapPaymentTimes_.size() - 1);
            double sqrtDenominator = (1.0 - discountRatio_
                    * Math.exp(-last * x));
            double denominator = sqrtDenominator * sqrtDenominator;
            QL_REQUIRE(denominator != 0.0,
                    "GFunctionWithShifts::derZ_derX: denominator == 0");

            double numerator = 0.0;
            numerator -= shapedPaymentTime_
                    * Math.exp(-shapedPaymentTime_ * x) * sqrtDenominator;
            numerator -= last * Math.exp(-shapedPaymentTime_ * x)
                    * (1.0 - sqrtDenominator);

            return numerator / denominator;
        }

        private double der2ZDerX2(double x) {
            double last = shapedSwapPaymentTimes_.get(
                    shapedSwapPaymentTimes_.size() - 1);
            double denOfZfunction = (1.0 - discountRatio_
                    * Math.exp(-last * x));
            double derDenOfZfunction = last * discountRatio_
                    * Math.exp(-last * x);
            double denominator = Math.pow(denOfZfunction, 4.0);
            QL_REQUIRE(denominator != 0.0,
                    "GFunctionWithShifts::der2Z_derX2: denominator == 0");

            double numOfDerZ = 0.0;
            numOfDerZ -= shapedPaymentTime_
                    * Math.exp(-shapedPaymentTime_ * x) * denOfZfunction;
            numOfDerZ -= last * Math.exp(-shapedPaymentTime_ * x)
                    * (1.0 - denOfZfunction);

            double denOfDerZ = Math.pow(denOfZfunction, 2.0);
            double derNumOfDerZ = (-shapedPaymentTime_
                    * Math.exp(-shapedPaymentTime_ * x)
                    * (-shapedPaymentTime_
                    + (shapedPaymentTime_ * discountRatio_
                    - last * discountRatio_)
                    * Math.exp(-last * x))
                    - last * Math.exp(-shapedPaymentTime_ * x)
                    * (shapedPaymentTime_ * discountRatio_
                    - last * discountRatio_)
                    * Math.exp(-last * x));

            double derDenOfDerZ = 2.0 * denOfZfunction * derDenOfZfunction;
            double numerator = derNumOfDerZ * denOfDerZ
                    - numOfDerZ * derDenOfDerZ;

            return numerator / denominator;
        }

        @Override
        public double firstDerivative(double Rs) {
            double calibratedShift = calibrationOfShift(Rs);
            return functionZ(calibratedShift)
                    + Rs * derZDerX(calibratedShift) / derRsDerX(calibratedShift);
        }

        @Override
        public double secondDerivative(double Rs) {
            double calibratedShift = calibrationOfShift(Rs);
            return 2.0 * derZDerX(calibratedShift) / derRsDerX(calibratedShift)
                    + Rs * der2ZDerX2(calibratedShift)
                    / Math.pow(derRsDerX(calibratedShift), 2.0)
                    - Rs * derZDerX(calibratedShift)
                    * der2RsDerX2(calibratedShift)
                    / Math.pow(derRsDerX(calibratedShift), 3.0);
        }

        private double shapeOfShift(double s) {
            double x = s - swapStartTime_;
            double meanReversion = meanReversion_.currentLink().value();
            if (meanReversion > 0.0)
                return (1.0 - Math.exp(-meanReversion * x)) / meanReversion;
            return x;
        }

        private double calibrationOfShift(double Rs) {
            if (Rs != tmpRs_) {
                double initialGuess;
                double n = 0.0;
                double d = 0.0;
                for (int i = 0; i < accruals_.size(); ++i) {
                    n += accruals_.get(i) * swapPaymentDiscounts_.get(i);
                    d += accruals_.get(i) * swapPaymentDiscounts_.get(i)
                            * shapedSwapPaymentTimes_.get(i);
                }
                int last = accruals_.size() - 1;
                n *= Rs;
                d *= Rs;
                n += accruals_.get(last) * swapPaymentDiscounts_.get(last)
                        - discountAtStart_;
                d += accruals_.get(last) * swapPaymentDiscounts_.get(last)
                        * shapedSwapPaymentTimes_.get(last);
                initialGuess = n / d;

                objectiveFunction_.setSwapRateValue(Rs);
                Newton solver = new Newton();
                solver.setMaxEvaluations(1000);

                final double lower = -20.0;
                final double upper = 20.0;

                try {
                    calibratedShift_ = solver.solve(objectiveFunction_,
                            accuracy_,
                            Math.max(Math.min(initialGuess, upper * 0.99),
                                    lower * 0.99),
                            lower, upper);
                } catch (RuntimeException e) {
                    QL_FAIL("meanReversion: "
                            + meanReversion_.currentLink().value()
                            + ", swapRateValue: " + swapRateValue_
                            + ", swapStartTime: " + swapStartTime_
                            + ", shapedPaymentTime: " + shapedPaymentTime_
                            + "\n error message: " + e.getMessage());
                }
                tmpRs_ = Rs;
            }
            return calibratedShift_;
        }

        private class ObjectiveFunction implements Function {
            private double Rs_;
            private double derivative_;

            ObjectiveFunction(double Rs) {
                Rs_ = Rs;
            }

            @Override
            public double value(double x) {
                double result = 0.0;
                derivative_ = 0.0;
                for (int i = 0; i < accruals_.size(); ++i) {
                    double temp = accruals_.get(i) * swapPaymentDiscounts_.get(i)
                            * Math.exp(-shapedSwapPaymentTimes_.get(i) * x);
                    result += temp;
                    derivative_ -= shapedSwapPaymentTimes_.get(i) * temp;
                }
                result *= Rs_;
                derivative_ *= Rs_;
                int last = swapPaymentDiscounts_.size() - 1;
                double temp = swapPaymentDiscounts_.get(last)
                        * Math.exp(-shapedSwapPaymentTimes_.get(last) * x);

                result += temp - discountAtStart_;
                derivative_ -= shapedSwapPaymentTimes_.get(last) * temp;
                return result;
            }

            @Override
            public double derivative(double x) {
                return derivative_;
            }

            void setSwapRateValue(double x) {
                Rs_ = x;
            }
        }
    }
}
