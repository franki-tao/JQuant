package termstructures;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.Matrix;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.FlatSmileSection;
import jquant.termstructures.volatility.swaption.ConstantSwaptionVolatility;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityMatrix;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests, <b>not</b> official BOOST ports. They cover the
 * swaption-volatility infrastructure needed by the official {@code cms.cpp}
 * ATM fixture:
 * <ul>
 *   <li>{@link SwaptionVolatilityMatrix} built with the exact official ATM
 *       option/swap tenors and 6x4 matrix, interpolating to the matrix entry
 *       at each node;</li>
 *   <li>{@link ConstantSwaptionVolatility} returning a flat smile section
 *       with the constant volatility for every strike;</li>
 *   <li>quote changes and relinks being visible through the handle-based
 *       matrix constructor.</li>
 * </ul>
 *
 * <p>Dates, tenors and matrix entries are copied verbatim from official
 * {@code test-suite/cms.cpp} CommonVars. The evaluation date is
 * {@code TARGET.adjust(today)} as official, not a fixed historical date.
 */
public class SwaptionVolatilityMatrixTest {

    private static final Calendar CALENDAR = new Target();
    private static final BusinessDayConvention BDC =
            BusinessDayConvention.FOLLOWING;
    private static final DayCounter DC = new Actual365Fixed();
    // Official cms.cpp CommonVars uses Date::todaysDate(), not Date().
    // Java Date() is the empty serial-0 date.

    /** Official cms.cpp ATM option tenors. */
    private static final List<Period> ATM_OPTION_TENORS = List.of(
            new Period(1, TimeUnit.MONTHS),
            new Period(6, TimeUnit.MONTHS),
            new Period(1, TimeUnit.YEARS),
            new Period(5, TimeUnit.YEARS),
            new Period(10, TimeUnit.YEARS),
            new Period(30, TimeUnit.YEARS));

    /** Official cms.cpp ATM swap tenors. */
    private static final List<Period> ATM_SWAP_TENORS = List.of(
            new Period(1, TimeUnit.YEARS),
            new Period(5, TimeUnit.YEARS),
            new Period(10, TimeUnit.YEARS),
            new Period(30, TimeUnit.YEARS));

    /** Official cms.cpp ATM volatilities: rows options, columns swaps. */
    private static final double[][] ATM_VOLATILITIES = {
            {0.1300, 0.1560, 0.1390, 0.1220},
            {0.1440, 0.1580, 0.1460, 0.1260},
            {0.1600, 0.1590, 0.1470, 0.1290},
            {0.1640, 0.1470, 0.1370, 0.1220},
            {0.1400, 0.1300, 0.1250, 0.1100},
            {0.1130, 0.1090, 0.1070, 0.0930}
    };

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        // Official cms.cpp CommonVars.
        Settings.instance.evaluationDate().equal(CALENDAR.adjust(Date.todaysDate()));
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private static Matrix atmMatrix() {
        return new Matrix(ATM_VOLATILITIES);
    }

    private static SwaptionVolatilityMatrix atmStructure() {
        return new SwaptionVolatilityMatrix(CALENDAR, BDC, ATM_OPTION_TENORS,
                ATM_SWAP_TENORS, atmMatrix(), DC);
    }

    @Test
    public void atmMatrixInterpolatesAtEveryNode() {
        SwaptionVolatilityMatrix matrix = atmStructure();

        List<Date> optionDates = matrix.optionDates();
        double[] swapLengths = matrix.swapLengths();
        assertEquals(ATM_OPTION_TENORS.size(), optionDates.size());
        assertEquals(ATM_SWAP_TENORS.size(), swapLengths.length);

        // Option dates are the official calendar-advanced tenors; swap
        // lengths are the tenors in years.
        for (int i = 0; i < ATM_OPTION_TENORS.size(); ++i) {
            Date expected = CALENDAR.advance(matrix.referenceDate(),
                    ATM_OPTION_TENORS.get(i), BDC, false);
            assertTrue(TimeUtils.equals(expected, optionDates.get(i)),
                    "option date " + i + ": expected " + expected
                            + ", got " + optionDates.get(i));
        }
        for (int j = 0; j < ATM_SWAP_TENORS.size(); ++j)
            assertEquals(ATM_SWAP_TENORS.get(j).length(), swapLengths[j],
                    0.0);

        for (int i = 0; i < ATM_VOLATILITIES.length; ++i) {
            Date optionDate = optionDates.get(i);
            double optionTime = matrix.timeFromReference(optionDate);
            for (int j = 0; j < ATM_VOLATILITIES[i].length; ++j) {
                double expected = ATM_VOLATILITIES[i][j];

                // Date + swap length (the pre-existing path).
                assertEquals(expected, matrix.volatility(optionDate,
                        swapLengths[j], 0.05, true), 1.0e-14,
                        "date/swap-length node (" + i + "," + j + ")");

                // Date + swap tenor (the new CMS path).
                assertEquals(expected, matrix.volatility(optionDate,
                        ATM_SWAP_TENORS.get(j), 0.05, true), 1.0e-14,
                        "date/swap-tenor node (" + i + "," + j + ")");

                // Black variance derives from the same volatility.
                assertEquals(expected * expected * optionTime,
                        matrix.blackVariance(optionDate,
                                ATM_SWAP_TENORS.get(j), 0.05, true),
                        1.0e-14,
                        "black variance node (" + i + "," + j + ")");
            }
        }
    }

    @Test
    public void atmMatrixSmileSectionIsFlatAtInterpolatedAtmVol() {
        SwaptionVolatilityMatrix matrix = atmStructure();
        Date optionDate = matrix.optionDates().get(1);
        Period swapTenor = ATM_SWAP_TENORS.get(2);

        double atmVol = matrix.volatility(optionDate, swapTenor, 0.05, true);
        SmileSection smile = matrix.smileSection(optionDate, swapTenor, true);

        assertInstanceOf(FlatSmileSection.class, smile);
        assertEquals(atmVol, smile.volatility(0.01), 1.0e-14);
        assertEquals(atmVol, smile.volatility(0.10), 1.0e-14);
        assertTrue(Double.isNaN(smile.atmLevel()),
                "matrix smile section uses the official null ATM level");
        assertEquals(QL_MIN_REAL, smile.minStrike(), 0.0);
        assertEquals(QL_MAX_REAL, smile.maxStrike(), 0.0);
    }

    @Test
    public void constantSwaptionVolatilitySmileSectionIsFlat() {
        ConstantSwaptionVolatility vol = new ConstantSwaptionVolatility(
                0, CALENDAR, BDC, 0.20, DC, ShiftedLognormal, 0.0);

        Date optionDate = CALENDAR.advance(
                Settings.instance.evaluationDate().Date(),
                new Period(1, TimeUnit.YEARS), BDC, false);

        SmileSection byDate = vol.smileSection(
                optionDate, new Period(5, TimeUnit.YEARS));
        assertInstanceOf(FlatSmileSection.class, byDate);
        assertEquals(0.20, byDate.volatility(0.01), 1.0e-15);
        assertEquals(0.20, byDate.volatility(0.10), 1.0e-15);
        assertTrue(Double.isNaN(byDate.atmLevel()));
        assertEquals(QL_MIN_REAL, byDate.minStrike(), 0.0);
        assertEquals(QL_MAX_REAL, byDate.maxStrike(), 0.0);

        SmileSection byTime = vol.smileSection(1.0, 5.0);
        assertInstanceOf(FlatSmileSection.class, byTime);
        assertEquals(0.20, byTime.volatility(0.01), 1.0e-15);
        assertEquals(0.20, byTime.volatility(0.10), 1.0e-15);
    }

    @Test
    public void handleMatrixSeesQuoteChangesAndRelinks() {
        List<List<Handle<Quote>>> handles = new ArrayList<>();
        for (double[] row : ATM_VOLATILITIES) {
            List<Handle<Quote>> handleRow = new ArrayList<>();
            for (double value : row)
                handleRow.add(new Handle<>(new SimpleQuote(value), true));
            handles.add(handleRow);
        }

        SwaptionVolatilityMatrix matrix = new SwaptionVolatilityMatrix(
                CALENDAR, BDC, ATM_OPTION_TENORS, ATM_SWAP_TENORS, handles, DC);

        Date optionDate = matrix.optionDates().get(3);
        Period swapTenor = ATM_SWAP_TENORS.get(1);
        assertEquals(ATM_VOLATILITIES[3][1],
                matrix.volatility(optionDate, swapTenor, 0.05, true), 1.0e-14);

        // A quote change through the registered Link must be visible.
        SimpleQuote changed = (SimpleQuote) handles.get(3).get(1)
                .currentLink();
        changed.setValue(0.9876);
        assertEquals(0.9876,
                matrix.volatility(optionDate, swapTenor, 0.05, true), 1.0e-14);
        // Other nodes are unaffected.
        assertEquals(ATM_VOLATILITIES[0][1],
                matrix.volatility(matrix.optionDates().get(0),
                        swapTenor, 0.05, true), 1.0e-14);

        // A relink of a RelinkableHandle must also be visible.
        RelinkableHandle<Quote> relinkable = new RelinkableHandle<>(
                new SimpleQuote(0.4321), true);
        handles.get(2).set(2, relinkable);
        SwaptionVolatilityMatrix relinked = new SwaptionVolatilityMatrix(
                CALENDAR, BDC, ATM_OPTION_TENORS, ATM_SWAP_TENORS, handles, DC);
        Date relinkedDate = relinked.optionDates().get(2);
        Period relinkedTenor = ATM_SWAP_TENORS.get(2);
        assertEquals(0.4321, relinked.volatility(relinkedDate,
                relinkedTenor, 0.05, true), 1.0e-14);

        relinkable.linkTo(new SimpleQuote(0.5678), true);
        assertEquals(0.5678, relinked.volatility(relinkedDate,
                relinkedTenor, 0.05, true), 1.0e-14);
    }
}
