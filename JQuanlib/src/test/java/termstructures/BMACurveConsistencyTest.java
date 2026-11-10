package termstructures;

import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.BMAIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.USDLibor;
import jquant.instruments.BMASwap;
import jquant.instruments.Swap;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.BMASwapRateHelper;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.JointCalendar;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QuantLib {@code testBMACurveConsistency} for the log-linear discount curve.
 * The expected Libor fractions are the published BMA quotes, not values
 * computed from the helper under test. Tolerance is the official {@code 1e-9}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/QuantLib-v1.16/test-suite/piecewiseyieldcurve.cpp
 */
public class BMACurveConsistencyTest {
    private static final double TOLERANCE = 1.0e-9;
    /** Official {@code bmaData} percentages. */
    private static final double[][] BMA = {
            {1, 67.56}, {2, 68.00}, {3, 68.25}, {4, 68.50}, {5, 68.81},
            {7, 69.50}, {10, 70.44}, {15, 71.69}, {20, 72.69}, {30, 73.81}
    };

    @Test
    public void testBMACurveConsistency() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            BMAIndex fixingIndex = new BMAIndex();
            USDLibor calendarLibor = new USDLibor(new Period(3, TimeUnit.MONTHS));
            Calendar calendar = new JointCalendar(fixingIndex.fixingCalendar(),
                    calendarLibor.fixingCalendar(), JointCalendar.Rule.JoinHolidays);
            Date today = calendar.adjust(Date.todaysDate(), BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            Date settlement = calendar.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            YieldTermStructure riskFree = new FlatForward(settlement, 0.04, new Actual360(false),
                    Compounding.Continuous, Frequency.ANNUAL);
            USDLibor libor = new USDLibor(new Period(3, TimeUnit.MONTHS), new Handle<>(riskFree, false));
            List<RateHelper> helpers = new ArrayList<>();
            for (double[] row : BMA) {
                helpers.add(new BMASwapRateHelper(
                        new Handle<>(new SimpleQuote(row[1] / 100.0), false),
                        new Period((int) row[0], TimeUnit.YEARS),
                        2, calendar, new Period(Frequency.QUARTERLY),
                        BusinessDayConvention.FOLLOWING,
                        new ActualActual(ActualActual.Convention.ISDA),
                        fixingIndex, libor));
            }
            int weekday = today.weekday().getValue();
            Date lastWednesday = weekday >= 4
                    ? today.add(-(weekday - 4))
                    : today.add(4 - weekday - 7);
            Date lastFixing = fixingIndex.fixingCalendar().adjust(lastWednesday, BusinessDayConvention.FOLLOWING);
            fixingIndex.addFixing(lastFixing, 0.03, false);

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(today, helpers, new Actual360(false));
            BMAIndex bma = new BMAIndex(new Handle<>(curve, false));
            for (double[] row : BMA) {
                int years = (int) row[0];
                double expected = row[1] / 100.0;
                Date maturity = settlement.add(new Period(years, TimeUnit.YEARS));
                Schedule bmaSchedule = new MakeSchedule().from(settlement).to(maturity)
                        .withFrequency(Frequency.QUARTERLY)
                        .withCalendar(bma.fixingCalendar())
                        .withConvention(BusinessDayConvention.FOLLOWING)
                        .backwards()
                        .toSchedule();
                Schedule liborSchedule = new MakeSchedule().from(settlement).to(maturity)
                        .withTenor(libor.tenor())
                        .withCalendar(libor.fixingCalendar())
                        .withConvention(libor.businessDayConvention())
                        .endOfMonth(libor.endOfMonth())
                        .backwards()
                        .toSchedule();
                BMASwap swap = new BMASwap(Swap.Type.Payer, 100.0,
                        liborSchedule, 0.75, 0.0, libor, libor.dayCounter(),
                        bmaSchedule, bma, new ActualActual(ActualActual.Convention.ISDA));
                swap.setPricingEngine(new DiscountingMultiLegSwapEngine(riskFree));
                assertEquals(expected, swap.fairLiborFraction(), TOLERANCE, years + " year BMA swap");
            }
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }
}
