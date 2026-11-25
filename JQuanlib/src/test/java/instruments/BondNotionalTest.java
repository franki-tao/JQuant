package instruments;

import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.instruments.Bond;
import jquant.instruments.ZeroCouponBond;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.Month;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests, <b>not</b> official BOOST ports. They pin the Java
 * precondition added to {@link Bond#notional(Date)} for an empty notional
 * schedule, and the official notional-at-date behaviour for a valid bond
 * (empty date means settlement, after the last schedule date means zero,
 * on a redemption date the payment has already occurred).
 *
 * <p>Official {@code Bond::notional} has no guard for an empty schedule; in
 * C++ reading {@code notionalSchedule_.back()} there is undefined behaviour.
 * The Java port instead fails with an {@link IllegalArgumentException} whose
 * message is {@code "empty notional schedule"} rather than returning zero or
 * leaking an index error.
 */
public class BondNotionalTest {

    private static final int SETTLEMENT_DAYS = 3;
    private static final double FACE_AMOUNT = 100.0;
    private static final String EMPTY_SCHEDULE_MESSAGE = "empty notional schedule";

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        // A concrete evaluation date so a valid bond's settlementDate()
        // (and therefore the default notional()) is well defined.
        Settings.instance.evaluationDate().equal(
                new Date(24, Month.APRIL, 2007));
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    @Test
    public void bareBondsRejectEmptyNotionalSchedule() {
        Target calendar = new Target();
        // No cash flows: both constructors leave the notional schedule empty.
        Bond plain = new Bond(SETTLEMENT_DAYS, calendar);
        Bond withIssueDate = new Bond(SETTLEMENT_DAYS, calendar,
                new Date(1, Month.JANUARY, 2007));

        assertEmptyScheduleFailure(plain);
        assertEmptyScheduleFailure(withIssueDate);
    }

    private static void assertEmptyScheduleFailure(Bond bond) {
        Date concreteDate = new Date(15, Month.JUNE, 2008);

        IllegalArgumentException noDate = assertThrows(
                IllegalArgumentException.class, () -> bond.notional());
        assertTrue(noDate.getMessage().contains(EMPTY_SCHEDULE_MESSAGE),
                "notional() should fail with the empty notional schedule "
                        + "message, got: " + noDate.getMessage());

        IllegalArgumentException dated = assertThrows(
                IllegalArgumentException.class, () -> bond.notional(concreteDate));
        assertTrue(dated.getMessage().contains(EMPTY_SCHEDULE_MESSAGE),
                "notional(date) should fail with the empty notional schedule "
                        + "message, got: " + dated.getMessage());
    }

    @Test
    public void liveBondNotionalAtSettlementAndAfterRedemption() {
        Target calendar = new Target();
        Date issueDate = new Date(15, Month.JUNE, 2000);
        Date maturityDate = new Date(15, Month.JUNE, 2010);

        Bond bond = new ZeroCouponBond(SETTLEMENT_DAYS, calendar,
                FACE_AMOUNT, maturityDate,
                BusinessDayConvention.FOLLOWING, FACE_AMOUNT, issueDate);

        Date settlementDate = bond.settlementDate();
        assertTrue(TimeUtils.less(settlementDate, maturityDate),
                "fixture settlement date must precede redemption");

        double atDefault = bond.notional();
        double atNull = bond.notional(null);
        double atToday = bond.notional(new Date());
        double atSettlement = bond.notional(settlementDate);

        // A live bond's notional is its outstanding amount, not zero.
        assertEquals(FACE_AMOUNT, atDefault, 0.0,
                "live bond notional at settlement");
        assertEquals(atDefault, atNull, 0.0,
                "notional(null) must equal notional()");
        assertEquals(atDefault, atToday, 0.0,
                "notional(new Date()) must equal notional()");
        assertEquals(atDefault, atSettlement, 0.0,
                "notional(settlementDate) must equal notional()");

        // The redemption date is a schedule point: the payment has occurred,
        // so the official behaviour is the already-changed (zero) notional.
        Date redemptionDate = bond.redemption().date();
        assertEquals(0.0, bond.notional(redemptionDate), 0.0,
                "notional on the redemption date follows payment-has-occurred");
        // Strictly after the last schedule date the bond has matured.
        assertEquals(0.0, bond.notional(redemptionDate.add(1)), 0.0,
                "notional strictly after maturity must be zero");
    }
}
