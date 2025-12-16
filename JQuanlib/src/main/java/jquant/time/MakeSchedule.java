package jquant.time;

import jquant.time.calendars.NullCalendar;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! helper class
/*! This class provides a more comfortable interface to the
    argument list of Schedule's constructor.
*/
public class MakeSchedule {
    private Calendar calendar_;
    private Date effectiveDate_, terminationDate_;
    private Optional<Period> tenor_;
    private Optional<BusinessDayConvention> convention_;
    private Optional<BusinessDayConvention> terminationDateConvention_;
    private DateGenerationRule rule_ = DateGenerationRule.BACKWARD;
    private boolean endOfMonth_ = false;
    private Date firstDate_, nextToLastDate_;

    public MakeSchedule() {
        effectiveDate_ = new Date();
        terminationDate_ = new Date();
        tenor_ = Optional.empty();
        convention_ = Optional.empty();
        terminationDateConvention_ = Optional.empty();
        rule_ = DateGenerationRule.BACKWARD;
        endOfMonth_ = false;
        firstDate_ = new Date();
        nextToLastDate_ = new Date();
    }

    public MakeSchedule from(final Date effectiveDate) {
        effectiveDate_ = effectiveDate;
        return this;
    }

    public MakeSchedule to(final Date terminationDate) {
        terminationDate_ = terminationDate;
        return this;
    }

    public MakeSchedule withTenor(final Period tenor) {
        tenor_ = Optional.of(tenor);
        return this;
    }

    public MakeSchedule withFrequency(Frequency frequency) {
        tenor_ = Optional.of(new Period(frequency));
        return this;
    }

    public MakeSchedule withCalendar(final Calendar calendar) {
        calendar_ = calendar;
        return this;
    }

    public MakeSchedule withConvention(BusinessDayConvention conv) {
        convention_ = Optional.of(conv);
        return this;
    }

    public MakeSchedule withTerminationDateConvention(
            BusinessDayConvention conv) {
        terminationDateConvention_ = Optional.of(conv);
        return this;
    }

    public MakeSchedule withRule(DateGenerationRule r) {
        rule_ = r;
        return this;
    }

    public MakeSchedule forwards() {
        rule_ = DateGenerationRule.FORWARD;
        return this;
    }

    public MakeSchedule backwards() {
        rule_ = DateGenerationRule.BACKWARD;
        return this;
    }

    public MakeSchedule endOfMonth(boolean flag) {
        endOfMonth_ = flag;
        return this;
    }

    public MakeSchedule withFirstDate(final Date d) {
        firstDate_ = d;
        return this;
    }

    public MakeSchedule withNextToLastDate(final Date d) {
        nextToLastDate_ = d;
        return this;
    }

    public Schedule toSchedule() {
        // check for mandatory arguments
        QL_REQUIRE(TimeUtils.neq(effectiveDate_, new Date()), "effective date not provided");
        QL_REQUIRE(TimeUtils.neq(terminationDate_, new Date()), "termination date not provided");
        QL_REQUIRE(tenor_.isPresent(), "tenor/frequency not provided");

        // set dynamic defaults:
        BusinessDayConvention convention;
        // if a convention was set, we use it.
        if (convention_.isPresent()) { // NOLINT(readability-implicit-bool-conversion)
            convention = convention_.get();
        } else {
            if (!calendar_.empty()) {
                // ...if we set a calendar, we probably want it to be used;
                convention = BusinessDayConvention.FOLLOWING;
            } else {
                // if not, we don't care.
                convention = BusinessDayConvention.UNADJUSTED;
            }
        }

        BusinessDayConvention terminationDateConvention;
        // if set explicitly, we use it;
        // NOLINT(readability-implicit-bool-conversion)
        // Unadjusted as per ISDA specification
        terminationDateConvention = terminationDateConvention_.orElse(convention);

        Calendar calendar = calendar_;
        // if no calendar was set...
        if (calendar.empty()) {
            // ...we use a null one.
            calendar = new NullCalendar();
        }

        return new Schedule(effectiveDate_, terminationDate_, tenor_.get(), calendar,
                convention, terminationDateConvention,
                rule_, endOfMonth_, firstDate_, nextToLastDate_);
    }
}
