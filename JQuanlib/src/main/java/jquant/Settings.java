package jquant;

import jquant.patterns.Singleton;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.Optional;

public class Settings implements Singleton<Settings> {
    private DateProxy evaluationDate_;
    private boolean includeReferenceDateEvents_ = false;
    private Optional<Boolean> includeTodaysCashFlows_;
    private Boolean enforcesTodaysHistoricFixings_ = false;
    public static Settings instance = new  Settings();
    private Settings() {}

    //! the date at which pricing is to be performed.
    /*! Client code can inspect the evaluation date, as in:
        \code
        Date d = Settings::instance().evaluationDate();
        \endcode
        where today's date is returned if the evaluation date is
        set to the null date (its default value;) can set it to a
        new value, as in:
        \code
        Settings::instance().evaluationDate() = d;
        \endcode
        and can register with it, as in:
        \code
        registerWith(Settings::instance().evaluationDate());
        \endcode
        to be notified when it is set to a new value.
        \warning a notification is not sent when the evaluation
                 date changes for natural causes---i.e., a date
                 was not explicitly set (which results in today's
                 date being used for pricing) and the current date
                 changes as the clock strikes midnight.
    */
    public DateProxy evaluationDate() {
        return evaluationDate_;
    }
    /*! Call this to prevent the evaluation date to change at
        midnight (and, incidentally, to gain quite a bit of
        performance.)  If no evaluation date was previously set,
        it is equivalent to setting the evaluation date to
        Date::todaysDate(); if an evaluation date other than
        Date() was already set, it has no effect.
    */
    public void anchorEvaluationDate() {
        // set to today's date if not already set.
        if (TimeUtils.equals(evaluationDate_.getValue() ,new Date()))
            evaluationDate_.equal(Date.todaysDate());
        // If set, no-op since the date is already anchored.
    }
    /*! Call this to reset the evaluation date to
        Date::todaysDate() and allow it to change at midnight.  It
        is equivalent to setting the evaluation date to Date().
        This comes at the price of losing some performance, since
        the evaluation date is re-evaluated each time it is read.
    */
    public void resetEvaluationDate() {
        evaluationDate_.equal(new Date());
    }
    /*! This flag specifies whether or not Events occurring on the reference
        date should, by default, be taken into account as not happened yet.
        It can be overridden locally when calling the Event::hasOccurred
        method.
    */
    // 支持对返回值修改
    public boolean includeReferenceDateEvents() {
        return includeReferenceDateEvents_;
    }
    public void setIncludeReferenceDateEvents(boolean includeReferenceDateEvents) {
        this.includeReferenceDateEvents_ = includeReferenceDateEvents;
    }
    /*! If set, this flag specifies whether or not CashFlows
        occurring on today's date should enter the NPV.  When the
        NPV date (i.e., the date at which the cash flows are
        discounted) equals today's date, this flag overrides the
        behavior chosen for includeReferenceDate. It cannot be overridden
        locally when calling the CashFlow::hasOccurred method.
    */
    public Optional<Boolean> includeTodaysCashFlows() {
        return includeTodaysCashFlows_;
    }

    public boolean enforcesTodaysHistoricFixings() {
        return enforcesTodaysHistoricFixings_;
    }
    public void setEnforcesTodaysHistoricFixings(boolean todaysCashFlows) {
        this.enforcesTodaysHistoricFixings_ = todaysCashFlows;
    }
}
