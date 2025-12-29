package jquant;

import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;
import org.apache.commons.math3.util.FastMath;

import static jquant.Compounding.*;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Concrete interest rate class
/*! This class encapsulate the interest rate compounding algebra.
    It manages day-counting conventions, compounding conventions,
    conversion between different conventions, discount/compound factor
    calculations, and implied/equivalent rate calculations.

    \test Converted rates are checked against known good results
*/
public class InterestRate {
    private double r_;
    private DayCounter dc_;
    private Compounding comp_;
    private boolean freqMakesSense_;
    private double freq_;

    //! \name constructors
    //@{
    //! Default constructor returning a null interest rate.
    public InterestRate() {
        r_ = Double.NaN;
    }

    //! Standard constructor
    public InterestRate(double r, DayCounter dc, Compounding comp, Frequency freq) {
        r_ = r;
        dc_ = dc;
        comp_ = comp;
        freqMakesSense_ = false;
        if (comp_ == Compounded || comp_ == SimpleThenCompounded || comp_ == CompoundedThenSimple) {
            freqMakesSense_ = true;
            QL_REQUIRE(freq != Frequency.ONCE && freq != Frequency.NO_FREQUENCY,
                    "frequency not allowed for this interest rate");
            freq_ = freq.getValue();
        }
    }

    //@}
    //! \name conversions
    //@{
    public double rate() {
        return r_;
    }

    public final DayCounter dayCounter() {
        return dc_;
    }

    public Compounding compounding() {
        return comp_;
    }

    public Frequency frequency() {
        return freqMakesSense_ ? Frequency.fromValue((int) freq_) : Frequency.NO_FREQUENCY;
    }
    //@}

    //! \name discount/compound factor calculations
    //@{
    //! discount factor implied by the rate compounded at time t.
    /*! \warning Time must be measured using InterestRate's own
                 day counter.
    */
    public double discountFactor(double t) {
        return 1.0 / compoundFactor(t);
    }

    //! discount factor implied by the rate compounded between two dates
    public double discountFactor(final Date d1,
                                 final Date d2,
                                 final Date refStart,
                                 final Date refEnd) {
        QL_REQUIRE(TimeUtils.geq(d2, d1),
                "d1 (" + d1 + ") " +
                        "later than d2 (" + d2 + ")");
        double t = dc_.yearFraction(d1, d2, refStart, refEnd);
        return discountFactor(t);
    }

    //! compound factor implied by the rate compounded at time t.
    /*! returns the compound (a.k.a capitalization) factor
        implied by the rate compounded at time t.

        \warning Time must be measured using InterestRate's own
                 day counter.
    */
    public double compoundFactor(double t) {
        QL_REQUIRE(t >= 0.0, "negative time (" + t + ") not allowed");
        QL_REQUIRE(!Double.isNaN(r_), "null interest rate");
        switch (comp_) {
            case Simple:
                return 1.0 + r_ * t;
            case Compounded:
                return FastMath.pow(1.0 + r_ / freq_, freq_ * t);
            case Continuous:
                return FastMath.exp(r_ * t);
            case SimpleThenCompounded:
                if (t <= 1.0 / (freq_))
                    return 1.0 + r_ * t;
                else
                    return FastMath.pow(1.0 + r_ / freq_, freq_ * t);
            case CompoundedThenSimple:
                if (t > 1.0 / (freq_))
                    return 1.0 + r_ * t;
                else
                    return FastMath.pow(1.0 + r_ / freq_, freq_ * t);
            default:
                QL_FAIL("unknown compounding convention");
        }
        return Double.NaN;
    }

    //! compound factor implied by the rate compounded between two dates
    /*! returns the compound (a.k.a capitalization) factor
        implied by the rate compounded between two dates.
    */
    public double compoundFactor(final Date d1,
                                 final Date d2,
                                 final Date refStart,
                                 final Date refEnd) {
        QL_REQUIRE(TimeUtils.geq(d2, d1),
                "d1 (" + d1 + ") " +
                        "later than d2 (" + d2 + ")");
        double t = dc_.yearFraction(d1, d2, refStart, refEnd);
        return compoundFactor(t);
    }
    //@}

    //! \name implied rate calculations
    //@{

    //! implied interest rate for a given compound factor at a given time.
    /*! The resulting InterestRate has the day-counter provided as input.

        \warning Time must be measured using the day-counter provided
                 as input.
    */
    public static InterestRate impliedRate(double compound,
                                           final DayCounter resultDC,
                                           Compounding comp,
                                           Frequency freq,
                                           double t) {

        QL_REQUIRE(compound > 0.0, "positive compound factor required");

        double r = Double.NaN;
        if (compound == 1.0) {
            QL_REQUIRE(t >= 0.0, "non negative time (" + t + ") required");
            r = 0.0;
        } else {
            QL_REQUIRE(t > 0.0, "positive time (" + t + ") required");
            switch (comp) {
                case Simple:
                    r = (compound - 1.0) / t;
                    break;
                case Compounded:
                    r = (FastMath.pow(compound, 1.0 / ((freq.getValue()) * t)) - 1.0) * (freq.getValue());
                    break;
                case Continuous:
                    r = FastMath.log(compound) / t;
                    break;
                case SimpleThenCompounded:
                    if (t <= 1.0 / (freq.getValue()))
                        r = (compound - 1.0) / t;
                    else
                        r = (FastMath.pow(compound, 1.0 / ((freq.getValue()) * t)) - 1.0) * (freq.getValue());
                    break;
                case CompoundedThenSimple:
                    if (t > 1.0 / (freq.getValue()))
                        r = (compound - 1.0) / t;
                    else
                        r = (FastMath.pow(compound, 1.0 / ((freq.getValue()) * t)) - 1.0) * (freq.getValue());
                    break;
                default:
                    QL_FAIL("unknown compounding convention (" + (comp).getIndex() + ")");
            }
        }
        return new InterestRate(r, resultDC, comp, freq);
    }

    //! implied rate for a given compound factor between two dates.
    /*! The resulting rate is calculated taking the required
        day-counting rule into account.
    */
    public static InterestRate impliedRate(double compound,
                                           final DayCounter resultDC,
                                           Compounding comp,
                                           Frequency freq,
                                           final Date d1,
                                           final Date d2,
                                           final Date refStart,
                                           final Date refEnd) {
        QL_REQUIRE(TimeUtils.geq(d2, d1),
                "d1 (" + d1 + ") " +
                        "later than d2 (" + d2 + ")");
        double t = resultDC.yearFraction(d1, d2, refStart, refEnd);
        return impliedRate(compound, resultDC, comp, freq, t);
    }
    //@}

    //! \name equivalent rate calculations
    //@{

    //! equivalent interest rate for a compounding period t.
    /*! The resulting InterestRate shares the same implicit
        day-counting rule of the original InterestRate instance.

        \warning Time must be measured using the InterestRate's
                 own day counter.
    */
    public InterestRate equivalentRate(Compounding comp,
                                       Frequency freq,
                                       double t) {
        return impliedRate(compoundFactor(t), dc_, comp, freq, t);
    }

    //! equivalent rate for a compounding period between two dates
    /*! The resulting rate is calculated taking the required
        day-counting rule into account.
    */
    public InterestRate equivalentRate(final DayCounter resultDC,
                                       Compounding comp,
                                       Frequency freq,
                                       Date d1,
                                       Date d2,
                                       final Date refStart,
                                       final Date refEnd) {
        QL_REQUIRE(TimeUtils.geq(d2, d1),
                "d1 (" + d1 + ") " +
                        "later than d2 (" + d2 + ")");
        double t1 = dc_.yearFraction(d1, d2, refStart, refEnd);
        double t2 = resultDC.yearFraction(d1, d2, refStart, refEnd);
        return impliedRate(compoundFactor(t1), resultDC, comp, freq, t2);
    }
    //@}

    @Override
    public String toString() {
        return "InterestRate{" +
                "r_=" + r_ + '}';
    }
}
