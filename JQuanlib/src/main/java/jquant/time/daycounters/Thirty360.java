package jquant.time.daycounters;

import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.impl.*;

import static jquant.math.CommonUtil.QL_FAIL;

//! 30/360 day count convention
/*! The 30/360 day count can be calculated according to a
    number of conventions.

    US convention: if the starting date is the 31st of a month or
    the last day of February, it becomes equal to the 30th of the
    same month.  If the ending date is the 31st of a month and the
    starting date is the 30th or 31th of a month, the ending date
    becomes equal to the 30th.  If the ending date is the last of
    February and the starting date is also the last of February,
    the ending date becomes equal to the 30th.
    Also known as "30/360" or "360/360".

    Bond Basis convention: if the starting date is the 31st of a
    month, it becomes equal to the 30th of the same month.
    If the ending date is the 31st of a month and the starting
    date is the 30th or 31th of a month, the ending date
    also becomes equal to the 30th of the month.
    Also known as "US (ISMA)".

    European convention: starting dates or ending dates that
    occur on the 31st of a month become equal to the 30th of the
    same month.
    Also known as "30E/360", or "Eurobond Basis".

    Italian convention: starting dates or ending dates that
    occur on February and are greater than 27 become equal to 30
    for computational sake.

    ISDA convention: starting or ending dates on the 31st of the
    month become equal to 30; starting dates or ending dates that
    occur on the last day of February also become equal to 30,
    except for the termination date.  Also known as "30E/360
    ISDA", "30/360 ISDA", or "30/360 German".

    NASD convention: if the starting date is the 31st of a
    month, it becomes equal to the 30th of the same month.
    If the ending date is the 31st of a month and the starting
    date is earlier than the 30th of a month, the ending date
    becomes equal to the 1st of the next month, otherwise the
    ending date becomes equal to the 30th of the same month.

    \ingroup daycounters
*/
public class Thirty360 extends DayCounter {
    public enum Convention {
        USA,
        BondBasis,
        European,
        EurobondBasis,
        Italian,
        German,
        ISMA,
        ISDA,
        NASD
    }

    public Thirty360(Convention c, Date terminationDate) {
        super(implementation(c, terminationDate));
    }

    public Thirty360(Convention c) {
        this(c, new Date());
    }

    private static DayCounterImpl implementation(Convention c, final Date terminationDate) {
        switch (c) {
            case USA:
                return new Thirty360USImpl();
            case European:
            case EurobondBasis:
                return new Thirty360EUImpl();
            case Italian:
                return new Thirty360ITImpl();
            case ISMA:
            case BondBasis:
                return new Thirty360ISMAImpl();
            case ISDA:
            case German:
                return new Thirty360ISDAImpl(terminationDate);
            case NASD:
                return new Thirty360NASDImpl();
            default:
                QL_FAIL("unknown 30/360 convention");
        }
        return new Thirty360USImpl();
    }
}
