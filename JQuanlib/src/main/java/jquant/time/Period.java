package jquant.time;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/*! This class provides a Period (length + TimeUnit) class
    and implements a limited algebra.

    \ingroup datetime

    \test self-consistency of algebra is checked.
*/
public class Period {
    private int length_ = 0;
    private TimeUnit units_ = TimeUnit.DAYS;
    public Period() {
        length_ = 0;
        units_ = TimeUnit.DAYS;
    }
    public Period(int n, TimeUnit u) {
        length_ = n;
        units_ = u;
    }

    public Period(Frequency f) {
        switch (f) {
            case NO_FREQUENCY:
                // same as Period()
                units_ = TimeUnit.DAYS;
                length_ = 0;
                break;
            case ONCE:
                units_ = TimeUnit.YEARS;
                length_ = 0;
                break;
            case ANNUAL:
                units_ = TimeUnit.YEARS;
                length_ = 1;
                break;
            case SEMIANNUAL:
            case EVERY_FOURTH_MONTH:
            case QUARTERLY:
            case BIMONTHLY:
            case MONTHLY:
                units_ = TimeUnit.MONTHS;
                length_ = 12/f.getValue();
                break;
            case EVERY_FOURTH_WEEK:
            case BIWEEKLY:
            case WEEKLY:
                units_ = TimeUnit.WEEKS;
                length_ = 52/f.getValue();
                break;
            case DAILY:
                units_ = TimeUnit.DAYS;
                length_ = 1;
                break;
            case OTHER_FREQUENCY:
                QL_FAIL("unknown frequency");  // no point in showing 999...
            default:
                QL_FAIL("unknown frequency (" + f.getValue() + ")");
        }
    }

    public Period clone() {
        return new Period(length_, units_);
    }

    public int length() {
        return length_;
    }

    public TimeUnit units() {
        return units_;
    }

    public Frequency frequency() {
        // unsigned version
        int length = Math.abs(length_);

        if (length==0) {
            if (units_==TimeUnit.YEARS) return Frequency.ONCE;
            return Frequency.NO_FREQUENCY;
        }

        switch (units_) {
            case YEARS:
                if (length == 1)
                    return Frequency.ANNUAL;
                else
                    return Frequency.OTHER_FREQUENCY;
            case MONTHS:
                if (12%length == 0 && length <= 12)
                    return Frequency.fromValue(12/length);
                else
                    return Frequency.OTHER_FREQUENCY;
            case WEEKS:
                if (length==1)
                    return Frequency.WEEKLY;
                else if (length==2)
                    return Frequency.BIWEEKLY;
                else if (length==4)
                    return Frequency.EVERY_FOURTH_WEEK;
                else
                    return Frequency.OTHER_FREQUENCY;
            case DAYS:
                if (length==1)
                    return Frequency.DAILY;
                else
                    return Frequency.OTHER_FREQUENCY;
            default:
                QL_FAIL("unknown time unit (" + units_.toString() + ")");
        }
        return Frequency.OTHER_FREQUENCY;
    }

    public Period addEquals(Period p) {
        if (length_==0) {
            length_ = p.length();
            units_ = p.units();
        } else if (units_==p.units()) {
            // no conversion needed
            length_ += p.length();
        } else {
            switch (units_) {

                case YEARS:
                    switch (p.units()) {
                        case MONTHS:
                            units_ = TimeUnit.MONTHS;
                            length_ = length_*12 + p.length();
                            break;
                        case WEEKS:
                        case DAYS:
                            QL_REQUIRE(p.length()==0,
                                    "impossible addition between " + this +
                                            " and " + p);
                            break;
                        default:
                            QL_FAIL("unknown time unit (" + p.units() + ")");
                    }
                    break;

                case MONTHS:
                    switch (p.units()) {
                        case YEARS:
                            length_ += p.length()*12;
                            break;
                        case WEEKS:
                        case DAYS:
                            QL_REQUIRE(p.length()==0,
                                    "impossible addition between " + this +
                                            " and " + p);
                            break;
                        default:
                            QL_FAIL("unknown time unit (" + p.units() + ")");
                    }
                    break;

                case WEEKS:
                    switch (p.units()) {
                        case DAYS:
                            units_ = TimeUnit.DAYS;
                            length_ = length_*7 + p.length();
                            break;
                        case YEARS:
                        case MONTHS:
                            QL_REQUIRE(p.length()==0,
                                    "impossible addition between " + this +
                                            " and " + p);
                            break;
                        default:
                            QL_FAIL("unknown time unit (" + p.units() + ")");
                    }
                    break;

                case DAYS:
                    switch (p.units()) {
                        case WEEKS:
                            length_ += p.length()*7;
                            break;
                        case YEARS:
                        case MONTHS:
                            QL_REQUIRE(p.length()==0,
                                    "impossible addition between " + this +
                                            " and " + p);
                            break;
                        default:
                            QL_FAIL("unknown time unit (" + p.units() + ")");
                    }
                    break;

                default:
                    QL_FAIL("unknown time unit (" +  units_ + ")");
            }
        }

        return this;
    }

    public Period subtractEquals(Period p) {
        return addEquals(TimeUtils.negetive(p));
    }

    public Period multiplyEquals(int n) {
        length_ *= n;
        return this;
    }

    public Period divideEquals(int n) {
        QL_REQUIRE(n != 0, "cannot be divided by zero");
        if (length_ % n == 0) {
            // keep the original units. If the user created a
            // 24-months period, he'll probably want a 12-months one
            // when he halves it.
            length_ /= n;
        } else {
            // try
            TimeUnit units = units_;
            int length = length_;
            switch (units) {
                case YEARS:
                    length *= 12;
                    units = TimeUnit.MONTHS;
                    break;
                case WEEKS:
                    length *= 7;
                    units = TimeUnit.DAYS;
                    break;
                default:
                    ;
            }
            QL_REQUIRE(length % n == 0, this + " cannot be divided by " + n);
            length_ = length/n;
            units_ = units;
        }
        return this;
    }

    public void normalize() {
        if (length_ == 0) {
            units_ = TimeUnit.DAYS;
        } else {
            switch (units_) {
                case MONTHS:
                    if ((length_ % 12) == 0) {
                        length_ /= 12;
                        units_ = TimeUnit.YEARS;
                    }
                    break;
                case DAYS:
                    if ((length_ % 7) == 0) {
                        length_ /= 7;
                        units_ = TimeUnit.WEEKS;
                    }
                    break;
                case WEEKS:
                case YEARS:
                    break;
                default:
                    QL_FAIL("unknown time unit (" + units_ + ")");
            }
        }
    }

    public Period normalized() {
        Period p = this;
        p.normalize();
        return p;
    }

    @Override
    public String toString() {
        return "Period{" +
                "length_=" + length_ +
                ", units_=" + units_ +
                '}';
    }
}
