package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.math.interpolations.impl.ConvexMonotone;
import jquant.math.interpolations.impl.Linear;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.IterativeBootstrap;
import jquant.termstructures.yield.LocalBootstrap;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * QuantLib {@code testConstructionWithExplicitBootstrap}
 * (test-suite/piecewiseyieldcurve.cpp lines 1213-1236): constructing a
 * {@code PiecewiseYieldCurve<ForwardRate, Linear, IterativeBootstrap>} and a
 * {@code PiecewiseYieldCurve<ForwardRate, ConvexMonotone, LocalBootstrap>} with
 * an explicitly supplied bootstrap object must succeed, and
 * {@code discount(1.0, true)} must not throw. The official test checks no
 * numeric value, so neither does this one.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/iterativebootstrap.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/localbootstrap.hpp
 */
public class ExplicitBootstrapConstructionTest {
    private static final double[] DEPOSIT_RATES = {4.559, 4.581, 4.573, 4.557, 4.496, 4.490};
    private static final int[] DEPOSIT_LENGTHS = {1, 1, 2, 3, 6, 9};
    private static final TimeUnit[] DEPOSIT_UNITS = {
            TimeUnit.WEEKS, TimeUnit.MONTHS, TimeUnit.MONTHS, TimeUnit.MONTHS,
            TimeUnit.MONTHS, TimeUnit.MONTHS
    };
    private static final double[] SWAP_RATES = {
            4.54, 4.63, 4.75, 4.86, 4.99, 5.11, 5.23, 5.33, 5.41, 5.47, 5.60, 5.75, 5.89, 5.95, 5.96
    };
    private static final int[] SWAP_YEARS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};

    @Test
    public void testConstructionWithExplicitBootstrap() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor6m = euribor(new Period(6, TimeUnit.MONTHS), flat());
            List<RateHelper> instruments = new ArrayList<>();
            for (int i = 0; i < DEPOSIT_RATES.length; i++) {
                instruments.add(new DepositRateHelper(
                        new Handle<>(new SimpleQuote(DEPOSIT_RATES[i] / 100.0), false),
                        new Period(DEPOSIT_LENGTHS[i], DEPOSIT_UNITS[i]), 2, target,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));
            }
            for (int i = 0; i < SWAP_RATES.length; i++) {
                instruments.add(new SwapRateHelper(
                        new Handle<>(new SimpleQuote(SWAP_RATES[i] / 100.0), false),
                        new Period(SWAP_YEARS[i], TimeUnit.YEARS), target, Frequency.ANNUAL,
                        BusinessDayConvention.UNADJUSTED,
                        new Thirty360(Thirty360.Convention.BondBasis), euribor6m));
            }

            // PiecewiseYieldCurve<ForwardRate, Linear, IterativeBootstrap>
            // with an explicit IterativeBootstrap object.
            YieldTermStructure linear = new PiecewiseYieldCurve(
                    settlement, instruments, actual360,
                    BootstrapTraits.ForwardRate, new Linear(), new IterativeBootstrap());
            assertDoesNotThrow(() -> linear.discount(1.0, true));

            // PiecewiseYieldCurve<ForwardRate, ConvexMonotone, LocalBootstrap>
            // with an explicit LocalBootstrap object.
            YieldTermStructure convexMonotone = new PiecewiseYieldCurve(
                    settlement, instruments, actual360,
                    BootstrapTraits.ForwardRate, new ConvexMonotone(), new LocalBootstrap());
            assertDoesNotThrow(() -> convexMonotone.discount(1.0, true));
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static YieldTermStructure flat() {
        return new FlatForward(new Date(15, Month.JANUARY, 2025), 0.04, new Actual360(false),
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static IborIndex euribor(Period tenor, YieldTermStructure curve) {
        return new IborIndex("Euribor", tenor, 2, new Currency(), new Target(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true, new Actual360(false),
                new Handle<>(curve, false));
    }
}
