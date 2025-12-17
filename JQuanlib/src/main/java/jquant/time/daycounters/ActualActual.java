package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.impl.*;

import static jquant.math.CommonUtil.QL_FAIL;

//! Actual/Actual day count
/*! The day count can be calculated according to:

    - the ISDA convention, also known as "Actual/Actual (Historical)",
      "Actual/Actual", "Act/Act", and according to ISDA also "Actual/365",
      "Act/365", and "A/365";
    - the ISMA and US Treasury convention, also known as
      "Actual/Actual (Bond)";
    - the AFB convention, also known as "Actual/Actual (Euro)".

    For more details, refer to
    https://www.isda.org/a/pIJEE/The-Actual-Actual-Day-Count-Fraction-1999.pdf

    \ingroup daycounters

    \test the correctness of the results is checked against known
          good values.
*/
public class ActualActual extends DayCounter {
    public enum Convention {
        ISMA, Bond,
        ISDA, Historical, Actual365,
        AFB, Euro
    }

    public ActualActual(Convention c, Schedule s) {
        super(implementation(c, s));
    }

    public ActualActual(Convention c) {
        this(c, new Schedule());
    }

    private static DayCounterImpl implementation(Convention c, Schedule schedule) {
        switch (c) {
            case ISMA:
            case Bond:
                if (!schedule.empty())
                    return new ActualActualISMAImpl(schedule);
                else
                    return new ActualActualOldISMAImpl();
            case ISDA:
            case Historical:
            case Actual365:
                return new ActualActualISDAImpl();
            case AFB:
            case Euro:
                return new ActualActualAFBImpl();
            default:
                QL_FAIL("unknown act/act convention");
        }
        return new ActualActualISDAImpl();
    }
}
