package jquant.cashflows;

import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Stub Ibor coupon fixing on an explicitly supplied set of indices
/*! This coupon is intended for broken periods.  It fixes on a
    combination of the indices selected by the given selection.

    https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/stubiborcoupon.hpp
 */
public class StubIborCoupon extends IborCoupon {

    //! %Index fixing as a fixed-weight linear combination of Ibor indices
    public static class WeightedIndex extends IborIndex {
        //! a component index and the weight it carries in the fixing
        public record Component(IborIndex index, double weight) {
        }

        private final List<Component> components_;

        public WeightedIndex(List<Component> components) {
            super("Weighted",
                    longestTenor(components),
                    firstComponent(components).fixingDays(),
                    firstComponent(components).currency(),
                    firstComponent(components).fixingCalendar(),
                    firstComponent(components).businessDayConvention(),
                    firstComponent(components).endOfMonth(),
                    firstComponent(components).dayCounter(),
                    null);
            components_ = components;

            /* The combination fixes on a single date and its clients derive
               dates from a single set of conventions.
            */
            final IborIndex first = components_.get(0).index();
            for (Component component : components_) {
                final IborIndex index = component.index();
                QL_REQUIRE(index != null, "null component index");
                QL_REQUIRE(index.fixingDays() == first.fixingDays(),
                        index.name() + " fixing days (" + index.fixingDays()
                                + ") do not match " + first.name() + " ("
                                + first.fixingDays() + ")");
                QL_REQUIRE(TimeUtils.equals(index.fixingCalendar(), first.fixingCalendar()),
                        index.name() + " fixing calendar does not match " + first.name());
                QL_REQUIRE(TimeUtils.equals(index.dayCounter(), first.dayCounter()),
                        index.name() + " day counter does not match " + first.name());
                QL_REQUIRE(index.currency().equals(first.currency()),
                        index.name() + " currency does not match " + first.name());
                registerWith(index);
            }

            StringBuilder out = new StringBuilder();
            for (int i = 0; i < components_.size(); ++i) {
                if (i > 0)
                    out.append(" + ");
                out.append(components_.get(i).index().name())
                        .append("(")
                        .append(String.format(Locale.ROOT, "%.4f", components_.get(i).weight()))
                        .append(")");
            }
            name_ = out.toString();
            registerWith(notifier());
        }

        private static IborIndex firstComponent(final List<Component> components) {
            QL_REQUIRE(!components.isEmpty(), "no components given for weighted index");
            return components.get(0).index();
        }

        private static Period longestTenor(final List<Component> components) {
            QL_REQUIRE(!components.isEmpty(), "no components given for weighted index");
            Period longest = components.get(0).index().tenor();
            for (Component component : components)
                if (TimeUtils.less(longest, component.index().tenor()))
                    longest = component.index().tenor();
            return longest;
        }

        //! the maturity of the longest component
        @Override
        public Date maturityDate(final Date valueDate) {
            Date maturity = Date.minDate();
            for (Component component : components_)
                maturity = TimeUtils.max(maturity, component.index().maturityDate(valueDate));
            return maturity;
        }

        @Override
        public double forecastFixing(final Date fixingDate) {
            // as in SwapSpreadIndex, this also handles the case where one of the
            // components has a historic fixing on the evaluation date
            double fixing = 0.0;
            for (Component component : components_)
                fixing += component.weight() * component.index().fixing(fixingDate, false);
            return fixing;
        }

        @Override
        public double pastFixing(final Date fixingDate) {
            double fixing = 0.0;
            for (Component component : components_) {
                final double componentFixing = component.index().pastFixing(fixingDate);
                // a missing component means a missing fixing for the combination
                if (Double.isNaN(componentFixing))
                    return Double.NaN;
                fixing += component.weight() * componentFixing;
            }
            return fixing;
        }

        @Override
        public boolean allowsNativeFixings() {
            return false;
        }

        @Override
        public IborIndex clone(final Handle<YieldTermStructure> h) {
            QL_FAIL("StubIborCoupon::WeightedIndex cannot be relinked to a single forwarding curve; "
                    + "relink its components instead");
            return null;
        }

        public List<Component> components() {
            return components_;
        }
    }

    private final StubIndexSelection stubIndexSelection_;
    private final WeightedIndex weightedIndex_;

    public StubIborCoupon(final Date paymentDate,
                          double nominal,
                          final Date startDate,
                          final Date endDate,
                          Integer fixingDays,
                          final StubIndexSelection stubIndexSelection,
                          double gearing,
                          double spread,
                          final Date refPeriodStart,
                          final Date refPeriodEnd,
                          final DayCounter dayCounter,
                          boolean isInArrears,
                          final Date exCouponDate,
                          BusinessDayConvention fixingConvention) {
        this(paymentDate, nominal, startDate, endDate, fixingDays,
                makeStubIndex(stubIndexSelection, startDate, endDate, fixingDays, isInArrears,
                        fixingConvention),
                stubIndexSelection, gearing, spread, refPeriodStart, refPeriodEnd, dayCounter,
                isInArrears, exCouponDate, fixingConvention);
    }

    public StubIborCoupon(final Date paymentDate,
                          double nominal,
                          final Date startDate,
                          final Date endDate,
                          Integer fixingDays,
                          final StubIndexSelection stubIndexSelection) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, stubIndexSelection,
                1.0, 0.0, new Date(), new Date(), new DayCounter(), false, new Date(),
                BusinessDayConvention.PRECEDING);
    }

    private StubIborCoupon(final Date paymentDate,
                           double nominal,
                           final Date startDate,
                           final Date endDate,
                           Integer fixingDays,
                           final WeightedIndex weightedIndex,
                           final StubIndexSelection stubIndexSelection,
                           double gearing,
                           double spread,
                           final Date refPeriodStart,
                           final Date refPeriodEnd,
                           final DayCounter dayCounter,
                           boolean isInArrears,
                           final Date exCouponDate,
                           BusinessDayConvention fixingConvention) {
        super(paymentDate, nominal, startDate, endDate, fixingDays, weightedIndex, gearing,
                spread, refPeriodStart, refPeriodEnd, dayCounter, isInArrears, exCouponDate,
                fixingConvention);
        stubIndexSelection_ = stubIndexSelection;
        weightedIndex_ = weightedIndex;
        for (WeightedIndex.Component component : weightedIndex_.components())
            QL_REQUIRE(TimeUtils.equals(component.index().valueDate(fixingDate()), accrualStartDate()),
                    component.index().name()
                            + " value date for the coupon fixing date " + fixingDate()
                            + " does not match accrual start date " + accrualStartDate());
    }

    public final StubIndexSelection stubIndexSelection() {
        return stubIndexSelection_;
    }

    @Override
    public double indexFixing() {
        return weightedIndex_.fixing(fixingDate(), true);
    }

    @Override
    public boolean hasFixed() {
        final Date today = jquant.Settings.instance.evaluationDate().Date();
        if (TimeUtils.greater(fixingDate(), today))
            return false;
        if (TimeUtils.less(fixingDate(), today))
            return true;
        if (jquant.Settings.instance.enforcesTodaysHistoricFixings())
            return true;
        final List<WeightedIndex.Component> components = weightedIndex_.components();
        for (WeightedIndex.Component component : components)
            if (!component.index().hasHistoricalFixing(fixingDate()))
                return false;
        return true;
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                Visitor<StubIborCoupon> vv = (Visitor<StubIborCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                super.accept(v);
            }
        } else {
            super.accept(v);
        }
    }

    private static WeightedIndex makeStubIndex(final StubIndexSelection selection,
                                               final Date startDate,
                                               final Date endDate,
                                               Integer fixingDays,
                                               boolean isInArrears,
                                               BusinessDayConvention fixingConvention) {
        QL_REQUIRE(!selection.empty(),
                "StubIborCoupon requires a non-empty stub index selection");
        QL_REQUIRE(!isInArrears,
                "stub index selection is not supported for in-arrears coupons");

        final Date fixingDate = selection.indices().get(0).fixingCalendar().advance(
                startDate, -fixingDays, TimeUnit.DAYS, fixingConvention, false);

        return new WeightedIndex(selectComponents(selection, fixingDate, startDate, endDate));
    }

    private record Candidate(Date maturity, IborIndex index) {
    }

    /*! Returns the indices an accrual period fixes on, with their weights.
        All candidates should start the rate on accrualStartDate, and their
        maturities must be distinct.
    */
    private static List<WeightedIndex.Component> selectComponents(
            final StubIndexSelection selection,
            final Date fixingDate,
            final Date accrualStartDate,
            final Date accrualEndDate) {
        QL_REQUIRE(!selection.empty(),
                "no candidate indices provided for stub index selection");

        // (maturity, index) pairs, sorted by maturity so selection reduces to
        // a nearest-neighbour or bracket search.
        List<Candidate> candidates = new ArrayList<>(selection.indices().size());
        for (IborIndex candidate : selection.indices()) {
            final Date candidateValueDate = candidate.valueDate(fixingDate);
            QL_REQUIRE(TimeUtils.equals(candidateValueDate, accrualStartDate),
                    candidate.name() + " value date " + candidateValueDate
                            + " does not match accrual start date " + accrualStartDate);
            candidates.add(new Candidate(candidate.maturityDate(candidateValueDate), candidate));
        }
        candidates.sort((a, b) -> a.maturity().serialNumber() - b.maturity().serialNumber());
        for (int i = 1; i < candidates.size(); ++i)
            QL_REQUIRE(TimeUtils.less(candidates.get(i - 1).maturity(),
                            candidates.get(i).maturity()),
                    "duplicate candidate index maturity " + candidates.get(i).maturity());

        switch (selection.convention()) {
            case ClosestIndex: {
                // Since candidates are sorted, ties select the smaller maturity.
                Candidate closest = candidates.get(0);
                long closestDistance = Math.abs(
                        (long) closest.maturity().serialNumber() - accrualEndDate.serialNumber());
                for (Candidate candidate : candidates) {
                    long distance = Math.abs(
                            (long) candidate.maturity().serialNumber() - accrualEndDate.serialNumber());
                    if (distance < closestDistance) {
                        closest = candidate;
                        closestDistance = distance;
                    }
                }
                return List.of(new WeightedIndex.Component(closest.index(), 1.0));
            }

            case Interpolated: {
                int longer = 0;
                while (longer < candidates.size()
                        && TimeUtils.less(candidates.get(longer).maturity(), accrualEndDate))
                    ++longer;
                QL_REQUIRE(longer != candidates.size(),
                        "index maturities do not bracket irregular coupon end date "
                                + accrualEndDate);
                if (TimeUtils.equals(candidates.get(longer).maturity(), accrualEndDate))
                    return List.of(new WeightedIndex.Component(
                            candidates.get(longer).index(), 1.0));

                QL_REQUIRE(longer != 0,
                        "index maturities do not bracket irregular coupon end date "
                                + accrualEndDate);
                Candidate shorter = candidates.get(longer - 1);
                Candidate longerCandidate = candidates.get(longer);
                double weight = (double) (accrualEndDate.serialNumber()
                        - shorter.maturity().serialNumber())
                        / (double) (longerCandidate.maturity().serialNumber()
                        - shorter.maturity().serialNumber());
                return List.of(
                        new WeightedIndex.Component(shorter.index(), 1.0 - weight),
                        new WeightedIndex.Component(longerCandidate.index(), weight));
            }

            default:
                QL_FAIL("unknown stub index convention");
                return new ArrayList<>();
        }
    }
}
