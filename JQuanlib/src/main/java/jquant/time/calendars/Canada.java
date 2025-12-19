package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.CanadaSettlementImpl;
import jquant.time.calendars.impl.CanadaTsxImpl;

import static jquant.math.CommonUtil.QL_FAIL;

public class Canada extends Calendar {
    public enum Market { Settlement,       //!< generic settlement calendar
        TSX               //!< Toronto stock exchange calendar
    }
    public Canada(Market market) {
        switch (market) {
            case Settlement:
                impl_ = new CanadaSettlementImpl();
                break;
            case TSX:
                impl_ = new CanadaTsxImpl();
                break;
            default:
                QL_FAIL("unknown market");
        }
    }
    public Canada() {
        this(Market.Settlement);
    }
}
