package jquant.math;

import org.apache.commons.math3.util.FastMath;

import static jquant.math.CommonUtil.QL_FAIL;

//! basic rounding class
/*! \test the correctness of the returned values is tested by
          checking them against known good results.
*/
public class Rounding {
    //! rounding methods
    /*! The rounding methods follow the OMG specification available
        at <http://www.omg.org/cgi-bin/doc?formal/00-06-29.pdf>.

        \warning the names of the Floor and Ceiling methods might
                 be misleading. Check the provided reference.
    */
    public enum Type {
        None, /*!< do not round: return the number unmodified */
        Up, /*!< the first decimal place past the precision will be
                          rounded up. This differs from the OMG rule which
                          rounds up only if the decimal to be rounded is
                          greater than or equal to the rounding digit */
        Down, /*!< all decimal places past the precision will be
                          truncated */
        Closest, /*!< the first decimal place past the precision
                          will be rounded up if greater than or equal
                          to the rounding digit; this corresponds to
                          the OMG round-up rule.  When the rounding
                          digit is 5, the result will be the one
                          closest to the original number, hence the
                          name. */
        Floor, /*!< positive numbers will be rounded up and negative
                          numbers will be rounded down using the OMG round up
                          and round down rules */
        Ceiling /*!< positive numbers will be rounded down and negative
                          numbers will be rounded up using the OMG round up
                          and round down rules */
    }
    private int precision_;
    private Type type_;
    private int digit_;

    public Rounding() {
        type_ = Type.None;
    }

    public Rounding(int precision, Type type, int digit) {
        precision_ = precision;
        type_ = type;
        digit_ = digit;
    }

    public double value(double value) {
        if (type_ == Type.None)
            return value;

        double mult = Math.pow(10.0,precision_);
        boolean neg = (value < 0.0);
        double lvalue = Math.abs(value)*mult;
        double integral = (long)lvalue;
        double modVal = lvalue - integral;

        lvalue -= modVal;
        switch (type_) {
            case Down:
                break;
            case Up:
                if (modVal != 0.0)
                    lvalue += 1.0;
                break;
            case Closest:
                if (modVal >= (digit_/10.0))
                    lvalue += 1.0;
                break;
            case Floor:
                if (!neg) {
                    if (modVal >= (digit_/10.0))
                        lvalue += 1.0;
                }
                break;
            case Ceiling:
                if (neg) {
                    if (modVal >= (digit_/10.0))
                        lvalue += 1.0;
                }
                break;
            default:
                QL_FAIL("unknown rounding method");
        }
        return (neg) ? (-(lvalue / mult)) : (lvalue / mult);
    }

    public int precision() {
        return precision_;
    }

    public Type type() {
        return type_;
    }

    public int roundingDigit() {
        return digit_;
    }
}
