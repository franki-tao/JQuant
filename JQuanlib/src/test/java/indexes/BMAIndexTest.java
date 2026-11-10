package indexes;

import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.BMAIndex;
import jquant.indexes.IndexManager;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Schedule;
import jquant.time.TimeUtils;
import jquant.time.Weekday;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code BMAIndex} and the BMA half of
 * {@code test-suite/indexes.cpp} {@code testFixingObservability}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/indexes/bmaindex.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/test-suite/indexes.cpp
 */
public class BMAIndexTest {
    @Test
    public void testValidFixingDatesAndSchedule() {
        IndexManager.Instance.clearHistories();
        try {
            BMAIndex index = new BMAIndex();
            assertEquals("BMA", index.familyName());
            assertEquals("BMA1W Actual/Actual (ISDA)", index.name());
            assertEquals("USD", index.currency().code());
            assertEquals("US government bond market", index.fixingCalendar().name());
            assertEquals("Actual/Actual (ISDA)", index.dayCounter().name());

            Date wednesday = new Date(15, Month.JANUARY, 2025);
            Date thursday = new Date(16, Month.JANUARY, 2025);
            assertEquals(Weekday.WEDNESDAY, wednesday.weekday());
            assertTrue(index.fixingCalendar().isBusinessDay(wednesday));
            assertTrue(index.isValidFixingDate(wednesday));
            assertFalse(index.isValidFixingDate(thursday));
            IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                    () -> index.addFixing(thursday, 0.01, false));
            assertTrue(rejected.getMessage().contains("invalid fixing"));
            index.addFixing(wednesday, 0.01, false);
            assertTrue(index.hasHistoricalFixing(wednesday));

            // Wednesday 1 January 2025 is New Year's Day. The fixing rolls to Thursday 2 January.
            Date newYear = new Date(1, Month.JANUARY, 2025);
            Date rolled = new Date(2, Month.JANUARY, 2025);
            Date friday = new Date(3, Month.JANUARY, 2025);
            assertEquals(Weekday.WEDNESDAY, newYear.weekday());
            assertFalse(index.fixingCalendar().isBusinessDay(newYear));
            assertFalse(index.isValidFixingDate(newYear));
            assertTrue(index.isValidFixingDate(rolled));
            assertFalse(index.isValidFixingDate(friday));
            assertThrows(IllegalArgumentException.class, () -> index.addFixing(newYear, 0.02, false));

            Schedule plain = index.fixingSchedule(
                    new Date(13, Month.JANUARY, 2025), new Date(31, Month.JANUARY, 2025));
            assertSchedule(plain,
                    new Date(8, Month.JANUARY, 2025),
                    new Date(15, Month.JANUARY, 2025),
                    new Date(22, Month.JANUARY, 2025),
                    new Date(29, Month.JANUARY, 2025),
                    new Date(5, Month.FEBRUARY, 2025));

            // Christmas 2024 and New Year 2025 are Wednesdays, so Following moves those fixings.
            Schedule rolledSchedule = index.fixingSchedule(
                    new Date(30, Month.DECEMBER, 2024), new Date(8, Month.JANUARY, 2025));
            assertSchedule(rolledSchedule,
                    new Date(26, Month.DECEMBER, 2024),
                    new Date(2, Month.JANUARY, 2025),
                    new Date(8, Month.JANUARY, 2025),
                    new Date(15, Month.JANUARY, 2025));
        } finally {
            IndexManager.Instance.clearHistories();
        }
    }

    @Test
    public void testFixingObservability() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Flag flag = new Flag();
            BMAIndex watched = new BMAIndex();
            flag.registerWith(watched);
            flag.up = false;

            BMAIndex writer = new BMAIndex();
            Date fixing = new Date(15, Month.JANUARY, 2025);
            while (!writer.isValidFixingDate(fixing))
                fixing = fixing.add(1);
            writer.addFixing(fixing, 0.01, false);
            assertTrue(flag.up);
            Settings.instance.evaluationDate().equal(fixing.add(1));
            assertEquals(0.01, watched.fixing(fixing, false), 0.0);
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testHistoricalAndForecastFixings() {
        IndexManager.Instance.clearHistories();
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date wednesday = new Date(15, Month.JANUARY, 2025);
            Date valueDate = new Date(16, Month.JANUARY, 2025);
            Date maturity = new Date(23, Month.JANUARY, 2025);
            YieldTermStructure curve = new FlatForward(new Date(20, Month.DECEMBER, 2024), 0.05,
                    new Actual360(false), Compounding.Continuous, Frequency.ANNUAL);
            BMAIndex index = new BMAIndex(new Handle<>(curve, false));
            assertTrue(TimeUtils.equals(maturity, index.maturityDate(valueDate)));

            Settings.instance.evaluationDate().equal(new Date(16, Month.JANUARY, 2025));
            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                    () -> index.fixing(wednesday, false));
            assertTrue(missing.getMessage().contains(index.name()));
            assertTrue(missing.getMessage().contains(wednesday.toString()));

            index.addFixing(wednesday, 0.0125, false);
            assertEquals(0.0125, index.fixing(wednesday, false), 0.0);

            Settings.instance.evaluationDate().equal(new Date(8, Month.JANUARY, 2025));
            // Flat 5% continuous Actual/360. The Wednesday fixing applies from the next
            // business day for one week: 16 January to 23 January, 7/365 Actual/Actual (ISDA).
            double expected = (Math.exp(0.05 * 7.0 / 360.0) - 1.0) / (7.0 / 365.0);
            assertEquals(0.050719095565300, expected, 1.0e-15);
            assertEquals(expected, index.fixing(wednesday, false), 1.0e-12);
            assertEquals(expected, index.forecastFixing(wednesday), 1.0e-12);

            // New Year Wednesday rolls to 2 January. The forward is then 3 January to 9 January.
            Date rolled = new Date(2, Month.JANUARY, 2025);
            assertTrue(TimeUtils.equals(new Date(3, Month.JANUARY, 2025),
                    index.fixingCalendar().advance(rolled, 1, jquant.time.TimeUnit.DAYS,
                            jquant.time.BusinessDayConvention.FOLLOWING, false)));
            assertTrue(TimeUtils.equals(new Date(9, Month.JANUARY, 2025),
                    index.maturityDate(new Date(3, Month.JANUARY, 2025))));
            Settings.instance.evaluationDate().equal(new Date(20, Month.DECEMBER, 2024));
            double rolledForward = (Math.exp(0.05 * 6.0 / 360.0) - 1.0) / (6.0 / 365.0);
            assertEquals(0.050715572998271, rolledForward, 1.0e-15);
            assertEquals(rolledForward, index.fixing(rolled, false), 1.0e-12);

            BMAIndex bare = new BMAIndex();
            IllegalArgumentException noCurve = assertThrows(IllegalArgumentException.class,
                    () -> bare.forecastFixing(wednesday));
            assertTrue(noCurve.getMessage().contains("null term structure"));
        } finally {
            IndexManager.Instance.clearHistories();
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static void assertSchedule(Schedule schedule, Date... dates) {
        assertEquals(dates.length, schedule.size());
        for (int i = 0; i < dates.length; i++)
            assertTrue(TimeUtils.equals(dates[i], schedule.date(i)), schedule.date(i).toString());
    }

    private static final class Flag implements Observer {
        private final Set<Observable> registered = new HashSet<>();
        boolean up;

        @Override
        public void registerWith(Observable observable) {
            observable.registerObserver(this);
            registered.add(observable);
        }

        @Override
        public void unregisterWith(Observable o) {
            if (o != null) {
                o.unregisterObserver(this);
                registered.remove(o);
            }
        }

        @Override
        public void unregisterWithAll() {
            for (Observable o : registered)
                o.unregisterObserver(this);
            registered.clear();
        }

        @Override
        public void update() {
            up = true;
        }
    }
}
