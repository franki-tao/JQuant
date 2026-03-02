package jquant.indexes;

import jquant.Currency;
import jquant.Index;
import jquant.Settings;
import jquant.time.*;

import static jquant.math.CommonUtil.QL_REQUIRE;


//! base class for interest rate indexes
/*! \todo add methods returning InterestRate */
public abstract class InterestRateIndex extends Index {
    private Calendar fixingCalendar_;
    protected String familyName_;
    protected Period tenor_;
    protected int fixingDays_;
    protected Currency currency_;
    protected DayCounter dayCounter_;
    protected String name_;

    public InterestRateIndex(String familyName,
                             final Period tenor,
                             int fixingDays,
                             Currency currency,
                             Calendar fixingCalendar,
                             DayCounter dayCounter) {
        familyName_ = familyName;
        tenor_ = tenor;
        fixingDays_ = fixingDays;
        currency_ = currency;
        dayCounter_ = dayCounter;
        fixingCalendar_ = fixingCalendar;
        // tenor_.normalize() does too much; we want to leave days alone
        if (tenor.units() == TimeUnit.MONTHS && tenor.length() % 12 == 0)
            tenor_ = new Period(tenor.length() / 12, TimeUnit.YEARS);
        StringBuilder sb = new StringBuilder(familyName_);
        if (TimeUtils.equals(tenor_, TimeUtils.multiply(1, TimeUnit.DAYS))) {
            if (fixingDays_ == 0)
                sb.append("ON");
            else if (fixingDays_ == 1)
                sb.append("TN");
            else if (fixingDays_ == 2)
                sb.append("SN");
            else
                sb.append(tenor_);
        } else {
            sb.append(tenor_);
        }
        sb.append(dayCounter_.name());
        name_ = sb.toString();
        registerWith(Settings.instance.evaluationDate());
        registerWith(notifier());
    }

    @Override
    public String name() {
        return name_;
    }

    @Override
    public Calendar fixingCalendar() {
        return fixingCalendar_;
    }

    @Override
    public boolean isValidFixingDate(final Date fixingDate) {
        return fixingCalendar_.isBusinessDay(fixingDate);
    }

    @Override
    public double fixing(final Date fixingDate, boolean forecastTodaysFixing) {
        QL_REQUIRE(isValidFixingDate(fixingDate),
                "Fixing date " + fixingDate + " is not valid");

        Date today = Settings.instance.evaluationDate().Date();

        if (TimeUtils.greater(fixingDate, today) ||
                (TimeUtils.equals(fixingDate, today) && forecastTodaysFixing))
            return forecastFixing(fixingDate);

        if (TimeUtils.less(fixingDate, today) ||
                Settings.instance.enforcesTodaysHistoricFixings()) {
            // must have been fixed
            // do not catch exceptions
            double result = pastFixing(fixingDate);
            QL_REQUIRE(!Double.isNaN(result),
                    "Missing " + name() + " fixing for " + fixingDate);
            return result;
        }

        try {
            // might have been fixed
            double result = pastFixing(fixingDate);
            if (!Double.isNaN(result))
                return result;
            else
                ;   // fall through and forecast
        } catch (Exception e) {
            ;   // fall through and forecast
        }
        return forecastFixing(fixingDate);
    }

    public String familyName() {
        return familyName_;
    }

    public Period tenor() {
        return tenor_;
    }

    public int fixingDays() {
        return fixingDays_;
    }

    public final Currency currency() {
        return currency_;
    }

    public final DayCounter dayCounter() {
        return dayCounter_;
    }

    public Date fixingDate(final Date valueDate) {
        return fixingCalendar().advance(valueDate,
                -fixingDays_, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
    }
    public Date valueDate(final Date fixingDate) {
        QL_REQUIRE(isValidFixingDate(fixingDate),
                fixingDate + " is not a valid fixing date");
        return fixingCalendar().advance(fixingDate, fixingDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
    }
    public abstract Date maturityDate(final Date valueDate);

    //! It can be overridden to implement particular conventions
    public abstract double forecastFixing(final Date fixingDate);
}
