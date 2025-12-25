package jquant.time;

import jquant.Settings;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.time.DateGenerationRule.*;

//! Payment schedule
/*! \ingroup datetime */
public class Schedule {
    private Optional<Period> tenor_ = Optional.empty();
    private Calendar calendar_;
    private BusinessDayConvention convention_;
    private Optional<BusinessDayConvention> terminationDateConvention_ = Optional.empty();
    private Optional<DateGenerationRule> rule_ = Optional.empty();
    private Optional<Boolean> endOfMonth_ = Optional.empty();
    private Date firstDate_, nextToLastDate_;
    private List<Date> dates_;
    private List<Boolean> isRegular_;

    /*! constructor that takes any list of dates, and optionally
        meta information that can be used by client classes. Note
        that neither the list of dates nor the meta information is
        checked for plausibility in any sense. */
    public Schedule(
            final List<Date> dates,
            Calendar calendar,
            BusinessDayConvention convention,
            final Optional<BusinessDayConvention> terminationDateConvention,
            final Optional<Period> tenor,
            final Optional<DateGenerationRule> rule,
            final Optional<Boolean> endOfMonth,
            List<Boolean> isRegular) {
        tenor_ = tenor;
        calendar_ = calendar;
        convention_ = convention;
        terminationDateConvention_ = terminationDateConvention;
        rule_ = rule;
        dates_ = dates;
        isRegular_ = isRegular;
        if (tenor.isPresent() && !TimeUtils.allowsEndOfMonth(tenor.get()))
            endOfMonth_ = Optional.of(false);
        else
            endOfMonth_ = endOfMonth;
        QL_REQUIRE(isRegular_.isEmpty() || isRegular_.size() == dates.size() - 1,
                "isRegular size (" + isRegular_.size()
                        + ") must be zero or equal to the number of dates minus 1 ("
                        + (dates.size() - 1) + ")");
    }

    /*! rule based constructor */
    public Schedule(Date effectiveDate,
                    final Date terminationDate,
                    final Period tenor,
                    Calendar cal,
                    BusinessDayConvention convention,
                    BusinessDayConvention terminationDateConvention,
                    DateGenerationRule rule,
                    boolean endOfMonth,
                    final Date first,
                    final Date nextToLast) {
        tenor_ = Optional.of(tenor);
        calendar_ = cal;
        convention_ = convention;
        terminationDateConvention_ = Optional.of(terminationDateConvention);
        rule_ = Optional.of(rule);
        endOfMonth_ = TimeUtils.allowsEndOfMonth(tenor) ? Optional.of(endOfMonth) : Optional.of(false);
        firstDate_ = first.equals(effectiveDate) ? new Date() : first;
        nextToLastDate_ = nextToLast.equals(terminationDate) ? new Date() : nextToLast;
        dates_ = new ArrayList<>();
        isRegular_ = new ArrayList<>();
        // sanity checks
        QL_REQUIRE(TimeUtils.neq(terminationDate, new Date()), "null termination date");
        // in many cases (e.g. non-expired bonds) the effective date is not
        // really necessary. In these cases a decent placeholder is enough
        if (effectiveDate.equals(new Date()) && first.equals(new Date())
                && rule == DateGenerationRule.BACKWARD) {
            Date evalDate = Settings.instance.evaluationDate().Date();
            QL_REQUIRE(TimeUtils.less(evalDate, terminationDate), "null effective date");
            int y;
            if (TimeUtils.neq(nextToLast, new Date())) {
                y = (TimeUtils.substract(nextToLast, evalDate)) / 366 + 1;
                effectiveDate = nextToLast.substract(TimeUtils.multiply(y, TimeUnit.YEARS));
            } else {
                y = (TimeUtils.substract(terminationDate, evalDate)) / 366 + 1;
                effectiveDate = terminationDate.substract(TimeUtils.multiply(y, TimeUnit.YEARS));
            }
        } else
            QL_REQUIRE(TimeUtils.neq(effectiveDate, new Date()), "null effective date");

        QL_REQUIRE(TimeUtils.less(effectiveDate, terminationDate),
                "effective date (" + effectiveDate
                        + ") later than or equal to termination date ("
                        + terminationDate + ")");

        if (tenor.length() == 0)
            rule_ = Optional.of(ZERO);
        else
            QL_REQUIRE(tenor.length() > 0,
                    "non positive tenor (" + tenor + ") not allowed");

        if (TimeUtils.neq(firstDate_, new Date())) {
            switch (rule_.get()) {
                case BACKWARD:
                case FORWARD:
                    QL_REQUIRE(TimeUtils.greater(firstDate_, effectiveDate) &&
                                    TimeUtils.leq(firstDate_, terminationDate),
                            "first date (" + firstDate_ +
                                    ") out of effective-termination date range (" +
                                    effectiveDate + ", " + terminationDate + "]");
                    // we should ensure that the above condition is still
                    // verified after adjustment
                    break;
                case THIRD_WEDNESDAY:
                    QL_REQUIRE(IMM.isIMMdate(firstDate_, false),
                            "first date (" + firstDate_ +
                                    ") is not an IMM date");
                    break;
                case ZERO:
                case TWENTIETH:
                case TWENTIETH_IMM:
                case OLD_CDS:
                case CDS:
                case CDS2015:
                    QL_FAIL("first date incompatible with " + rule_ +
                            " date generation rule");
                default:
                    QL_FAIL("unknown rule (" + rule_.get().getValue() + ")");
            }
        }
        if (TimeUtils.neq(nextToLastDate_, new Date())) {
            switch (rule_.get()) {
                case BACKWARD:
                case FORWARD:
                    QL_REQUIRE(TimeUtils.geq(nextToLastDate_, effectiveDate) &&
                                    TimeUtils.less(nextToLastDate_, terminationDate),
                            "next to last date (" + nextToLastDate_ +
                                    ") out of effective-termination date range [" +
                                    effectiveDate + ", " + terminationDate + ")");
                    // we should ensure that the above condition is still
                    // verified after adjustment
                    break;
                case THIRD_WEDNESDAY:
                    QL_REQUIRE(IMM.isIMMdate(nextToLastDate_, false),
                            "next-to-last date (" + nextToLastDate_ +
                                    ") is not an IMM date");
                    break;
                case ZERO:
                case TWENTIETH:
                case TWENTIETH_IMM:
                case OLD_CDS:
                case CDS:
                case CDS2015:
                    QL_FAIL("next to last date incompatible with " + rule_ +
                            " date generation rule");
                default:
                    QL_FAIL("unknown rule (" + rule_.get().getValue() + ")");
            }
        }


        // calendar needed for endOfMonth adjustment
        Calendar nullCalendar = new NullCalendar();
        int periods = 1;
        Date seed = new Date();
        Date exitDate = new Date();
        switch (rule_.get()) {

            case ZERO:
                tenor_ = Optional.of(TimeUtils.multiply(0, TimeUnit.YEARS));
                dates_.add(effectiveDate);
                dates_.add(terminationDate);
                isRegular_.add(true);
                break;

            case BACKWARD:

                dates_.add(terminationDate);

                seed = terminationDate;
                if (TimeUtils.neq(nextToLastDate_, new Date())) {
                    dates_.add(nextToLastDate_);
                    Date temp = nullCalendar.advance(seed,
                            TimeUtils.negetive(TimeUtils.multiply(periods, (tenor_.get()))), convention, endOfMonth_.get());
                    isRegular_.add(temp == nextToLastDate_);
                    seed = nextToLastDate_;
                }

                exitDate = effectiveDate;
                if (TimeUtils.neq(firstDate_, new Date()))
                    exitDate = firstDate_;

                for (; ; ) {
                    Date temp = nullCalendar.advance(seed,
                            TimeUtils.negetive(TimeUtils.multiply(periods, (tenor_.get()))), convention, endOfMonth_.get());
                    if (TimeUtils.less(temp, exitDate)) {
                        if (TimeUtils.neq(firstDate_, new Date()) &&
                                (calendar_.adjust(dates_.get(dates_.size() - 1), convention) !=
                                        calendar_.adjust(firstDate_, convention))) {
                            dates_.add(firstDate_);
                            isRegular_.add(false);
                        }
                        break;
                    } else {
                        // skip dates that would result in duplicates
                        // after adjustment
                        if (TimeUtils.neq(calendar_.adjust(dates_.get(dates_.size() - 1), convention),
                                calendar_.adjust(temp, convention))) {
                            dates_.add(temp);
                            isRegular_.add(true);
                        }
                        ++periods;
                    }
                }

                if (calendar_.adjust(dates_.get(dates_.size() - 1), convention) !=
                        calendar_.adjust(effectiveDate, convention)) {
                    dates_.add(effectiveDate);
                    isRegular_.add(false);
                }
                Collections.reverse(dates_);
                Collections.reverse(isRegular_);
                break;

            case TWENTIETH:
            case TWENTIETH_IMM:
            case THIRD_WEDNESDAY:
            case THIRD_WEDNESDAY_INCLUSIVE:
            case OLD_CDS:
            case CDS:
            case CDS2015:
                QL_REQUIRE(!endOfMonth_.get(),
                        "endOfMonth convention incompatible with " + rule_ +
                                " date generation rule");
            case FORWARD:

                if (rule_.get() == CDS || rule_.get() == CDS2015) {
                    Date prev20th = TimeUtils.previousTwentieth(effectiveDate, rule_.get());
                    if (TimeUtils.greater(calendar_.adjust(prev20th, convention), effectiveDate)) {
                        dates_.add(prev20th.substract(TimeUtils.multiply(3, TimeUnit.MONTHS)));
                        isRegular_.add(true);
                    }
                    dates_.add(prev20th);
                } else {
                    dates_.add(effectiveDate);
                }

                seed = dates_.get(dates_.size() - 1);

                if (TimeUtils.neq(firstDate_, new Date())) {
                    dates_.add(firstDate_);
                    Date temp = nullCalendar.advance(seed, TimeUtils.multiply(periods, (tenor_.get())),
                            convention, endOfMonth_.get());
                    if (temp != firstDate_)
                        isRegular_.add(false);
                    else
                        isRegular_.add(true);
                    seed = firstDate_;
                } else if (rule_.get() == TWENTIETH ||
                        rule_.get() == TWENTIETH_IMM ||
                        rule_.get() == OLD_CDS ||
                        rule_.get() == CDS ||
                        rule_.get() == CDS2015) {
                    Date next20th = TimeUtils.nextTwentieth(effectiveDate, rule_.get());
                    if (rule_.get() == OLD_CDS) {
                        // distance rule inforced in natural days
                        int stubDays = 30;
                        if (TimeUtils.substract(next20th, effectiveDate) < stubDays) {
                            // +1 will skip this one and get the next
                            next20th = TimeUtils.nextTwentieth(next20th.add(1), rule_.get());
                        }
                    }
                    if (next20th != effectiveDate) {
                        dates_.add(next20th);
                        isRegular_.add(rule_.get() == CDS || rule_.get() == CDS2015);
                        seed = next20th;
                    }
                }

                exitDate = terminationDate;
                if (TimeUtils.neq(nextToLastDate_, new Date()))
                    exitDate = nextToLastDate_;
                for (; ; ) {
                    Date temp = nullCalendar.advance(seed, TimeUtils.multiply(periods, (tenor_.get())),
                            convention, endOfMonth_.get());
                    if (TimeUtils.greater(temp, exitDate)) {
                        if (TimeUtils.neq(nextToLastDate_, new Date()) &&
                                (calendar_.adjust(dates_.get(dates_.size() - 1), convention) !=
                                        calendar_.adjust(nextToLastDate_, convention))) {
                            dates_.add(nextToLastDate_);
                            isRegular_.add(false);
                        }
                        break;
                    } else {
                        // skip dates that would result in duplicates
                        // after adjustment
                        if (calendar_.adjust(dates_.get(dates_.size() - 1), convention) !=
                                calendar_.adjust(temp, convention)) {
                            dates_.add(temp);
                            isRegular_.add(true);
                        }
                        ++periods;
                    }
                }

                if (calendar_.adjust(dates_.get(dates_.size() - 1), terminationDateConvention) !=
                        calendar_.adjust(terminationDate, terminationDateConvention)) {
                    if (rule_.get() == TWENTIETH ||
                            rule_.get() == TWENTIETH_IMM ||
                            rule_.get() == OLD_CDS ||
                            rule_.get() == CDS ||
                            rule_.get() == CDS2015) {
                        dates_.add(TimeUtils.nextTwentieth(terminationDate, rule_.get()));
                        isRegular_.add(true);
                    } else {
                        dates_.add(terminationDate);
                        isRegular_.add(false);
                    }
                }

                break;

            default:
                QL_FAIL("unknown rule (" + rule_.get().getValue() + ")");
        }

        // adjustments
        if (rule_.get() == THIRD_WEDNESDAY)
            for (int i = 1; i < dates_.size() - 1; ++i)
                dates_.set(i, Date.nthWeekday(3, Weekday.WEDNESDAY,
                        dates_.get(i).month(),
                        dates_.get(i).year()));
        else if (rule_.get() == THIRD_WEDNESDAY_INCLUSIVE)
            for (Date date : dates_)
                date = Date.nthWeekday(3, Weekday.WEDNESDAY, date.month(), date.year());

        // first date not adjusted for old CDS schedules
        if (convention != BusinessDayConvention.UNADJUSTED && rule_.get() != OLD_CDS)
            dates_.set(0, calendar_.adjust(dates_.get(0), convention));

        // termination date is NOT adjusted as per ISDA
        // specifications, unless otherwise specified in the
        // confirmation of the deal or unless we're creating a CDS
        // schedule
        if (terminationDateConvention != BusinessDayConvention.UNADJUSTED
                && rule_.get() != CDS
                && rule_.get() != CDS2015) {
            dates_.set(dates_.size() - 1, calendar_.adjust(dates_.get(dates_.size() - 1),
                    terminationDateConvention));
        }

        if (endOfMonth_.get() && calendar_.isEndOfMonth(seed)) {
            // adjust to end of month
            for (int i = 1; i < dates_.size() - 1; ++i)
                dates_.set(i, calendar_.adjust(Date.endOfMonth(dates_.get(i)), convention));
        } else {
            for (int i = 1; i < dates_.size() - 1; ++i)
                dates_.set(i, calendar_.adjust(dates_.get(i), convention));
        }

        // Final safety checks to remove extra next-to-last date, if
        // necessary.  It can happen to be equal or later than the end
        // date due to EOM adjustments (see the Schedule test suite
        // for an example).
        if (dates_.size() >= 2 && TimeUtils.geq(dates_.get(dates_.size() - 2), dates_.get(dates_.size() - 1))) {
            // there might be two dates only, then isRegular_ has size one
            if (isRegular_.size() >= 2) {
                isRegular_.set(isRegular_.size() - 2,
                        TimeUtils.equals(dates_.get(dates_.size() - 2), dates_.get(dates_.size() - 1)));
            }
            dates_.set(dates_.size() - 2, dates_.get(dates_.size() - 1));
            dates_.remove(dates_.size() - 1);
            isRegular_.remove(isRegular_.size() - 1);
        }
        if (dates_.size() >= 2 && TimeUtils.leq(dates_.get(1), dates_.get(0))) {
            isRegular_.set(1,
                    TimeUtils.equals(dates_.get(1), dates_.get(0)));
            dates_.set(1, dates_.get(0));
            dates_.remove(0);
            isRegular_.remove(0);
        }

        QL_REQUIRE(dates_.size() > 1,
                "degenerate single date (" + dates_.get(0) + ") schedule" +
                        "\n seed date: " + seed +
                        "\n exit date: " + exitDate +
                        "\n effective date: " + effectiveDate +
                        "\n first date: " + first +
                        "\n next to last date: " + nextToLast +
                        "\n termination date: " + terminationDate +
                        "\n generation rule: " + rule_.get() +
                        "\n end of month: " + endOfMonth_.get());
    }

    public Schedule() {
        dates_ = new ArrayList<>();
        isRegular_ = new ArrayList<>();
        tenor_ = Optional.empty();
        terminationDateConvention_ = Optional.empty();
        rule_ = Optional.empty();
        endOfMonth_ = Optional.empty();
        firstDate_ = new Date();
        nextToLastDate_ = new Date();
    }

    public int size() {
        return dates_.size();
    }

    public Date at(int i) {
        return dates_.get(i);
    }

    public Date date(int i) {
        return dates_.get(i);
    }

    public List<Date> dates() {
        return dates_;
    }

    public boolean empty() {
        return dates_.isEmpty();
    }

    public Date front() {
        QL_REQUIRE(!dates_.isEmpty(), "no front date for empty schedule");
        return dates_.get(0);
    }

    public Date back() {
        QL_REQUIRE(!dates_.isEmpty(), "no back date for empty schedule");
        return dates_.get(dates_.size() - 1);
    }

    public Date previousDate(final Date refDate) {
        int res = lower_bound(refDate);
        if (res != 0)
            return dates_.get(--res);
        else
            return new Date();
    }

    public Date nextDate(final Date refDate) {
        int res = lower_bound(refDate);
        if (res != dates_.size())
            return dates_.get(res);
        else
            return new Date();
    }

    public boolean hasIsRegular() {
        return !isRegular_.isEmpty();
    }

    public boolean isRegular(int i) {
        QL_REQUIRE(hasIsRegular(),
                "full interface (isRegular) not available");
        QL_REQUIRE(i <= isRegular_.size() && i > 0,
                "index (" + i + ") must be in [1, " +
                        isRegular_.size() + "]");
        return isRegular_.get(i - 1);
    }

    public final List<Boolean> isRegular() {
        QL_REQUIRE(!isRegular_.isEmpty(), "full interface (isRegular) not available");
        return isRegular_;
    }

    public final Calendar calendar() {
        return calendar_;
    }

    public final Date startDate() {
        return dates_.get(0);
    }

    public final Date endDate() {
        return dates_.get(dates_.size() - 1);
    }

    public boolean hasTenor() {
        return tenor_.isPresent();
    }

    public final Period tenor() {
        QL_REQUIRE(hasTenor(),
                "full interface (tenor) not available");
        return tenor_.get();  // NOLINT(bugprone-unchecked-optional-access)
    }

    public BusinessDayConvention businessDayConvention() {
        return convention_;
    }

    public boolean hasTerminationDateBusinessDayConvention() {
        return terminationDateConvention_.isPresent();
    }

    public BusinessDayConvention terminationDateBusinessDayConvention() {
        QL_REQUIRE(hasTerminationDateBusinessDayConvention(),
                "full interface (termination date bdc) not available");
        return terminationDateConvention_.get();  // NOLINT(bugprone-unchecked-optional-access)
    }

    public boolean hasRule() {
        return rule_.isPresent();
    }

    public DateGenerationRule rule() {
        QL_REQUIRE(hasRule(), "full interface (rule) not available");
        return rule_.get();  // NOLINT(bugprone-unchecked-optional-access)
    }

    public boolean hasEndOfMonth() {
        return endOfMonth_.isPresent();
    }

    public boolean endOfMonth() {
        QL_REQUIRE(hasEndOfMonth(),
                "full interface (end of month) not available");
        return endOfMonth_.get();  // NOLINT(bugprone-unchecked-optional-access)
    }

    public int begin() {
        return 0;
    }

    public int end() {
        return dates_.size();
    }

    public int lower_bound(final Date refDate) {
        Date d = TimeUtils.equals(refDate, new Date()) ? Settings.instance.evaluationDate().Date() : refDate;
        int index = 0;
        while (!TimeUtils.leq(d, dates_.get(index))) {
            index++;
        }
        return index;
    }

    public Schedule after(final Date truncationDate) {
        Schedule result = this.copy();

        QL_REQUIRE(TimeUtils.less(truncationDate, result.dates_.get(result.dates_.size() - 1)),
                "truncation date " + truncationDate +
                        " must be before the last schedule date " +
                        result.dates_.get(result.dates_.size() - 1));
        if (TimeUtils.greater(truncationDate, result.dates_.get(0))) {
            // remove earlier dates
            while (TimeUtils.less(result.dates_.get(0), truncationDate)) {
                result.dates_.remove(0);
                if (!result.isRegular_.isEmpty())
                    result.isRegular_.remove(0);
            }

            // add truncationDate if missing
            if (TimeUtils.neq(truncationDate, result.dates_.get(0))) {
                result.dates_.add(0, truncationDate);
                result.isRegular_.add(0, false);
                result.terminationDateConvention_ = Optional.of(BusinessDayConvention.UNADJUSTED);
            } else {
                result.terminationDateConvention_ = Optional.of(convention_);
            }

            if (TimeUtils.leq(result.nextToLastDate_, truncationDate))
                result.nextToLastDate_ = new Date();
            if (TimeUtils.leq(result.firstDate_, truncationDate))
                result.firstDate_ = new Date();
        }
        return result;
    }

    public Schedule until(final Date truncationDate) {
        Schedule result = this.copy();
        QL_REQUIRE(TimeUtils.greater(truncationDate, result.dates_.get(0)),
                "truncation date " + truncationDate +
                        " must be later than schedule first date " +
                        result.dates_.get(0));
        if (TimeUtils.less(truncationDate, result.dates_.get(result.dates_.size() - 1))) {
            // remove later dates
            while (TimeUtils.greater(result.dates_.get(result.dates_.size() - 1), truncationDate)) {
                result.dates_.remove(result.dates_.size() - 1);
                if (!result.isRegular_.isEmpty())
                    result.isRegular_.remove(result.isRegular_.size() - 1);
            }

            // add truncationDate if missing
            if (TimeUtils.neq(truncationDate, result.dates_.get(result.dates_.size() - 1))) {
                result.dates_.add(truncationDate);
                result.isRegular_.add(false);
                result.terminationDateConvention_ = Optional.of(BusinessDayConvention.UNADJUSTED);
            } else {
                result.terminationDateConvention_ = Optional.of(convention_);
            }

            if (TimeUtils.geq(result.nextToLastDate_, truncationDate))
                result.nextToLastDate_ = new Date();
            if (TimeUtils.geq(result.firstDate_, truncationDate))
                result.firstDate_ = new Date();
        }

        return result;
    }

    public Schedule copy() {
        return new Schedule(this.dates_, this.calendar_, this.convention_,
                this.terminationDateConvention_, this.tenor_, this.rule_, this.endOfMonth_, this.isRegular_);
    }
}
