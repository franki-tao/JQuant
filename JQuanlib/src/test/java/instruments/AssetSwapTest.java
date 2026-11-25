package instruments;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.PricingEngine;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.BlackIborCouponPricer;
import jquant.cashflows.CashFlows;
import jquant.cashflows.CashflowUtil;
import jquant.cashflows.FixedRateLeg;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.cashflows.SimpleCashFlow;
import jquant.indexes.Estr;
import jquant.indexes.Euribor;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.AssetSwap;
import jquant.instruments.Bond;
import jquant.instruments.FixedRateBond;
import jquant.instruments.FloatingRateBond;
import jquant.instruments.ZeroCouponBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.pricingengines.bond.BondFunctions;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ports of the {@code testConsistency}, {@code testImpliedValue},
 * {@code testMarketASWSpread}, {@code testZSpread},
 * {@code testGenericBondImplied}, {@code testMASWWithGenericBond},
 * {@code testZSpreadWithGenericBond},
 * {@code testSpecializedBondVsGenericBond} and
 * {@code testSpecializedBondVsGenericBondUsingAsw} cases from QuantLib
 * {@code test-suite/assetswap.cpp}.
 *
 * <p>Official {@code DiscountingSwapEngine} maps to the Java generic
 * {@link DiscountingMultiLegSwapEngine}. {@code testConsistency} uses the
 * four-argument constructor (curve, includeSettlementDateFlows,
 * settlementDate, npvDate); the later cases use the official
 * one-argument constructor, which leaves the optional arguments defaulted.
 *
 * <p>The CMS/CmsRateBond branches of the official cases are out of scope
 * and are skipped: Java has no CMS pricer, so no substitute results are
 * asserted.
 */
public class AssetSwapTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        // Official TopLevelFixture clears index histories before
        // SavedSettings restores the evaluation date and other settings.
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    /** Official {@code CommonVars} from test-suite/assetswap.cpp. */
    private static class CommonVars {
        final IborIndex iborIndex;
        final BlackIborCouponPricer pricer;
        final double spread;
        final double nonnullspread;
        final double faceAmount;
        final Compounding compounding;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        CommonVars() {
            spread = 0.0;
            nonnullspread = 0.003;
            faceAmount = 100.0;
            compounding = Compounding.Continuous;
            Date today = new Date(24, Month.APRIL, 2007);
            Settings.instance.evaluationDate().equal(today);

            termStructure.linkTo(
                    new FlatForward(today, 0.05, new Actual365Fixed()), true);
            iborIndex = new Euribor(new Period(Frequency.SEMIANNUAL), termStructure);
            pricer = new BlackIborCouponPricer();
        }
    }

    /**
     * Generic fixed-rate bond of the official generic-bond cases:
     * {@code FixedRateLeg} plus an explicit redemption {@code SimpleCashFlow},
     * passed to the official old {@code Bond} constructor.
     */
    private static Bond genericFixedBond(CommonVars vars, Calendar calendar,
                                         int settlementDays,
                                         Date startDate, Date maturityDate,
                                         Schedule schedule,
                                         double rate, DayCounter dayCounter) {
        List<CashFlow> leg = new FixedRateLeg(schedule)
                .withNotionals(vars.faceAmount)
                .withCouponRates(rate, dayCounter)
                .leg();
        leg.add(new SimpleCashFlow(100.0, calendar.adjust(
                maturityDate, BusinessDayConvention.FOLLOWING)));
        return new Bond(settlementDays, calendar, vars.faceAmount,
                maturityDate, startDate, leg);
    }

    /**
     * Generic Ibor floater of the official generic-bond cases:
     * {@code IborLeg} plus an explicit redemption {@code SimpleCashFlow}.
     */
    private static Bond genericFloatingBond(CommonVars vars, Calendar calendar,
                                            int settlementDays, int fixingDays,
                                            Date startDate, Date maturityDate,
                                            Schedule schedule,
                                            BusinessDayConvention convention,
                                            double spread,
                                            boolean inArrears) {
        List<CashFlow> leg = new IborLeg(schedule, vars.iborIndex)
                .withNotionals(vars.faceAmount)
                .withPaymentDayCounter(new Actual360())
                .withPaymentAdjustment(convention)
                .withFixingDays(fixingDays)
                .withSpreads(spread)
                .inArrears(inArrears)
                .leg();
        leg.add(new SimpleCashFlow(100.0,
                calendar.adjust(maturityDate, convention)));
        return new Bond(settlementDays, calendar, vars.faceAmount,
                maturityDate, startDate, leg);
    }

    /**
     * Generic zero-coupon bond of the official generic-bond cases: a
     * one-element leg with the adjusted redemption.
     */
    private static Bond genericZeroBond(CommonVars vars, Calendar calendar,
                                        int settlementDays,
                                        Date startDate, Date maturityDate) {
        List<CashFlow> leg = new ArrayList<>();
        leg.add(new SimpleCashFlow(100.0, calendar.adjust(
                maturityDate, BusinessDayConvention.FOLLOWING)));
        return new Bond(settlementDays, calendar, vars.faceAmount,
                maturityDate, startDate, leg);
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /** Official {@code "fair clean price doesn't zero the NPV"} check. */
    private static void checkCleanPriceZerosNpv(String kind,
                                                double cleanPrice,
                                                double fairCleanPrice,
                                                double npv,
                                                double tolerance) {
        assertTrue(Math.abs(npv) <= tolerance,
                "\n" + kind + " asset swap fair clean price doesn't zero the NPV: "
                        + "\n  clean price:      " + f(cleanPrice)
                        + "\n  fair clean price: " + f(fairCleanPrice)
                        + "\n  NPV:              " + f(npv)
                        + "\n  tolerance:        " + f(tolerance));
    }

    /** Official {@code "fair clean price doesn't equal input clean price"} check. */
    private static void checkCleanPriceEqualsInput(String kind,
                                                   double inputCleanPrice,
                                                   double fairCleanPrice,
                                                   double npv,
                                                   double tolerance) {
        assertTrue(Math.abs(fairCleanPrice - inputCleanPrice) <= tolerance,
                "\n" + kind + " asset swap fair clean price doesn't equal input "
                        + "clean price at zero NPV: "
                        + "\n  input clean price: " + f(inputCleanPrice)
                        + "\n  fair clean price:  " + f(fairCleanPrice)
                        + "\n  NPV:               " + f(npv)
                        + "\n  tolerance:         " + f(tolerance));
    }

    /** Official {@code "fair spread doesn't equal input spread at zero NPV"} check. */
    private static void checkSpreadEqualsInput(String kind,
                                               double inputSpread,
                                               double fairSpread,
                                               double npv,
                                               double tolerance) {
        assertTrue(Math.abs(fairSpread - inputSpread) <= tolerance,
                "\n" + kind + " asset swap fair spread doesn't equal input spread "
                        + "at zero NPV: "
                        + "\n  input spread: " + f(inputSpread)
                        + "\n  fair spread:  " + f(fairSpread)
                        + "\n  NPV:          " + f(npv)
                        + "\n  tolerance:    " + f(tolerance));
    }

    /** Official {@code "fair spread doesn't zero the NPV"} check. */
    private static void checkSpreadZerosNpv(String kind,
                                            double spread,
                                            double fairSpread,
                                            double npv,
                                            double tolerance) {
        assertTrue(Math.abs(npv) <= tolerance,
                "\n" + kind + " asset swap fair spread doesn't zero the NPV: "
                        + "\n  spread:      " + f(spread)
                        + "\n  fair spread: " + f(fairSpread)
                        + "\n  NPV:         " + f(npv)
                        + "\n  tolerance:   " + f(tolerance));
    }

    /** Official {@code std::scientific << std::setprecision(2)}. */
    private static String sci(double value) {
        return String.format(Locale.ROOT, "%.2e", value);
    }

    /** Official {@code io::rate}: fixed 4 decimals, expressed in percent. */
    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.4f %%", value * 100.0);
    }

    /** Official zero-spread implied-value message (standard spacing). */
    private static void checkZeroSpreadPrice(String kind,
                                             double bondPrice,
                                             double assetSwapPrice,
                                             double error,
                                             double tolerance) {
        assertTrue(error <= tolerance,
                "\nwrong zero spread asset swap price for " + kind + ":"
                        + "\n  bond's clean price:    " + f(bondPrice)
                        + "\n  asset swap fair price: " + f(assetSwapPrice)
                        + "\n  error:                 " + sci(error)
                        + "\n  tolerance:             " + sci(tolerance));
    }

    /** Official error9 message spacing (wider labels). */
    private static void checkZeroSpreadPriceWide(double bondPrice,
                                                 double assetSwapPrice,
                                                 double error,
                                                 double tolerance) {
        assertTrue(error <= tolerance,
                "\nwrong zero spread asset swap price for zero cpn bond:"
                        + "\n  bond's clean price:      " + f(bondPrice)
                        + "\n  asset swap fair price:   " + f(assetSwapPrice)
                        + "\n  error:                   " + sci(error)
                        + "\n  tolerance:               " + sci(tolerance));
    }

    /** Official market-vs-par ASW spread message. */
    private static void checkMarketSpread(String kind,
                                          double marketSpread,
                                          double parSpread,
                                          double error,
                                          double tolerance) {
        assertTrue(error <= tolerance,
                "\nwrong asset swap spreads for " + kind + ":"
                        + "\n  market ASW spread: " + rate(marketSpread)
                        + "\n  par ASW spread:    " + rate(parSpread)
                        + "\n  error:             " + sci(error)
                        + "\n  tolerance:         " + sci(tolerance));
    }

    /** Official testZSpread fixed/FRN message (labels kept as in official). */
    private static void checkZSpreadFixedPrice(double impliedValue,
                                               double cleanPrice,
                                               double error,
                                               double tolerance) {
        assertTrue(error <= tolerance,
                "\nwrong clean price for fixed bond:"
                        + "\n  market asset swap spread: " + f(impliedValue)
                        + "\n  par asset swap spread: " + f(cleanPrice)
                        + "\n  error:                 " + sci(error)
                        + "\n  tolerance:             " + sci(tolerance));
    }

    /** Official testZSpread zero-coupon message. */
    private static void checkZSpreadZeroPrice(double impliedValue,
                                              double cleanPrice,
                                              double error,
                                              double tolerance) {
        assertTrue(error <= tolerance,
                "\nwrong clean price for zero coupon bond:"
                        + "\n  zero cpn implied value: " + f(impliedValue)
                        + "\n  zero cpn price: " + f(cleanPrice)
                        + "\n  error:                 " + sci(error)
                        + "\n  tolerance:             " + sci(tolerance));
    }

    /**
     * Official testMASWWithGenericBond message. Unlike the specialized
     * testMarketASWSpread helper, it uses the long "asset swap spread"
     * labels and wider error/tolerance padding.
     */
    private static void checkGenericMaswSpread(String kind,
                                               double marketSpread,
                                               double parSpread,
                                               double error,
                                               double tolerance) {
        assertTrue(error <= tolerance,
                "\nwrong asset swap spreads for " + kind + ":"
                        + "\n  market asset swap spread: " + rate(marketSpread)
                        + "\n  par asset swap spread:    " + rate(parSpread)
                        + "\n  error:                    " + sci(error)
                        + "\n  tolerance:                " + sci(tolerance));
    }

    /**
     * Official two-bond comparison message used by the specialized-vs-generic
     * cases. The caller passes the official labels and values in the official
     * order, including any official label/value mismatch.
     */
    private static void checkBondPair(String header,
                                      String label1, double value1,
                                      String label2, double value2,
                                      double error, double tolerance,
                                      boolean scientificError) {
        String errorText = scientificError ? sci(error) : f(error);
        String toleranceText = scientificError ? sci(tolerance) : f(tolerance);
        assertTrue(error <= tolerance,
                "\n" + header
                        + "\n  " + label1 + " " + f(value1)
                        + "\n  " + label2 + " " + f(value2)
                        + "\n  error:                 " + errorText
                        + "\n  tolerance:             " + toleranceText);
    }

    @Test
    public void testConsistency() {
        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day

        Schedule bondSchedule = new Schedule(
                new Date(4, Month.JANUARY, 2005),
                new Date(4, Month.JANUARY, 2037),
                new Period(Frequency.ANNUAL),
                bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD,
                false,
                new Date(), new Date());
        Bond bond = new FixedRateBond(settlementDays, vars.faceAmount,
                bondSchedule,
                new double[]{0.04},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(4, Month.JANUARY, 2005));

        boolean payFixedRate = true;
        double bondPrice = 95.0;

        boolean isPar = true;
        AssetSwap parAssetSwap = new AssetSwap(payFixedRate,
                bond, bondPrice,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);

        PricingEngine swapEngine = new DiscountingMultiLegSwapEngine(
                vars.termStructure,
                true,
                bond.settlementDate(),
                Settings.instance.evaluationDate().Date());

        parAssetSwap.setPricingEngine(swapEngine);
        double fairCleanPrice = parAssetSwap.fairCleanPrice();
        double fairSpread = parAssetSwap.fairSpread();

        double tolerance = 1.0e-13;

        AssetSwap assetSwap2 = new AssetSwap(payFixedRate,
                bond, fairCleanPrice,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap2.setPricingEngine(swapEngine);
        checkCleanPriceZerosNpv("par", bondPrice, fairCleanPrice,
                assetSwap2.NPV(), tolerance);
        checkCleanPriceEqualsInput("par", fairCleanPrice,
                assetSwap2.fairCleanPrice(), assetSwap2.NPV(), tolerance);
        checkSpreadEqualsInput("par", vars.spread,
                assetSwap2.fairSpread(), assetSwap2.NPV(), tolerance);

        AssetSwap assetSwap3 = new AssetSwap(payFixedRate,
                bond, bondPrice,
                vars.iborIndex, fairSpread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap3.setPricingEngine(swapEngine);
        checkSpreadZerosNpv("par", vars.spread, fairSpread,
                assetSwap3.NPV(), tolerance);
        checkCleanPriceEqualsInput("par", bondPrice,
                assetSwap3.fairCleanPrice(), assetSwap3.NPV(), tolerance);
        checkSpreadEqualsInput("par", fairSpread,
                assetSwap3.fairSpread(), assetSwap3.NPV(), tolerance);

        // let's change the npv date
        swapEngine = new DiscountingMultiLegSwapEngine(
                vars.termStructure,
                true,
                bond.settlementDate(),
                bond.settlementDate());

        parAssetSwap.setPricingEngine(swapEngine);
        // fair clean price and fair spread should not change
        assertTrue(Math.abs(parAssetSwap.fairCleanPrice() - fairCleanPrice) <= tolerance,
                "\npar asset swap fair clean price changed with NpvDate:"
                        + "\n expected clean price: " + f(fairCleanPrice)
                        + "\n fair clean price:     " + f(parAssetSwap.fairCleanPrice())
                        + "\n tolerance:            " + f(tolerance));
        assertTrue(Math.abs(parAssetSwap.fairSpread() - fairSpread) <= tolerance,
                "\npar asset swap fair spread changed with NpvDate:"
                        + "\n  expected spread: " + f(fairSpread)
                        + "\n  fair spread:     " + f(parAssetSwap.fairSpread())
                        + "\n  tolerance:       " + f(tolerance));

        assetSwap2 = new AssetSwap(payFixedRate,
                bond, fairCleanPrice,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap2.setPricingEngine(swapEngine);
        checkCleanPriceZerosNpv("par", bondPrice, fairCleanPrice,
                assetSwap2.NPV(), tolerance);
        checkCleanPriceEqualsInput("par", fairCleanPrice,
                assetSwap2.fairCleanPrice(), assetSwap2.NPV(), tolerance);
        checkSpreadEqualsInput("par", vars.spread,
                assetSwap2.fairSpread(), assetSwap2.NPV(), tolerance);

        assetSwap3 = new AssetSwap(payFixedRate,
                bond, bondPrice,
                vars.iborIndex, fairSpread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap3.setPricingEngine(swapEngine);
        checkSpreadZerosNpv("par", vars.spread, fairSpread,
                assetSwap3.NPV(), tolerance);
        checkCleanPriceEqualsInput("par", bondPrice,
                assetSwap3.fairCleanPrice(), assetSwap3.NPV(), tolerance);
        checkSpreadEqualsInput("par", fairSpread,
                assetSwap3.fairSpread(), assetSwap3.NPV(), tolerance);

        // using overnight index

        Estr overnight = new Estr(vars.termStructure);
        Schedule overnightSchedule = new Schedule(
                bond.settlementDate(),
                bond.maturityDate(),
                new Period(6, TimeUnit.MONTHS),
                overnight.fixingCalendar(),
                overnight.businessDayConvention(),
                overnight.businessDayConvention(),
                DateGenerationRule.BACKWARD,
                false,
                new Date(), new Date());

        parAssetSwap = new AssetSwap(payFixedRate,
                bond, bondPrice,
                overnight, vars.spread,
                overnightSchedule,
                overnight.dayCounter(),
                isPar);

        swapEngine = new DiscountingMultiLegSwapEngine(
                vars.termStructure,
                true,
                bond.settlementDate(),
                Settings.instance.evaluationDate().Date());

        parAssetSwap.setPricingEngine(swapEngine);
        fairCleanPrice = parAssetSwap.fairCleanPrice();
        fairSpread = parAssetSwap.fairSpread();

        assetSwap2 = new AssetSwap(payFixedRate,
                bond, fairCleanPrice,
                overnight, vars.spread,
                overnightSchedule,
                overnight.dayCounter(),
                isPar);
        assetSwap2.setPricingEngine(swapEngine);
        checkCleanPriceZerosNpv("par", bondPrice, fairCleanPrice,
                assetSwap2.NPV(), tolerance);
        checkCleanPriceEqualsInput("par", fairCleanPrice,
                assetSwap2.fairCleanPrice(), assetSwap2.NPV(), tolerance);
        checkSpreadEqualsInput("par", vars.spread,
                assetSwap2.fairSpread(), assetSwap2.NPV(), tolerance);

        assetSwap3 = new AssetSwap(payFixedRate,
                bond, bondPrice,
                overnight, fairSpread,
                overnightSchedule,
                overnight.dayCounter(),
                isPar);
        assetSwap3.setPricingEngine(swapEngine);
        checkSpreadZerosNpv("par", vars.spread, fairSpread,
                assetSwap3.NPV(), tolerance);
        checkCleanPriceEqualsInput("par", bondPrice,
                assetSwap3.fairCleanPrice(), assetSwap3.NPV(), tolerance);
        checkSpreadEqualsInput("par", fairSpread,
                assetSwap3.fairSpread(), assetSwap3.NPV(), tolerance);

        // now market asset swap

        isPar = false;
        AssetSwap mktAssetSwap = new AssetSwap(payFixedRate,
                bond, bondPrice,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);

        swapEngine = new DiscountingMultiLegSwapEngine(
                vars.termStructure,
                true,
                bond.settlementDate(),
                Settings.instance.evaluationDate().Date());

        mktAssetSwap.setPricingEngine(swapEngine);
        fairCleanPrice = mktAssetSwap.fairCleanPrice();
        fairSpread = mktAssetSwap.fairSpread();

        AssetSwap assetSwap4 = new AssetSwap(payFixedRate,
                bond, fairCleanPrice,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap4.setPricingEngine(swapEngine);
        checkCleanPriceZerosNpv("market", bondPrice, fairCleanPrice,
                assetSwap4.NPV(), tolerance);
        checkCleanPriceEqualsInput("market", fairCleanPrice,
                assetSwap4.fairCleanPrice(), assetSwap4.NPV(), tolerance);
        checkSpreadEqualsInput("market", vars.spread,
                assetSwap4.fairSpread(), assetSwap4.NPV(), tolerance);

        AssetSwap assetSwap5 = new AssetSwap(payFixedRate,
                bond, bondPrice,
                vars.iborIndex, fairSpread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap5.setPricingEngine(swapEngine);
        checkSpreadZerosNpv("market", vars.spread, fairSpread,
                assetSwap5.NPV(), tolerance);
        checkCleanPriceEqualsInput("market", bondPrice,
                assetSwap5.fairCleanPrice(), assetSwap5.NPV(), tolerance);
        checkSpreadEqualsInput("market", fairSpread,
                assetSwap5.fairSpread(), assetSwap5.NPV(), tolerance);

        // let's change the npv date
        swapEngine = new DiscountingMultiLegSwapEngine(
                vars.termStructure,
                true,
                bond.settlementDate(),
                bond.settlementDate());

        mktAssetSwap.setPricingEngine(swapEngine);
        // fair clean price and fair spread should not change
        assertTrue(Math.abs(mktAssetSwap.fairCleanPrice() - fairCleanPrice) <= tolerance,
                "\nmarket asset swap fair clean price changed with NpvDate:"
                        + "\n  expected clean price: " + f(fairCleanPrice)
                        + "\n  fair clean price:  " + f(mktAssetSwap.fairCleanPrice())
                        + "\n  tolerance:         " + f(tolerance));
        assertTrue(Math.abs(mktAssetSwap.fairSpread() - fairSpread) <= tolerance,
                "\nmarket asset swap fair spread changed with NpvDate:"
                        + "\n  expected spread: " + f(fairSpread)
                        + "\n  fair spread:  " + f(mktAssetSwap.fairSpread())
                        + "\n  tolerance:    " + f(tolerance));

        assetSwap4 = new AssetSwap(payFixedRate,
                bond, fairCleanPrice,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap4.setPricingEngine(swapEngine);
        checkCleanPriceZerosNpv("market", bondPrice, fairCleanPrice,
                assetSwap4.NPV(), tolerance);
        checkCleanPriceEqualsInput("market", fairCleanPrice,
                assetSwap4.fairCleanPrice(), assetSwap4.NPV(), tolerance);
        checkSpreadEqualsInput("market", vars.spread,
                assetSwap4.fairSpread(), assetSwap4.NPV(), tolerance);

        assetSwap5 = new AssetSwap(payFixedRate,
                bond, bondPrice,
                vars.iborIndex, fairSpread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                isPar);
        assetSwap5.setPricingEngine(swapEngine);
        checkSpreadZerosNpv("market", vars.spread, fairSpread,
                assetSwap5.NPV(), tolerance);
        checkCleanPriceEqualsInput("market", bondPrice,
                assetSwap5.fairCleanPrice(), assetSwap5.NPV(), tolerance);
        checkSpreadEqualsInput("market", fairSpread,
                assetSwap5.fairSpread(), assetSwap5.NPV(), tolerance);

        // using overnight index

        mktAssetSwap = new AssetSwap(payFixedRate,
                bond, bondPrice,
                overnight, vars.spread,
                overnightSchedule,
                overnight.dayCounter(),
                isPar);

        swapEngine = new DiscountingMultiLegSwapEngine(
                vars.termStructure,
                true,
                bond.settlementDate(),
                Settings.instance.evaluationDate().Date());

        mktAssetSwap.setPricingEngine(swapEngine);
        fairCleanPrice = mktAssetSwap.fairCleanPrice();
        fairSpread = mktAssetSwap.fairSpread();

        assetSwap4 = new AssetSwap(payFixedRate,
                bond, fairCleanPrice,
                overnight, vars.spread,
                overnightSchedule,
                overnight.dayCounter(),
                isPar);
        assetSwap4.setPricingEngine(swapEngine);
        checkCleanPriceZerosNpv("market", bondPrice, fairCleanPrice,
                assetSwap4.NPV(), tolerance);
        checkCleanPriceEqualsInput("market", fairCleanPrice,
                assetSwap4.fairCleanPrice(), assetSwap4.NPV(), tolerance);
        checkSpreadEqualsInput("market", vars.spread,
                assetSwap4.fairSpread(), assetSwap4.NPV(), tolerance);

        assetSwap5 = new AssetSwap(payFixedRate,
                bond, bondPrice,
                overnight, fairSpread,
                overnightSchedule,
                overnight.dayCounter(),
                isPar);
        assetSwap5.setPricingEngine(swapEngine);
        checkSpreadZerosNpv("market", vars.spread, fairSpread,
                assetSwap5.NPV(), tolerance);
        checkCleanPriceEqualsInput("market", bondPrice,
                assetSwap5.fairCleanPrice(), assetSwap5.NPV(), tolerance);
        checkSpreadEqualsInput("market", fairSpread,
                assetSwap5.fairSpread(), assetSwap5.NPV(), tolerance);
    }

    /**
     * Official {@code testImpliedValue} (Fixed + Floating + Zero branches).
     *
     * <p>Official {@code DiscountingSwapEngine(termStructure)} maps to the
     * one-argument Java {@link DiscountingMultiLegSwapEngine}; both leave the
     * settlement/NPV dates empty (the curve reference date) and the
     * include-settlement-date-flows flag to the settings. The CMS branches are
     * out of DON-17 scope (Java has no {@code CmsRateBond}).
     */
    @Test
    public void testImpliedValue() {
        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean payFixedRate = true;
        boolean parAssetSwap = true;
        boolean inArrears = false;

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day

        Schedule fixedBondSchedule1 = new Schedule(
                new Date(4, Month.JANUARY, 2005),
                new Date(4, Month.JANUARY, 2037),
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond1 = new FixedRateBond(settlementDays, vars.faceAmount,
                fixedBondSchedule1,
                new double[]{0.04},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(4, Month.JANUARY, 2005));

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);
        PricingEngine swapEngine =
                new DiscountingMultiLegSwapEngine(vars.termStructure);
        fixedBond1.setPricingEngine(bondEngine);

        double fixedBondPrice1 = fixedBond1.cleanPrice();
        AssetSwap fixedBondAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondAssetSwapPrice1 = fixedBondAssetSwap1.fairCleanPrice();
        double tolerance = 1.0e-13;

        // for indexed coupons the float leg will not be par, therefore we
        // have to relax the tolerance - note that the fair clean price is
        // correct though, only we can not compare it to the bond price
        // directly. The same kind of discrepancy will occur for a multi
        // curve set up, which we do not test here.
        double tolerance2 = usingAtParCoupons ? 1.0e-13 : 1.0e-2;

        double error1 = Math.abs(fixedBondAssetSwapPrice1 - fixedBondPrice1);
        checkZeroSpreadPrice("fixed bond", fixedBondPrice1,
                fixedBondAssetSwapPrice1, error1, tolerance2);

        // Fixed Underlying bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day

        Schedule fixedBondSchedule2 = new Schedule(
                new Date(5, Month.FEBRUARY, 2005),
                new Date(5, Month.FEBRUARY, 2019),
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond2 = new FixedRateBond(settlementDays, vars.faceAmount,
                fixedBondSchedule2,
                new double[]{0.05},
                new Thirty360(Thirty360.Convention.BondBasis),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(5, Month.FEBRUARY, 2005));

        fixedBond2.setPricingEngine(bondEngine);

        double fixedBondPrice2 = fixedBond2.cleanPrice();
        AssetSwap fixedBondAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondAssetSwapPrice2 = fixedBondAssetSwap2.fairCleanPrice();
        double error2 = Math.abs(fixedBondAssetSwapPrice2 - fixedBondPrice2);
        checkZeroSpreadPrice("fixed bond", fixedBondPrice2,
                fixedBondAssetSwapPrice2, error2, tolerance2);

        // FRN Underlying bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day

        Schedule floatingBondSchedule1 = new Schedule(
                new Date(29, Month.SEPTEMBER, 2003),
                new Date(29, Month.SEPTEMBER, 2013),
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());

        Bond floatingBond1 = new FloatingRateBond(settlementDays, vars.faceAmount,
                floatingBondSchedule1,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0056),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(29, Month.SEPTEMBER, 2003));

        floatingBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        double floatingBondPrice1 = floatingBond1.cleanPrice();
        AssetSwap floatingBondAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondAssetSwapPrice1 =
                floatingBondAssetSwap1.fairCleanPrice();
        double error3 = Math.abs(floatingBondAssetSwapPrice1 - floatingBondPrice1);
        checkZeroSpreadPrice("floater", floatingBondPrice1,
                floatingBondAssetSwapPrice1, error3, tolerance2);

        // FRN Underlying bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day

        Schedule floatingBondSchedule2 = new Schedule(
                new Date(24, Month.SEPTEMBER, 2004),
                new Date(24, Month.SEPTEMBER, 2018),
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond2 = new FloatingRateBond(settlementDays, vars.faceAmount,
                floatingBondSchedule2,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0025),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(24, Month.SEPTEMBER, 2004));

        floatingBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);
        double currentCoupon = 0.04013 + 0.0025;
        double floatingCurrentCoupon = CashFlows.nextCouponRate(
                floatingBond2.cashflows(), false,
                floatingBond2.settlementDate());
        double error4 = Math.abs(floatingCurrentCoupon - currentCoupon);
        assertTrue(error4 <= tolerance,
                "\nwrong current coupon is returned for floater bond:"
                        + "\n  bond's calculated current coupon:      "
                        + f(currentCoupon)
                        + "\n  current coupon asked to the bond: "
                        + f(floatingCurrentCoupon)
                        + "\n  error:                 " + sci(error4)
                        + "\n  tolerance:             " + sci(tolerance));

        double floatingBondPrice2 = floatingBond2.cleanPrice();
        AssetSwap floatingBondAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondAssetSwapPrice2 =
                floatingBondAssetSwap2.fairCleanPrice();
        double error5 = Math.abs(floatingBondAssetSwapPrice2 - floatingBondPrice2);
        checkZeroSpreadPrice("floater", floatingBondPrice2,
                floatingBondAssetSwapPrice2, error5, tolerance2);

        // CMS Underlying bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day

        Bond zeroCpnBond1 = new ZeroCouponBond(settlementDays, bondCalendar,
                vars.faceAmount,
                new Date(20, Month.DECEMBER, 2015),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(19, Month.DECEMBER, 1985));

        zeroCpnBond1.setPricingEngine(bondEngine);

        double zeroCpnBondPrice1 = zeroCpnBond1.cleanPrice();
        AssetSwap zeroCpnAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondAssetSwapPrice1 =
                zeroCpnAssetSwap1.fairCleanPrice();
        // Official source typo: error8 compares the CMS prices while its
        // message describes this zero-coupon bond. CMS is out of scope, so
        // implement the check the official message describes.
        double error8 = Math.abs(zeroCpnBondAssetSwapPrice1 - zeroCpnBondPrice1);
        checkZeroSpreadPrice("zero cpn bond", zeroCpnBondPrice1,
                zeroCpnBondAssetSwapPrice1, error8, tolerance2);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity occurs on a business day

        Bond zeroCpnBond2 = new ZeroCouponBond(settlementDays, bondCalendar,
                vars.faceAmount,
                new Date(17, Month.FEBRUARY, 2028),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(17, Month.FEBRUARY, 1998));

        zeroCpnBond2.setPricingEngine(bondEngine);

        double zeroCpnBondPrice2 = zeroCpnBond2.cleanPrice();
        AssetSwap zeroCpnAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondAssetSwapPrice2 =
                zeroCpnAssetSwap2.fairCleanPrice();
        // Official source typo as for error8 above.
        double error9 = Math.abs(zeroCpnBondAssetSwapPrice2 - zeroCpnBondPrice2);
        checkZeroSpreadPriceWide(zeroCpnBondPrice2,
                zeroCpnBondAssetSwapPrice2, error9, tolerance2);
    }

    /**
     * Official {@code testMarketASWSpread} (Fixed + Floating + Zero branches).
     *
     * <p>Same one-argument mapping of the official
     * {@code DiscountingSwapEngine(termStructure)} as in
     * {@link #testImpliedValue()}. CMS branches are out of DON-17 scope.
     */
    @Test
    public void testMarketASWSpread() {
        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean payFixedRate = true;
        boolean parAssetSwap = true;
        boolean mktAssetSwap = false;
        boolean inArrears = false;

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day

        Schedule fixedBondSchedule1 = new Schedule(
                new Date(4, Month.JANUARY, 2005),
                new Date(4, Month.JANUARY, 2037),
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond1 = new FixedRateBond(settlementDays, vars.faceAmount,
                fixedBondSchedule1,
                new double[]{0.04},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(4, Month.JANUARY, 2005));

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);
        PricingEngine swapEngine =
                new DiscountingMultiLegSwapEngine(vars.termStructure);
        fixedBond1.setPricingEngine(bondEngine);

        double fixedBondMktPrice1 = 89.22; // market price observed on 7th June 2007
        double fixedBondMktFullPrice1 =
                fixedBondMktPrice1 + fixedBond1.accruedAmount();
        AssetSwap fixedBondParAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondParAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondParAssetSwapSpread1 =
                fixedBondParAssetSwap1.fairSpread();
        AssetSwap fixedBondMktAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        fixedBondMktAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondMktAssetSwapSpread1 =
                fixedBondMktAssetSwap1.fairSpread();

        // see comment above
        double tolerance2 = usingAtParCoupons ? 1.0e-13 : 1.0e-4;

        double error1 = Math.abs(fixedBondMktAssetSwapSpread1
                - 100 * fixedBondParAssetSwapSpread1 / fixedBondMktFullPrice1);
        checkMarketSpread("fixed bond", fixedBondMktAssetSwapSpread1,
                fixedBondParAssetSwapSpread1, error1, tolerance2);

        // Fixed Underlying bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day

        Schedule fixedBondSchedule2 = new Schedule(
                new Date(5, Month.FEBRUARY, 2005),
                new Date(5, Month.FEBRUARY, 2019),
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond2 = new FixedRateBond(settlementDays, vars.faceAmount,
                fixedBondSchedule2,
                new double[]{0.05},
                new Thirty360(Thirty360.Convention.BondBasis),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(5, Month.FEBRUARY, 2005));

        fixedBond2.setPricingEngine(bondEngine);

        double fixedBondMktPrice2 = 99.98; // market price observed on 7th June 2007
        double fixedBondMktFullPrice2 =
                fixedBondMktPrice2 + fixedBond2.accruedAmount();
        AssetSwap fixedBondParAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondParAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondParAssetSwapSpread2 =
                fixedBondParAssetSwap2.fairSpread();
        AssetSwap fixedBondMktAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        fixedBondMktAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondMktAssetSwapSpread2 =
                fixedBondMktAssetSwap2.fairSpread();
        double error2 = Math.abs(fixedBondMktAssetSwapSpread2
                - 100 * fixedBondParAssetSwapSpread2 / fixedBondMktFullPrice2);
        checkMarketSpread("fixed bond", fixedBondMktAssetSwapSpread2,
                fixedBondParAssetSwapSpread2, error2, tolerance2);

        // FRN Underlying bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day

        Schedule floatingBondSchedule1 = new Schedule(
                new Date(29, Month.SEPTEMBER, 2003),
                new Date(29, Month.SEPTEMBER, 2013),
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());

        Bond floatingBond1 = new FloatingRateBond(settlementDays, vars.faceAmount,
                floatingBondSchedule1,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0056),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(29, Month.SEPTEMBER, 2003));

        floatingBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        // market price observed on 7th June 2007
        double floatingBondMktPrice1 = 101.64;
        double floatingBondMktFullPrice1 =
                floatingBondMktPrice1 + floatingBond1.accruedAmount();
        AssetSwap floatingBondParAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondParAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondParAssetSwapSpread1 =
                floatingBondParAssetSwap1.fairSpread();
        AssetSwap floatingBondMktAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        floatingBondMktAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondMktAssetSwapSpread1 =
                floatingBondMktAssetSwap1.fairSpread();
        double error3 = Math.abs(floatingBondMktAssetSwapSpread1
                - 100 * floatingBondParAssetSwapSpread1 / floatingBondMktFullPrice1);
        checkMarketSpread("floating bond", floatingBondMktAssetSwapSpread1,
                floatingBondParAssetSwapSpread1, error3, tolerance2);

        // FRN Underlying bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day

        Schedule floatingBondSchedule2 = new Schedule(
                new Date(24, Month.SEPTEMBER, 2004),
                new Date(24, Month.SEPTEMBER, 2018),
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond2 = new FloatingRateBond(settlementDays, vars.faceAmount,
                floatingBondSchedule2,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0025),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(24, Month.SEPTEMBER, 2004));

        floatingBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);
        // market price observed on 7th June 2007
        double floatingBondMktPrice2 = 101.248;
        double floatingBondMktFullPrice2 =
                floatingBondMktPrice2 + floatingBond2.accruedAmount();
        AssetSwap floatingBondParAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondParAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondParAssetSwapSpread2 =
                floatingBondParAssetSwap2.fairSpread();
        AssetSwap floatingBondMktAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        floatingBondMktAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondMktAssetSwapSpread2 =
                floatingBondMktAssetSwap2.fairSpread();
        double error4 = Math.abs(floatingBondMktAssetSwapSpread2
                - 100 * floatingBondParAssetSwapSpread2 / floatingBondMktFullPrice2);
        checkMarketSpread("floating bond", floatingBondMktAssetSwapSpread2,
                floatingBondParAssetSwapSpread2, error4, tolerance2);

        // CMS Underlying bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day

        Bond zeroCpnBond1 = new ZeroCouponBond(settlementDays, bondCalendar,
                vars.faceAmount,
                new Date(20, Month.DECEMBER, 2015),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(19, Month.DECEMBER, 1985));

        zeroCpnBond1.setPricingEngine(bondEngine);

        // market price observed on 12th June 2007
        double zeroCpnBondMktPrice1 = 70.436;
        double zeroCpnBondMktFullPrice1 =
                zeroCpnBondMktPrice1 + zeroCpnBond1.accruedAmount();
        AssetSwap zeroCpnBondParAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondParAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondParAssetSwapSpread1 =
                zeroCpnBondParAssetSwap1.fairSpread();
        AssetSwap zeroCpnBondMktAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        zeroCpnBondMktAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondMktAssetSwapSpread1 =
                zeroCpnBondMktAssetSwap1.fairSpread();
        double error7 = Math.abs(zeroCpnBondMktAssetSwapSpread1
                - 100 * zeroCpnBondParAssetSwapSpread1 / zeroCpnBondMktFullPrice1);
        checkMarketSpread("zero cpn bond", zeroCpnBondMktAssetSwapSpread1,
                zeroCpnBondParAssetSwapSpread1, error7, tolerance2);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity occurs on a business day

        Bond zeroCpnBond2 = new ZeroCouponBond(settlementDays, bondCalendar,
                vars.faceAmount,
                new Date(17, Month.FEBRUARY, 2028),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(17, Month.FEBRUARY, 1998));

        zeroCpnBond2.setPricingEngine(bondEngine);

        // Real zeroCpnBondPrice2 = zeroCpnBond2->cleanPrice();

        // market price observed on 12th June 2007
        double zeroCpnBondMktPrice2 = 35.160;
        double zeroCpnBondMktFullPrice2 =
                zeroCpnBondMktPrice2 + zeroCpnBond2.accruedAmount();
        AssetSwap zeroCpnBondParAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondParAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondParAssetSwapSpread2 =
                zeroCpnBondParAssetSwap2.fairSpread();
        AssetSwap zeroCpnBondMktAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        zeroCpnBondMktAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondMktAssetSwapSpread2 =
                zeroCpnBondMktAssetSwap2.fairSpread();
        double error8 = Math.abs(zeroCpnBondMktAssetSwapSpread2
                - 100 * zeroCpnBondParAssetSwapSpread2 / zeroCpnBondMktFullPrice2);
        checkMarketSpread("zero cpn bond", zeroCpnBondMktAssetSwapSpread2,
                zeroCpnBondParAssetSwapSpread2, error8, tolerance2);
    }

    /**
     * Official {@code testZSpread} (Fixed + Floating + Zero branches).
     *
     * <p>No AssetSwap is built here: each bond's engine clean price is
     * compared with
     * {@link BondFunctions#cleanPrice(Bond, YieldTermStructure, double,
     * Compounding, Frequency, Date)} at the official null Z-spread
     * ({@code vars.spread == 0.0}). The CMS branches are out of DON-17
     * scope (Java has no {@code CmsRateBond}).
     */
    @Test
    public void testZSpread() {
        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean inArrears = false;

        // Fixed bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day

        Schedule fixedBondSchedule1 = new Schedule(
                new Date(4, Month.JANUARY, 2005),
                new Date(4, Month.JANUARY, 2037),
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond1 = new FixedRateBond(settlementDays, vars.faceAmount,
                fixedBondSchedule1,
                new double[]{0.04},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(4, Month.JANUARY, 2005));

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);
        fixedBond1.setPricingEngine(bondEngine);

        double fixedBondImpliedValue1 = fixedBond1.cleanPrice();
        Date fixedBondSettlementDate1 = fixedBond1.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YC...
        double fixedBondCleanPrice1 = BondFunctions.cleanPrice(
                fixedBond1, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                fixedBondSettlementDate1);
        double tolerance = 1.0e-13;
        double error1 = Math.abs(fixedBondImpliedValue1 - fixedBondCleanPrice1);
        checkZSpreadFixedPrice(fixedBondImpliedValue1, fixedBondCleanPrice1,
                error1, tolerance);

        // Fixed bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day

        Schedule fixedBondSchedule2 = new Schedule(
                new Date(5, Month.FEBRUARY, 2005),
                new Date(5, Month.FEBRUARY, 2019),
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond2 = new FixedRateBond(settlementDays, vars.faceAmount,
                fixedBondSchedule2,
                new double[]{0.05},
                new Thirty360(Thirty360.Convention.BondBasis),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(5, Month.FEBRUARY, 2005));

        fixedBond2.setPricingEngine(bondEngine);

        double fixedBondImpliedValue2 = fixedBond2.cleanPrice();
        Date fixedBondSettlementDate2 = fixedBond2.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double fixedBondCleanPrice2 = BondFunctions.cleanPrice(
                fixedBond2, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                fixedBondSettlementDate2);
        double error3 = Math.abs(fixedBondImpliedValue2 - fixedBondCleanPrice2);
        checkZSpreadFixedPrice(fixedBondImpliedValue2, fixedBondCleanPrice2,
                error3, tolerance);

        // FRN bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day

        Schedule floatingBondSchedule1 = new Schedule(
                new Date(29, Month.SEPTEMBER, 2003),
                new Date(29, Month.SEPTEMBER, 2013),
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());

        Bond floatingBond1 = new FloatingRateBond(settlementDays, vars.faceAmount,
                floatingBondSchedule1,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0056),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(29, Month.SEPTEMBER, 2003));

        floatingBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        double floatingBondImpliedValue1 = floatingBond1.cleanPrice();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double floatingBondCleanPrice1 = BondFunctions.cleanPrice(
                floatingBond1, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.SEMIANNUAL,
                fixedBondSettlementDate1);
        double error5 = Math.abs(floatingBondImpliedValue1 - floatingBondCleanPrice1);
        checkZSpreadFixedPrice(floatingBondImpliedValue1, floatingBondCleanPrice1,
                error5, tolerance);

        // FRN bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day

        Schedule floatingBondSchedule2 = new Schedule(
                new Date(24, Month.SEPTEMBER, 2004),
                new Date(24, Month.SEPTEMBER, 2018),
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond2 = new FloatingRateBond(settlementDays, vars.faceAmount,
                floatingBondSchedule2,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0025),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(24, Month.SEPTEMBER, 2004));

        floatingBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);
        double floatingBondImpliedValue2 = floatingBond2.cleanPrice();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double floatingBondCleanPrice2 = BondFunctions.cleanPrice(
                floatingBond2, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.SEMIANNUAL,
                fixedBondSettlementDate1);
        double error7 = Math.abs(floatingBondImpliedValue2 - floatingBondCleanPrice2);
        // Official failure text says "fixed bond" for this FRN branch.
        checkZSpreadFixedPrice(floatingBondImpliedValue2, floatingBondCleanPrice2,
                error7, tolerance);

        // CMS bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those (official error9 and
        // error11) branches are skipped.

        // Zero-Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day

        Bond zeroCpnBond1 = new ZeroCouponBond(settlementDays, bondCalendar,
                vars.faceAmount,
                new Date(20, Month.DECEMBER, 2015),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(19, Month.DECEMBER, 1985));

        zeroCpnBond1.setPricingEngine(bondEngine);

        double zeroCpnBondImpliedValue1 = zeroCpnBond1.cleanPrice();
        Date zeroCpnBondSettlementDate1 = zeroCpnBond1.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double zeroCpnBondCleanPrice1 = BondFunctions.cleanPrice(
                zeroCpnBond1, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                zeroCpnBondSettlementDate1);
        double error13 =
                Math.abs(zeroCpnBondImpliedValue1 - zeroCpnBondCleanPrice1);
        checkZSpreadZeroPrice(zeroCpnBondImpliedValue1, zeroCpnBondCleanPrice1,
                error13, tolerance);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity doesn't occur on a business day

        Bond zeroCpnBond2 = new ZeroCouponBond(settlementDays, bondCalendar,
                vars.faceAmount,
                new Date(17, Month.FEBRUARY, 2028),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(17, Month.FEBRUARY, 1998));

        zeroCpnBond2.setPricingEngine(bondEngine);

        double zeroCpnBondImpliedValue2 = zeroCpnBond2.cleanPrice();
        Date zeroCpnBondSettlementDate2 = zeroCpnBond2.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double zeroCpnBondCleanPrice2 = BondFunctions.cleanPrice(
                zeroCpnBond2, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                zeroCpnBondSettlementDate2);
        double error15 =
                Math.abs(zeroCpnBondImpliedValue2 - zeroCpnBondCleanPrice2);
        checkZSpreadZeroPrice(zeroCpnBondImpliedValue2, zeroCpnBondCleanPrice2,
                error15, tolerance);
    }

    /**
     * Official {@code testGenericBondImplied} (Fixed + Floating + Zero).
     * The generic legs are built with {@code FixedRateLeg}/{@code IborLeg} and
     * an explicit redemption; CMS branches are out of DON-17 scope.
     */
    @Test
    public void testGenericBondImplied() {
        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean payFixedRate = true;
        boolean parAssetSwap = true;
        boolean inArrears = false;

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);
        PricingEngine swapEngine =
                new DiscountingMultiLegSwapEngine(vars.termStructure);

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day
        Date fixedBondStartDate1 = new Date(4, Month.JANUARY, 2005);
        Date fixedBondMaturityDate1 = new Date(4, Month.JANUARY, 2037);
        Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond1 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate1, fixedBondMaturityDate1, fixedBondSchedule1,
                0.04, new ActualActual(ActualActual.Convention.ISDA));
        fixedBond1.setPricingEngine(bondEngine);

        double fixedBondPrice1 = fixedBond1.cleanPrice();
        AssetSwap fixedBondAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondAssetSwapPrice1 = fixedBondAssetSwap1.fairCleanPrice();
        double tolerance = 1.0e-13;

        // see comment above
        double tolerance2 = usingAtParCoupons ? 1.0e-13 : 1.0e-2;

        double error1 = Math.abs(fixedBondAssetSwapPrice1 - fixedBondPrice1);
        checkZeroSpreadPrice("fixed bond", fixedBondPrice1,
                fixedBondAssetSwapPrice1, error1, tolerance2);

        // Fixed Underlying bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day
        Date fixedBondStartDate2 = new Date(5, Month.FEBRUARY, 2005);
        Date fixedBondMaturityDate2 = new Date(5, Month.FEBRUARY, 2019);
        Schedule fixedBondSchedule2 = new Schedule(
                fixedBondStartDate2, fixedBondMaturityDate2,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond2 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate2, fixedBondMaturityDate2, fixedBondSchedule2,
                0.05, new Thirty360(Thirty360.Convention.BondBasis));
        fixedBond2.setPricingEngine(bondEngine);

        double fixedBondPrice2 = fixedBond2.cleanPrice();
        AssetSwap fixedBondAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondAssetSwapPrice2 = fixedBondAssetSwap2.fairCleanPrice();
        double error2 = Math.abs(fixedBondAssetSwapPrice2 - fixedBondPrice2);
        checkZeroSpreadPrice("fixed bond", fixedBondPrice2,
                fixedBondAssetSwapPrice2, error2, tolerance2);

        // FRN Underlying bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day
        Date floatingBondStartDate1 = new Date(29, Month.SEPTEMBER, 2003);
        Date floatingBondMaturityDate1 = new Date(29, Month.SEPTEMBER, 2013);
        Schedule floatingBondSchedule1 = new Schedule(
                floatingBondStartDate1, floatingBondMaturityDate1,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond1 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate1, floatingBondMaturityDate1,
                floatingBondSchedule1, BusinessDayConvention.FOLLOWING,
                0.0056, inArrears);
        floatingBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        double floatingBondPrice1 = floatingBond1.cleanPrice();
        AssetSwap floatingBondAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondAssetSwapPrice1 =
                floatingBondAssetSwap1.fairCleanPrice();
        double error3 = Math.abs(floatingBondAssetSwapPrice1 - floatingBondPrice1);
        checkZeroSpreadPrice("floater", floatingBondPrice1,
                floatingBondAssetSwapPrice1, error3, tolerance2);

        // FRN Underlying bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day
        Date floatingBondStartDate2 = new Date(24, Month.SEPTEMBER, 2004);
        Date floatingBondMaturityDate2 = new Date(24, Month.SEPTEMBER, 2018);
        Schedule floatingBondSchedule2 = new Schedule(
                floatingBondStartDate2, floatingBondMaturityDate2,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond2 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate2, floatingBondMaturityDate2,
                floatingBondSchedule2, BusinessDayConvention.MODIFIED_FOLLOWING,
                0.0025, inArrears);
        floatingBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);
        double currentCoupon = 0.04013 + 0.0025;
        double floatingCurrentCoupon = CashFlows.nextCouponRate(
                floatingBond2.cashflows(), false,
                floatingBond2.settlementDate());
        double error4 = Math.abs(floatingCurrentCoupon - currentCoupon);
        assertTrue(error4 <= tolerance,
                "\nwrong current coupon is returned for floater bond:"
                        + "\n  bond's calculated current coupon:      "
                        + f(currentCoupon)
                        + "\n  current coupon asked to the bond: "
                        + f(floatingCurrentCoupon)
                        + "\n  error:                 " + sci(error4)
                        + "\n  tolerance:             " + sci(tolerance));

        double floatingBondPrice2 = floatingBond2.cleanPrice();
        AssetSwap floatingBondAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondAssetSwapPrice2 =
                floatingBondAssetSwap2.fairCleanPrice();
        double error5 = Math.abs(floatingBondAssetSwapPrice2 - floatingBondPrice2);
        checkZeroSpreadPrice("floater", floatingBondPrice2,
                floatingBondAssetSwapPrice2, error5, tolerance2);

        // CMS Underlying bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day
        Date zeroCpnBondStartDate1 = new Date(19, Month.DECEMBER, 1985);
        Date zeroCpnBondMaturityDate1 = new Date(20, Month.DECEMBER, 2015);
        Bond zeroCpnBond1 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate1, zeroCpnBondMaturityDate1);
        zeroCpnBond1.setPricingEngine(bondEngine);

        double zeroCpnBondPrice1 = zeroCpnBond1.cleanPrice();
        AssetSwap zeroCpnAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondAssetSwapPrice1 =
                zeroCpnAssetSwap1.fairCleanPrice();
        double error8 = Math.abs(zeroCpnBondAssetSwapPrice1 - zeroCpnBondPrice1);
        checkZeroSpreadPrice("zero cpn bond", zeroCpnBondPrice1,
                zeroCpnBondAssetSwapPrice1, error8, tolerance2);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity occurs on a business day
        Date zeroCpnBondStartDate2 = new Date(17, Month.FEBRUARY, 1998);
        Date zeroCpnBondMaturityDate2 = new Date(17, Month.FEBRUARY, 2028);
        Bond zeroCpnBond2 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate2, zeroCpnBondMaturityDate2);
        zeroCpnBond2.setPricingEngine(bondEngine);

        double zeroCpnBondPrice2 = zeroCpnBond2.cleanPrice();
        AssetSwap zeroCpnAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondAssetSwapPrice2 =
                zeroCpnAssetSwap2.fairCleanPrice();
        double error9 = Math.abs(zeroCpnBondAssetSwapPrice2 - zeroCpnBondPrice2);
        checkZeroSpreadPrice("zero cpn bond", zeroCpnBondPrice2,
                zeroCpnBondAssetSwapPrice2, error9, tolerance2);
    }

    /**
     * Official {@code testMASWWithGenericBond} (Fixed + Floating + Zero).
     */
    @Test
    public void testMASWWithGenericBond() {
        boolean usingAtParCoupons =
                IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean payFixedRate = true;
        boolean parAssetSwap = true;
        boolean mktAssetSwap = false;
        boolean inArrears = false;

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);
        PricingEngine swapEngine =
                new DiscountingMultiLegSwapEngine(vars.termStructure);

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day
        Date fixedBondStartDate1 = new Date(4, Month.JANUARY, 2005);
        Date fixedBondMaturityDate1 = new Date(4, Month.JANUARY, 2037);
        Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond1 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate1, fixedBondMaturityDate1, fixedBondSchedule1,
                0.04, new ActualActual(ActualActual.Convention.ISDA));
        fixedBond1.setPricingEngine(bondEngine);

        double fixedBondMktPrice1 = 89.22; // market price observed on 7th June 2007
        double fixedBondMktFullPrice1 =
                fixedBondMktPrice1 + fixedBond1.accruedAmount();
        AssetSwap fixedBondParAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondParAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondParAssetSwapSpread1 =
                fixedBondParAssetSwap1.fairSpread();
        AssetSwap fixedBondMktAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        fixedBondMktAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondMktAssetSwapSpread1 =
                fixedBondMktAssetSwap1.fairSpread();

        // see comment above
        double tolerance2 = usingAtParCoupons ? 1.0e-13 : 1.0e-4;

        double error1 = Math.abs(fixedBondMktAssetSwapSpread1
                - 100 * fixedBondParAssetSwapSpread1 / fixedBondMktFullPrice1);
        checkGenericMaswSpread("fixed bond", fixedBondMktAssetSwapSpread1,
                fixedBondParAssetSwapSpread1, error1, tolerance2);

        // Fixed Underlying bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day
        Date fixedBondStartDate2 = new Date(5, Month.FEBRUARY, 2005);
        Date fixedBondMaturityDate2 = new Date(5, Month.FEBRUARY, 2019);
        Schedule fixedBondSchedule2 = new Schedule(
                fixedBondStartDate2, fixedBondMaturityDate2,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond2 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate2, fixedBondMaturityDate2, fixedBondSchedule2,
                0.05, new Thirty360(Thirty360.Convention.BondBasis));
        fixedBond2.setPricingEngine(bondEngine);

        double fixedBondMktPrice2 = 99.98; // market price observed on 7th June 2007
        double fixedBondMktFullPrice2 =
                fixedBondMktPrice2 + fixedBond2.accruedAmount();
        AssetSwap fixedBondParAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondParAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondParAssetSwapSpread2 =
                fixedBondParAssetSwap2.fairSpread();
        AssetSwap fixedBondMktAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        fixedBondMktAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondMktAssetSwapSpread2 =
                fixedBondMktAssetSwap2.fairSpread();
        double error2 = Math.abs(fixedBondMktAssetSwapSpread2
                - 100 * fixedBondParAssetSwapSpread2 / fixedBondMktFullPrice2);
        checkGenericMaswSpread("fixed bond", fixedBondMktAssetSwapSpread2,
                fixedBondParAssetSwapSpread2, error2, tolerance2);

        // FRN Underlying bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day
        Date floatingBondStartDate1 = new Date(29, Month.SEPTEMBER, 2003);
        Date floatingBondMaturityDate1 = new Date(29, Month.SEPTEMBER, 2013);
        Schedule floatingBondSchedule1 = new Schedule(
                floatingBondStartDate1, floatingBondMaturityDate1,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond1 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate1, floatingBondMaturityDate1,
                floatingBondSchedule1, BusinessDayConvention.FOLLOWING,
                0.0056, inArrears);
        floatingBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        // market price observed on 7th June 2007
        double floatingBondMktPrice1 = 101.64;
        double floatingBondMktFullPrice1 =
                floatingBondMktPrice1 + floatingBond1.accruedAmount();
        AssetSwap floatingBondParAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondParAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondParAssetSwapSpread1 =
                floatingBondParAssetSwap1.fairSpread();
        AssetSwap floatingBondMktAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        floatingBondMktAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondMktAssetSwapSpread1 =
                floatingBondMktAssetSwap1.fairSpread();
        double error3 = Math.abs(floatingBondMktAssetSwapSpread1
                - 100 * floatingBondParAssetSwapSpread1 / floatingBondMktFullPrice1);
        checkGenericMaswSpread("floating bond", floatingBondMktAssetSwapSpread1,
                floatingBondParAssetSwapSpread1, error3, tolerance2);

        // FRN Underlying bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day
        Date floatingBondStartDate2 = new Date(24, Month.SEPTEMBER, 2004);
        Date floatingBondMaturityDate2 = new Date(24, Month.SEPTEMBER, 2018);
        Schedule floatingBondSchedule2 = new Schedule(
                floatingBondStartDate2, floatingBondMaturityDate2,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond2 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate2, floatingBondMaturityDate2,
                floatingBondSchedule2, BusinessDayConvention.MODIFIED_FOLLOWING,
                0.0025, inArrears);
        floatingBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);
        // market price observed on 7th June 2007
        double floatingBondMktPrice2 = 101.248;
        double floatingBondMktFullPrice2 =
                floatingBondMktPrice2 + floatingBond2.accruedAmount();
        AssetSwap floatingBondParAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondParAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondParAssetSwapSpread2 =
                floatingBondParAssetSwap2.fairSpread();
        AssetSwap floatingBondMktAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        floatingBondMktAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondMktAssetSwapSpread2 =
                floatingBondMktAssetSwap2.fairSpread();
        double error4 = Math.abs(floatingBondMktAssetSwapSpread2
                - 100 * floatingBondParAssetSwapSpread2 / floatingBondMktFullPrice2);
        checkGenericMaswSpread("floating bond", floatingBondMktAssetSwapSpread2,
                floatingBondParAssetSwapSpread2, error4, tolerance2);

        // CMS Underlying bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day
        Date zeroCpnBondStartDate1 = new Date(19, Month.DECEMBER, 1985);
        Date zeroCpnBondMaturityDate1 = new Date(20, Month.DECEMBER, 2015);
        Bond zeroCpnBond1 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate1, zeroCpnBondMaturityDate1);
        zeroCpnBond1.setPricingEngine(bondEngine);

        // market price observed on 12th June 2007
        double zeroCpnBondMktPrice1 = 70.436;
        double zeroCpnBondMktFullPrice1 =
                zeroCpnBondMktPrice1 + zeroCpnBond1.accruedAmount();
        AssetSwap zeroCpnBondParAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondParAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondParAssetSwapSpread1 =
                zeroCpnBondParAssetSwap1.fairSpread();
        AssetSwap zeroCpnBondMktAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        zeroCpnBondMktAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondMktAssetSwapSpread1 =
                zeroCpnBondMktAssetSwap1.fairSpread();
        double error7 = Math.abs(zeroCpnBondMktAssetSwapSpread1
                - 100 * zeroCpnBondParAssetSwapSpread1 / zeroCpnBondMktFullPrice1);
        checkGenericMaswSpread("zero cpn bond", zeroCpnBondMktAssetSwapSpread1,
                zeroCpnBondParAssetSwapSpread1, error7, tolerance2);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity occurs on a business day
        Date zeroCpnBondStartDate2 = new Date(17, Month.FEBRUARY, 1998);
        Date zeroCpnBondMaturityDate2 = new Date(17, Month.FEBRUARY, 2028);
        Bond zeroCpnBond2 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate2, zeroCpnBondMaturityDate2);
        zeroCpnBond2.setPricingEngine(bondEngine);

        // Real zeroCpnBondPrice2 = zeroCpnBond2->cleanPrice();
        // market price observed on 12th June 2007
        double zeroCpnBondMktPrice2 = 35.160;
        double zeroCpnBondMktFullPrice2 =
                zeroCpnBondMktPrice2 + zeroCpnBond2.accruedAmount();
        AssetSwap zeroCpnBondParAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondParAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondParAssetSwapSpread2 =
                zeroCpnBondParAssetSwap2.fairSpread();
        AssetSwap zeroCpnBondMktAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                mktAssetSwap);
        zeroCpnBondMktAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondMktAssetSwapSpread2 =
                zeroCpnBondMktAssetSwap2.fairSpread();
        double error8 = Math.abs(zeroCpnBondMktAssetSwapSpread2
                - 100 * zeroCpnBondParAssetSwapSpread2 / zeroCpnBondMktFullPrice2);
        checkGenericMaswSpread("zero cpn bond", zeroCpnBondMktAssetSwapSpread2,
                zeroCpnBondParAssetSwapSpread2, error8, tolerance2);
    }

    /**
     * Official {@code testZSpreadWithGenericBond} (Fixed + Floating + Zero).
     * The FRN checks pass the first fixed bond's settlement date, as official.
     */
    @Test
    public void testZSpreadWithGenericBond() {
        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean inArrears = false;

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day
        Date fixedBondStartDate1 = new Date(4, Month.JANUARY, 2005);
        Date fixedBondMaturityDate1 = new Date(4, Month.JANUARY, 2037);
        Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond1 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate1, fixedBondMaturityDate1, fixedBondSchedule1,
                0.04, new ActualActual(ActualActual.Convention.ISDA));
        fixedBond1.setPricingEngine(bondEngine);

        double fixedBondImpliedValue1 = fixedBond1.cleanPrice();
        Date fixedBondSettlementDate1 = fixedBond1.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double fixedBondCleanPrice1 = BondFunctions.cleanPrice(
                fixedBond1, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                fixedBondSettlementDate1);
        double tolerance = 1.0e-13;
        double error1 = Math.abs(fixedBondImpliedValue1 - fixedBondCleanPrice1);
        checkZSpreadFixedPrice(fixedBondImpliedValue1, fixedBondCleanPrice1,
                error1, tolerance);

        // Fixed Underlying bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day
        Date fixedBondStartDate2 = new Date(5, Month.FEBRUARY, 2005);
        Date fixedBondMaturityDate2 = new Date(5, Month.FEBRUARY, 2019);
        Schedule fixedBondSchedule2 = new Schedule(
                fixedBondStartDate2, fixedBondMaturityDate2,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond fixedBond2 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate2, fixedBondMaturityDate2, fixedBondSchedule2,
                0.05, new Thirty360(Thirty360.Convention.BondBasis));
        fixedBond2.setPricingEngine(bondEngine);

        double fixedBondImpliedValue2 = fixedBond2.cleanPrice();
        Date fixedBondSettlementDate2 = fixedBond2.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve

        double fixedBondCleanPrice2 = BondFunctions.cleanPrice(
                fixedBond2, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                fixedBondSettlementDate2);
        double error3 = Math.abs(fixedBondImpliedValue2 - fixedBondCleanPrice2);
        checkZSpreadFixedPrice(fixedBondImpliedValue2, fixedBondCleanPrice2,
                error3, tolerance);

        // FRN Underlying bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day
        Date floatingBondStartDate1 = new Date(29, Month.SEPTEMBER, 2003);
        Date floatingBondMaturityDate1 = new Date(29, Month.SEPTEMBER, 2013);
        Schedule floatingBondSchedule1 = new Schedule(
                floatingBondStartDate1, floatingBondMaturityDate1,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond1 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate1, floatingBondMaturityDate1,
                floatingBondSchedule1, BusinessDayConvention.FOLLOWING,
                0.0056, inArrears);
        floatingBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        double floatingBondImpliedValue1 = floatingBond1.cleanPrice();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double floatingBondCleanPrice1 = BondFunctions.cleanPrice(
                floatingBond1, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.SEMIANNUAL,
                fixedBondSettlementDate1);
        double error5 = Math.abs(floatingBondImpliedValue1 - floatingBondCleanPrice1);
        checkZSpreadFixedPrice(floatingBondImpliedValue1, floatingBondCleanPrice1,
                error5, tolerance);

        // FRN Underlying bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day
        Date floatingBondStartDate2 = new Date(24, Month.SEPTEMBER, 2004);
        Date floatingBondMaturityDate2 = new Date(24, Month.SEPTEMBER, 2018);
        Schedule floatingBondSchedule2 = new Schedule(
                floatingBondStartDate2, floatingBondMaturityDate2,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        Bond floatingBond2 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate2, floatingBondMaturityDate2,
                floatingBondSchedule2, BusinessDayConvention.MODIFIED_FOLLOWING,
                0.0025, inArrears);
        floatingBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);
        double floatingBondImpliedValue2 = floatingBond2.cleanPrice();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double floatingBondCleanPrice2 = BondFunctions.cleanPrice(
                floatingBond2, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.SEMIANNUAL,
                fixedBondSettlementDate1);
        double error7 = Math.abs(floatingBondImpliedValue2 - floatingBondCleanPrice2);
        // Official failure text says "fixed bond" for this FRN branch.
        checkZSpreadFixedPrice(floatingBondImpliedValue2, floatingBondCleanPrice2,
                error7, tolerance);

        // CMS Underlying bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day
        Date zeroCpnBondStartDate1 = new Date(19, Month.DECEMBER, 1985);
        Date zeroCpnBondMaturityDate1 = new Date(20, Month.DECEMBER, 2015);
        Bond zeroCpnBond1 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate1, zeroCpnBondMaturityDate1);
        zeroCpnBond1.setPricingEngine(bondEngine);

        double zeroCpnBondImpliedValue1 = zeroCpnBond1.cleanPrice();
        Date zeroCpnBondSettlementDate1 = zeroCpnBond1.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double zeroCpnBondCleanPrice1 = BondFunctions.cleanPrice(
                zeroCpnBond1, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                zeroCpnBondSettlementDate1);
        double error13 =
                Math.abs(zeroCpnBondImpliedValue1 - zeroCpnBondCleanPrice1);
        checkZSpreadZeroPrice(zeroCpnBondImpliedValue1, zeroCpnBondCleanPrice1,
                error13, tolerance);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity occurs on a business day
        Date zeroCpnBondStartDate2 = new Date(17, Month.FEBRUARY, 1998);
        Date zeroCpnBondMaturityDate2 = new Date(17, Month.FEBRUARY, 2028);
        Bond zeroCpnBond2 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate2, zeroCpnBondMaturityDate2);
        zeroCpnBond2.setPricingEngine(bondEngine);

        double zeroCpnBondImpliedValue2 = zeroCpnBond2.cleanPrice();
        Date zeroCpnBondSettlementDate2 = zeroCpnBond2.settlementDate();
        // standard market conventions:
        // bond's frequency + coumpounding and daycounter of the YieldCurve
        double zeroCpnBondCleanPrice2 = BondFunctions.cleanPrice(
                zeroCpnBond2, vars.termStructure.currentLink(), vars.spread,
                vars.compounding, Frequency.ANNUAL,
                zeroCpnBondSettlementDate2);
        double error15 =
                Math.abs(zeroCpnBondImpliedValue2 - zeroCpnBondCleanPrice2);
        checkZSpreadZeroPrice(zeroCpnBondImpliedValue2, zeroCpnBondCleanPrice2,
                error15, tolerance);
    }

    /**
     * Official {@code testSpecializedBondVsGenericBond} (Fixed + Floating +
     * Zero). The official clean/dirty messages have swapped or mistyped
     * labels; the official label/value pairing and wording are kept.
     */
    @Test
    public void testSpecializedBondVsGenericBond() {
        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean inArrears = false;

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day
        Date fixedBondStartDate1 = new Date(4, Month.JANUARY, 2005);
        Date fixedBondMaturityDate1 = new Date(4, Month.JANUARY, 2037);
        Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond fixedBond1 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate1, fixedBondMaturityDate1, fixedBondSchedule1,
                0.04, new ActualActual(ActualActual.Convention.ISDA));
        fixedBond1.setPricingEngine(bondEngine);

        // equivalent specialized fixed rate bond
        Bond fixedSpecializedBond1 = new FixedRateBond(settlementDays,
                vars.faceAmount, fixedBondSchedule1,
                new double[]{0.04},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(4, Month.JANUARY, 2005));
        fixedSpecializedBond1.setPricingEngine(bondEngine);

        double fixedBondTheoValue1 = fixedBond1.cleanPrice();
        double fixedSpecializedBondTheoValue1 =
                fixedSpecializedBond1.cleanPrice();
        double tolerance = 1.0e-13;
        double error1 =
                Math.abs(fixedBondTheoValue1 - fixedSpecializedBondTheoValue1);
        checkBondPair("wrong clean price for fixed bond:",
                "specialized fixed rate bond's theo clean price:",
                fixedBondTheoValue1,
                "generic equivalent bond's theo clean price:",
                fixedSpecializedBondTheoValue1, error1, tolerance, true);

        double fixedBondTheoDirty1 =
                fixedBondTheoValue1 + fixedBond1.accruedAmount();
        double fixedSpecializedTheoDirty1 =
                fixedSpecializedBondTheoValue1
                        + fixedSpecializedBond1.accruedAmount();
        double error2 =
                Math.abs(fixedBondTheoDirty1 - fixedSpecializedTheoDirty1);
        checkBondPair("wrong dirty price for fixed bond:",
                "specialized fixed rate bond's theo dirty price:",
                fixedBondTheoDirty1,
                "generic equivalent bond's theo dirty price:",
                fixedSpecializedTheoDirty1, error2, tolerance, true);

        // Fixed Underlying bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day
        Date fixedBondStartDate2 = new Date(5, Month.FEBRUARY, 2005);
        Date fixedBondMaturityDate2 = new Date(5, Month.FEBRUARY, 2019);
        Schedule fixedBondSchedule2 = new Schedule(
                fixedBondStartDate2, fixedBondMaturityDate2,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond fixedBond2 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate2, fixedBondMaturityDate2, fixedBondSchedule2,
                0.05, new Thirty360(Thirty360.Convention.BondBasis));
        fixedBond2.setPricingEngine(bondEngine);

        // equivalent specialized fixed rate bond
        Bond fixedSpecializedBond2 = new FixedRateBond(settlementDays,
                vars.faceAmount, fixedBondSchedule2,
                new double[]{0.05},
                new Thirty360(Thirty360.Convention.BondBasis),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(5, Month.FEBRUARY, 2005));
        fixedSpecializedBond2.setPricingEngine(bondEngine);

        double fixedBondTheoValue2 = fixedBond2.cleanPrice();
        double fixedSpecializedBondTheoValue2 =
                fixedSpecializedBond2.cleanPrice();

        double error3 =
                Math.abs(fixedBondTheoValue2 - fixedSpecializedBondTheoValue2);
        // Official message omits the scientific manipulators for error3.
        checkBondPair("wrong clean price for fixed bond:",
                "specialized fixed rate bond's theo clean price:",
                fixedBondTheoValue2,
                "generic equivalent bond's theo clean price:",
                fixedSpecializedBondTheoValue2, error3, tolerance, false);

        double fixedBondTheoDirty2 =
                fixedBondTheoValue2 + fixedBond2.accruedAmount();
        double fixedSpecializedBondTheoDirty2 =
                fixedSpecializedBondTheoValue2
                        + fixedSpecializedBond2.accruedAmount();

        double error4 =
                Math.abs(fixedBondTheoDirty2 - fixedSpecializedBondTheoDirty2);
        checkBondPair("wrong dirty price for fixed bond:",
                "specialized fixed rate bond's dirty clean price:",
                fixedBondTheoDirty2,
                "generic equivalent bond's theo dirty price:",
                fixedSpecializedBondTheoDirty2, error4, tolerance, true);

        // FRN Underlying bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day
        Date floatingBondStartDate1 = new Date(29, Month.SEPTEMBER, 2003);
        Date floatingBondMaturityDate1 = new Date(29, Month.SEPTEMBER, 2013);
        Schedule floatingBondSchedule1 = new Schedule(
                floatingBondStartDate1, floatingBondMaturityDate1,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond floatingBond1 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate1, floatingBondMaturityDate1,
                floatingBondSchedule1, BusinessDayConvention.FOLLOWING,
                0.0056, inArrears);
        floatingBond1.setPricingEngine(bondEngine);

        // equivalent specialized floater
        Bond floatingSpecializedBond1 = new FloatingRateBond(settlementDays,
                vars.faceAmount, floatingBondSchedule1,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0056),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(29, Month.SEPTEMBER, 2003));
        floatingSpecializedBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        CashflowUtil.setCouponPricer(
                floatingSpecializedBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        double floatingBondTheoValue1 = floatingBond1.cleanPrice();
        double floatingSpecializedBondTheoValue1 =
                floatingSpecializedBond1.cleanPrice();

        double error5 = Math.abs(floatingBondTheoValue1
                - floatingSpecializedBondTheoValue1);
        checkBondPair("wrong clean price for fixed bond:",
                "generic fixed rate bond's theo clean price:",
                floatingBondTheoValue1,
                "equivalent specialized bond's theo clean price:",
                floatingSpecializedBondTheoValue1, error5, tolerance, true);

        double floatingBondTheoDirty1 =
                floatingBondTheoValue1 + floatingBond1.accruedAmount();
        double floatingSpecializedBondTheoDirty1 =
                floatingSpecializedBondTheoValue1
                        + floatingSpecializedBond1.accruedAmount();
        double error6 = Math.abs(floatingBondTheoDirty1
                - floatingSpecializedBondTheoDirty1);
        checkBondPair("wrong dirty price for frn bond:",
                "generic frn bond's dirty clean price:",
                floatingBondTheoDirty1,
                "equivalent specialized bond's theo dirty price:",
                floatingSpecializedBondTheoDirty1, error6, tolerance, true);

        // FRN Underlying bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day
        Date floatingBondStartDate2 = new Date(24, Month.SEPTEMBER, 2004);
        Date floatingBondMaturityDate2 = new Date(24, Month.SEPTEMBER, 2018);
        Schedule floatingBondSchedule2 = new Schedule(
                floatingBondStartDate2, floatingBondMaturityDate2,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond floatingBond2 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate2, floatingBondMaturityDate2,
                floatingBondSchedule2, BusinessDayConvention.MODIFIED_FOLLOWING,
                0.0025, inArrears);
        floatingBond2.setPricingEngine(bondEngine);

        // equivalent specialized floater
        Bond floatingSpecializedBond2 = new FloatingRateBond(settlementDays,
                vars.faceAmount, floatingBondSchedule2,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0025),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(24, Month.SEPTEMBER, 2004));
        floatingSpecializedBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        CashflowUtil.setCouponPricer(
                floatingSpecializedBond2.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);

        double floatingBondTheoValue2 = floatingBond2.cleanPrice();
        double floatingSpecializedBondTheoValue2 =
                floatingSpecializedBond2.cleanPrice();

        double error7 =
                Math.abs(floatingBondTheoValue2 - floatingSpecializedBondTheoValue2);
        checkBondPair("wrong clean price for floater bond:",
                "generic floater bond's theo clean price:",
                floatingBondTheoValue2,
                "equivalent specialized bond's theo clean price:",
                floatingSpecializedBondTheoValue2, error7, tolerance, true);

        double floatingBondTheoDirty2 =
                floatingBondTheoValue2 + floatingBond2.accruedAmount();
        double floatingSpecializedTheoDirty2 =
                floatingSpecializedBondTheoValue2
                        + floatingSpecializedBond2.accruedAmount();

        double error8 =
                Math.abs(floatingBondTheoDirty2 - floatingSpecializedTheoDirty2);
        checkBondPair("wrong dirty price for floater bond:",
                "generic floater bond's theo dirty price:",
                floatingBondTheoDirty2,
                "equivalent specialized  bond's theo dirty price:",
                floatingSpecializedTheoDirty2, error8, tolerance, true);

        // CMS Underlying bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day
        Date zeroCpnBondStartDate1 = new Date(19, Month.DECEMBER, 1985);
        Date zeroCpnBondMaturityDate1 = new Date(20, Month.DECEMBER, 2015);
        // generic bond
        Bond zeroCpnBond1 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate1, zeroCpnBondMaturityDate1);
        zeroCpnBond1.setPricingEngine(bondEngine);

        // specialized zerocpn bond
        Bond zeroCpnSpecializedBond1 = new ZeroCouponBond(settlementDays,
                bondCalendar, vars.faceAmount,
                new Date(20, Month.DECEMBER, 2015),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(19, Month.DECEMBER, 1985));
        zeroCpnSpecializedBond1.setPricingEngine(bondEngine);

        double zeroCpnBondTheoValue1 = zeroCpnBond1.cleanPrice();
        double zeroCpnSpecializedBondTheoValue1 =
                zeroCpnSpecializedBond1.cleanPrice();

        double error13 =
                Math.abs(zeroCpnBondTheoValue1 - zeroCpnSpecializedBondTheoValue1);
        checkBondPair("wrong clean price for zero coupon bond:",
                "generic zero bond's clean price:",
                zeroCpnBondTheoValue1,
                "specialized zero bond's clean price:",
                zeroCpnSpecializedBondTheoValue1, error13, tolerance, true);

        double zeroCpnBondTheoDirty1 =
                zeroCpnBondTheoValue1 + zeroCpnBond1.accruedAmount();
        double zeroCpnSpecializedBondTheoDirty1 =
                zeroCpnSpecializedBondTheoValue1
                        + zeroCpnSpecializedBond1.accruedAmount();
        double error14 =
                Math.abs(zeroCpnBondTheoDirty1 - zeroCpnSpecializedBondTheoDirty1);
        checkBondPair("wrong dirty price for zero bond:",
                "generic zerocpn bond's dirty price:",
                zeroCpnBondTheoDirty1,
                "specialized zerocpn bond's clean price:",
                zeroCpnSpecializedBondTheoDirty1, error14, tolerance, true);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity occurs on a business day
        Date zeroCpnBondStartDate2 = new Date(17, Month.FEBRUARY, 1998);
        Date zeroCpnBondMaturityDate2 = new Date(17, Month.FEBRUARY, 2028);
        // generic bond
        Bond zeroCpnBond2 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate2, zeroCpnBondMaturityDate2);
        zeroCpnBond2.setPricingEngine(bondEngine);

        // specialized zerocpn bond
        Bond zeroCpnSpecializedBond2 = new ZeroCouponBond(settlementDays,
                bondCalendar, vars.faceAmount,
                new Date(17, Month.FEBRUARY, 2028),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(17, Month.FEBRUARY, 1998));
        zeroCpnSpecializedBond2.setPricingEngine(bondEngine);

        double zeroCpnBondTheoValue2 = zeroCpnBond2.cleanPrice();
        double zeroCpnSpecializedBondTheoValue2 =
                zeroCpnSpecializedBond2.cleanPrice();

        double error15 =
                Math.abs(zeroCpnBondTheoValue2 - zeroCpnSpecializedBondTheoValue2);
        checkBondPair("wrong clean price for zero coupon bond:",
                "generic zerocpn bond's clean price:",
                zeroCpnBondTheoValue2,
                "specialized zerocpn bond's clean price:",
                zeroCpnSpecializedBondTheoValue2, error15, tolerance, true);

        double zeroCpnBondTheoDirty2 =
                zeroCpnBondTheoValue2 + zeroCpnBond2.accruedAmount();
        double zeroCpnSpecializedBondTheoDirty2 =
                zeroCpnSpecializedBondTheoValue2
                        + zeroCpnSpecializedBond2.accruedAmount();

        double error16 =
                Math.abs(zeroCpnBondTheoDirty2 - zeroCpnSpecializedBondTheoDirty2);
        checkBondPair("wrong dirty price for zero coupon bond:",
                "generic zerocpn bond's dirty price:",
                zeroCpnBondTheoDirty2,
                "specialized zerocpn bond's dirty price:",
                zeroCpnSpecializedBondTheoDirty2, error16, tolerance, true);
    }

    /**
     * Official {@code testSpecializedBondVsGenericBondUsingAsw} (Fixed +
     * Floating + Zero). Two asset swaps are compared per bond: one at the
     * bond clean price with the non-null spread, and one at the official
     * market executable price with the null spread. Official mistyped
     * headers and label spacing are kept.
     */
    @Test
    public void testSpecializedBondVsGenericBondUsingAsw() {
        CommonVars vars = new CommonVars();

        Calendar bondCalendar = new Target();
        int settlementDays = 3;
        int fixingDays = 2;
        boolean payFixedRate = true;
        boolean parAssetSwap = true;
        boolean inArrears = false;

        PricingEngine bondEngine = new DiscountingBondEngine(vars.termStructure);
        PricingEngine swapEngine =
                new DiscountingMultiLegSwapEngine(vars.termStructure);

        // Fixed bond (Isin: DE0001135275 DBR 4 01/04/37)
        // maturity doesn't occur on a business day
        Date fixedBondStartDate1 = new Date(4, Month.JANUARY, 2005);
        Date fixedBondMaturityDate1 = new Date(4, Month.JANUARY, 2037);
        Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond fixedBond1 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate1, fixedBondMaturityDate1, fixedBondSchedule1,
                0.04, new ActualActual(ActualActual.Convention.ISDA));
        fixedBond1.setPricingEngine(bondEngine);

        // equivalent specialized fixed rate bond
        Bond fixedSpecializedBond1 = new FixedRateBond(settlementDays,
                vars.faceAmount, fixedBondSchedule1,
                new double[]{0.04},
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(4, Month.JANUARY, 2005));
        fixedSpecializedBond1.setPricingEngine(bondEngine);

        double fixedBondPrice1 = fixedBond1.cleanPrice();
        double fixedSpecializedBondPrice1 =
                fixedSpecializedBond1.cleanPrice();
        AssetSwap fixedBondAssetSwap1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondPrice1,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap1.setPricingEngine(swapEngine);
        AssetSwap fixedSpecializedBondAssetSwap1 = new AssetSwap(payFixedRate,
                fixedSpecializedBond1, fixedSpecializedBondPrice1,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedSpecializedBondAssetSwap1.setPricingEngine(swapEngine);
        double fixedBondAssetSwapPrice1 = fixedBondAssetSwap1.fairCleanPrice();
        double fixedSpecializedBondAssetSwapPrice1 =
                fixedSpecializedBondAssetSwap1.fairCleanPrice();
        double tolerance = 1.0e-13;
        double error1 = Math.abs(fixedBondAssetSwapPrice1
                - fixedSpecializedBondAssetSwapPrice1);
        checkBondPair("wrong clean price for fixed bond:",
                "generic  fixed rate bond's  clean price:",
                fixedBondAssetSwapPrice1,
                "equivalent specialized bond's clean price:",
                fixedSpecializedBondAssetSwapPrice1,
                error1, tolerance, true);

        // market executable price as of 4th sept 2007
        double fixedBondMktPrice1 = 91.832;
        AssetSwap fixedBondASW1 = new AssetSwap(payFixedRate,
                fixedBond1, fixedBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondASW1.setPricingEngine(swapEngine);
        AssetSwap fixedSpecializedBondASW1 = new AssetSwap(payFixedRate,
                fixedSpecializedBond1, fixedBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedSpecializedBondASW1.setPricingEngine(swapEngine);
        double fixedBondASWSpread1 = fixedBondASW1.fairSpread();
        double fixedSpecializedBondASWSpread1 =
                fixedSpecializedBondASW1.fairSpread();
        double error2 =
                Math.abs(fixedBondASWSpread1 - fixedSpecializedBondASWSpread1);
        checkBondPair("wrong asw spread  for fixed bond:",
                "generic  fixed rate bond's  asw spread:",
                fixedBondASWSpread1,
                "equivalent specialized bond's asw spread:",
                fixedSpecializedBondASWSpread1,
                error2, tolerance, true);

        // Fixed bond (Isin: IT0006527060 IBRD 5 02/05/19)
        // maturity occurs on a business day
        Date fixedBondStartDate2 = new Date(5, Month.FEBRUARY, 2005);
        Date fixedBondMaturityDate2 = new Date(5, Month.FEBRUARY, 2019);
        Schedule fixedBondSchedule2 = new Schedule(
                fixedBondStartDate2, fixedBondMaturityDate2,
                new Period(Frequency.ANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond fixedBond2 = genericFixedBond(vars, bondCalendar, settlementDays,
                fixedBondStartDate2, fixedBondMaturityDate2, fixedBondSchedule2,
                0.05, new Thirty360(Thirty360.Convention.BondBasis));
        fixedBond2.setPricingEngine(bondEngine);

        // equivalent specialized fixed rate bond
        Bond fixedSpecializedBond2 = new FixedRateBond(settlementDays,
                vars.faceAmount, fixedBondSchedule2,
                new double[]{0.05},
                new Thirty360(Thirty360.Convention.BondBasis),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(5, Month.FEBRUARY, 2005));
        fixedSpecializedBond2.setPricingEngine(bondEngine);

        double fixedBondPrice2 = fixedBond2.cleanPrice();
        double fixedSpecializedBondPrice2 =
                fixedSpecializedBond2.cleanPrice();
        AssetSwap fixedBondAssetSwap2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondPrice2,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap2.setPricingEngine(swapEngine);
        AssetSwap fixedSpecializedBondAssetSwap2 = new AssetSwap(payFixedRate,
                fixedSpecializedBond2, fixedSpecializedBondPrice2,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedSpecializedBondAssetSwap2.setPricingEngine(swapEngine);
        double fixedBondAssetSwapPrice2 = fixedBondAssetSwap2.fairCleanPrice();
        double fixedSpecializedBondAssetSwapPrice2 =
                fixedSpecializedBondAssetSwap2.fairCleanPrice();

        double error3 = Math.abs(fixedBondAssetSwapPrice2
                - fixedSpecializedBondAssetSwapPrice2);
        checkBondPair("wrong clean price for fixed bond:",
                "generic  fixed rate bond's clean price:",
                fixedBondAssetSwapPrice2,
                "equivalent specialized  bond's clean price:",
                fixedSpecializedBondAssetSwapPrice2,
                error3, tolerance, true);

        // market executable price as of 4th sept 2007
        double fixedBondMktPrice2 = 102.178;
        AssetSwap fixedBondASW2 = new AssetSwap(payFixedRate,
                fixedBond2, fixedBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondASW2.setPricingEngine(swapEngine);
        AssetSwap fixedSpecializedBondASW2 = new AssetSwap(payFixedRate,
                fixedSpecializedBond2, fixedBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        fixedSpecializedBondASW2.setPricingEngine(swapEngine);
        double fixedBondASWSpread2 = fixedBondASW2.fairSpread();
        double fixedSpecializedBondASWSpread2 =
                fixedSpecializedBondASW2.fairSpread();
        double error4 =
                Math.abs(fixedBondASWSpread2 - fixedSpecializedBondASWSpread2);
        checkBondPair("wrong asw spread for fixed bond:",
                "generic  fixed rate bond's  asw spread:",
                fixedBondASWSpread2,
                "equivalent specialized bond's asw spread:",
                fixedSpecializedBondASWSpread2,
                error4, tolerance, true);

        // FRN bond (Isin: IT0003543847 ISPIM 0 09/29/13)
        // maturity doesn't occur on a business day
        Date floatingBondStartDate1 = new Date(29, Month.SEPTEMBER, 2003);
        Date floatingBondMaturityDate1 = new Date(29, Month.SEPTEMBER, 2013);
        Schedule floatingBondSchedule1 = new Schedule(
                floatingBondStartDate1, floatingBondMaturityDate1,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond floatingBond1 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate1, floatingBondMaturityDate1,
                floatingBondSchedule1, BusinessDayConvention.FOLLOWING,
                0.0056, inArrears);
        floatingBond1.setPricingEngine(bondEngine);

        // equivalent specialized floater
        Bond floatingSpecializedBond1 = new FloatingRateBond(settlementDays,
                vars.faceAmount, floatingBondSchedule1,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0056),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(29, Month.SEPTEMBER, 2003));
        floatingSpecializedBond1.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond1.cashflows(), vars.pricer);
        CashflowUtil.setCouponPricer(
                floatingSpecializedBond1.cashflows(), vars.pricer);
        vars.iborIndex.addFixing(new Date(27, Month.MARCH, 2007), 0.0402, false);
        double floatingBondPrice1 = floatingBond1.cleanPrice();
        double floatingSpecializedBondPrice1 =
                floatingSpecializedBond1.cleanPrice();
        AssetSwap floatingBondAssetSwap1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondPrice1,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondAssetSwap1.setPricingEngine(swapEngine);
        AssetSwap floatingSpecializedBondAssetSwap1 = new AssetSwap(payFixedRate,
                floatingSpecializedBond1, floatingSpecializedBondPrice1,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingSpecializedBondAssetSwap1.setPricingEngine(swapEngine);
        double floatingBondAssetSwapPrice1 =
                floatingBondAssetSwap1.fairCleanPrice();
        double floatingSpecializedBondAssetSwapPrice1 =
                floatingSpecializedBondAssetSwap1.fairCleanPrice();

        double error5 = Math.abs(floatingBondAssetSwapPrice1
                - floatingSpecializedBondAssetSwapPrice1);
        checkBondPair("wrong clean price for frnbond:",
                "generic frn rate bond's clean price:",
                floatingBondAssetSwapPrice1,
                "equivalent specialized  bond's price:",
                floatingSpecializedBondAssetSwapPrice1,
                error5, tolerance, true);

        // market executable price as of 4th sept 2007
        double floatingBondMktPrice1 = 101.33;
        AssetSwap floatingBondASW1 = new AssetSwap(payFixedRate,
                floatingBond1, floatingBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondASW1.setPricingEngine(swapEngine);
        AssetSwap floatingSpecializedBondASW1 = new AssetSwap(payFixedRate,
                floatingSpecializedBond1, floatingBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingSpecializedBondASW1.setPricingEngine(swapEngine);
        double floatingBondASWSpread1 = floatingBondASW1.fairSpread();
        double floatingSpecializedBondASWSpread1 =
                floatingSpecializedBondASW1.fairSpread();
        double error6 = Math.abs(floatingBondASWSpread1
                - floatingSpecializedBondASWSpread1);
        checkBondPair("wrong asw spread for fixed bond:",
                "generic  frn rate bond's  asw spread:",
                floatingBondASWSpread1,
                "equivalent specialized bond's asw spread:",
                floatingSpecializedBondASWSpread1,
                error6, tolerance, true);

        // FRN bond (Isin: XS0090566539 COE 0 09/24/18)
        // maturity occurs on a business day
        Date floatingBondStartDate2 = new Date(24, Month.SEPTEMBER, 2004);
        Date floatingBondMaturityDate2 = new Date(24, Month.SEPTEMBER, 2018);
        Schedule floatingBondSchedule2 = new Schedule(
                floatingBondStartDate2, floatingBondMaturityDate2,
                new Period(Frequency.SEMIANNUAL), bondCalendar,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());
        // generic bond
        Bond floatingBond2 = genericFloatingBond(vars, bondCalendar,
                settlementDays, fixingDays,
                floatingBondStartDate2, floatingBondMaturityDate2,
                floatingBondSchedule2, BusinessDayConvention.MODIFIED_FOLLOWING,
                0.0025, inArrears);
        floatingBond2.setPricingEngine(bondEngine);

        // equivalent specialized floater
        Bond floatingSpecializedBond2 = new FloatingRateBond(settlementDays,
                vars.faceAmount, floatingBondSchedule2,
                vars.iborIndex, new Actual360(),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(1.0), List.of(0.0025),
                List.of(), List.of(),
                inArrears,
                100.0, new Date(24, Month.SEPTEMBER, 2004));
        floatingSpecializedBond2.setPricingEngine(bondEngine);

        CashflowUtil.setCouponPricer(floatingBond2.cashflows(), vars.pricer);
        CashflowUtil.setCouponPricer(
                floatingSpecializedBond2.cashflows(), vars.pricer);

        vars.iborIndex.addFixing(new Date(22, Month.MARCH, 2007), 0.04013, false);

        double floatingBondPrice2 = floatingBond2.cleanPrice();
        double floatingSpecializedBondPrice2 =
                floatingSpecializedBond2.cleanPrice();
        AssetSwap floatingBondAssetSwap2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondPrice2,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondAssetSwap2.setPricingEngine(swapEngine);
        AssetSwap floatingSpecializedBondAssetSwap2 = new AssetSwap(payFixedRate,
                floatingSpecializedBond2, floatingSpecializedBondPrice2,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingSpecializedBondAssetSwap2.setPricingEngine(swapEngine);
        double floatingBondAssetSwapPrice2 =
                floatingBondAssetSwap2.fairCleanPrice();
        double floatingSpecializedBondAssetSwapPrice2 =
                floatingSpecializedBondAssetSwap2.fairCleanPrice();
        double error7 = Math.abs(floatingBondAssetSwapPrice2
                - floatingSpecializedBondAssetSwapPrice2);
        checkBondPair("wrong clean price for frnbond:",
                "generic frn rate bond's clean price:",
                floatingBondAssetSwapPrice2,
                "equivalent specialized frn  bond's price:",
                floatingSpecializedBondAssetSwapPrice2,
                error7, tolerance, true);

        // market executable price as of 4th sept 2007
        double floatingBondMktPrice2 = 101.26;
        AssetSwap floatingBondASW2 = new AssetSwap(payFixedRate,
                floatingBond2, floatingBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingBondASW2.setPricingEngine(swapEngine);
        AssetSwap floatingSpecializedBondASW2 = new AssetSwap(payFixedRate,
                floatingSpecializedBond2, floatingBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        floatingSpecializedBondASW2.setPricingEngine(swapEngine);
        double floatingBondASWSpread2 = floatingBondASW2.fairSpread();
        double floatingSpecializedBondASWSpread2 =
                floatingSpecializedBondASW2.fairSpread();
        double error8 =
                Math.abs(floatingBondASWSpread2 - floatingSpecializedBondASWSpread2);
        checkBondPair("wrong asw spread for frn bond:",
                "generic  frn rate bond's  asw spread:",
                floatingBondASWSpread2,
                "equivalent specialized bond's asw spread:",
                floatingSpecializedBondASWSpread2,
                error8, tolerance, true);

        // CMS bonds (Isin: XS0228052402 CRDIT 0 8/22/20 and
        // Isin: XS0218766664 ISPIM 0 5/6/15) are out of DON-17 scope:
        // Java has no CmsRateBond/CMS pricer, so those branches are skipped.

        // Zero-Coupon bond (Isin: DE0004771662 IBRD 0 12/20/15)
        // maturity doesn't occur on a business day
        Date zeroCpnBondStartDate1 = new Date(19, Month.DECEMBER, 1985);
        Date zeroCpnBondMaturityDate1 = new Date(20, Month.DECEMBER, 2015);
        // generic bond
        Bond zeroCpnBond1 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate1, zeroCpnBondMaturityDate1);
        zeroCpnBond1.setPricingEngine(bondEngine);

        // specialized zerocpn bond
        Bond zeroCpnSpecializedBond1 = new ZeroCouponBond(settlementDays,
                bondCalendar, vars.faceAmount,
                new Date(20, Month.DECEMBER, 2015),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(19, Month.DECEMBER, 1985));
        zeroCpnSpecializedBond1.setPricingEngine(bondEngine);

        double zeroCpnBondPrice1 = zeroCpnBond1.cleanPrice();
        double zeroCpnSpecializedBondPrice1 =
                zeroCpnSpecializedBond1.cleanPrice();
        AssetSwap zeroCpnBondAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondPrice1,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondAssetSwap1.setPricingEngine(swapEngine);
        AssetSwap zeroCpnSpecializedBondAssetSwap1 = new AssetSwap(payFixedRate,
                zeroCpnSpecializedBond1, zeroCpnSpecializedBondPrice1,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnSpecializedBondAssetSwap1.setPricingEngine(swapEngine);
        double zeroCpnBondAssetSwapPrice1 =
                zeroCpnBondAssetSwap1.fairCleanPrice();
        double zeroCpnSpecializedBondAssetSwapPrice1 =
                zeroCpnSpecializedBondAssetSwap1.fairCleanPrice();
        double error13 = Math.abs(zeroCpnBondAssetSwapPrice1
                - zeroCpnSpecializedBondAssetSwapPrice1);
        // Official message omits the scientific manipulators for error13.
        checkBondPair("wrong clean price for zerocpn bond:",
                "generic zero cpn bond's clean price:",
                zeroCpnBondAssetSwapPrice1,
                "specialized equivalent bond's price:",
                zeroCpnSpecializedBondAssetSwapPrice1,
                error13, tolerance, false);

        // market executable price as of 4th sept 2007
        double zeroCpnBondMktPrice1 = 72.277;
        AssetSwap zeroCpnBondASW1 = new AssetSwap(payFixedRate,
                zeroCpnBond1, zeroCpnBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondASW1.setPricingEngine(swapEngine);
        AssetSwap zeroCpnSpecializedBondASW1 = new AssetSwap(payFixedRate,
                zeroCpnSpecializedBond1, zeroCpnBondMktPrice1,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnSpecializedBondASW1.setPricingEngine(swapEngine);
        double zeroCpnBondASWSpread1 = zeroCpnBondASW1.fairSpread();
        double zeroCpnSpecializedBondASWSpread1 =
                zeroCpnSpecializedBondASW1.fairSpread();
        double error14 =
                Math.abs(zeroCpnBondASWSpread1 - zeroCpnSpecializedBondASWSpread1);
        checkBondPair("wrong asw spread for zeroCpn bond:",
                "generic zeroCpn bond's  asw spread:",
                zeroCpnBondASWSpread1,
                "equivalent specialized bond's asw spread:",
                zeroCpnSpecializedBondASWSpread1,
                error14, tolerance, true);

        // Zero Coupon bond (Isin: IT0001200390 ISPIM 0 02/17/28)
        // maturity doesn't occur on a business day
        Date zeroCpnBondStartDate2 = new Date(17, Month.FEBRUARY, 1998);
        Date zeroCpnBondMaturityDate2 = new Date(17, Month.FEBRUARY, 2028);
        // generic bond
        Bond zeroCpnBond2 = genericZeroBond(vars, bondCalendar, settlementDays,
                zeroCpnBondStartDate2, zeroCpnBondMaturityDate2);
        zeroCpnBond2.setPricingEngine(bondEngine);

        // specialized zerocpn bond
        Bond zeroCpnSpecializedBond2 = new ZeroCouponBond(settlementDays,
                bondCalendar, vars.faceAmount,
                new Date(17, Month.FEBRUARY, 2028),
                BusinessDayConvention.FOLLOWING,
                100.0, new Date(17, Month.FEBRUARY, 1998));
        zeroCpnSpecializedBond2.setPricingEngine(bondEngine);

        double zeroCpnBondPrice2 = zeroCpnBond2.cleanPrice();
        double zeroCpnSpecializedBondPrice2 =
                zeroCpnSpecializedBond2.cleanPrice();

        AssetSwap zeroCpnBondAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondPrice2,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondAssetSwap2.setPricingEngine(swapEngine);
        AssetSwap zeroCpnSpecializedBondAssetSwap2 = new AssetSwap(payFixedRate,
                zeroCpnSpecializedBond2, zeroCpnSpecializedBondPrice2,
                vars.iborIndex, vars.nonnullspread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnSpecializedBondAssetSwap2.setPricingEngine(swapEngine);
        double zeroCpnBondAssetSwapPrice2 =
                zeroCpnBondAssetSwap2.fairCleanPrice();
        double zeroCpnSpecializedBondAssetSwapPrice2 =
                zeroCpnSpecializedBondAssetSwap2.fairCleanPrice();
        double error15 = Math.abs(zeroCpnBondAssetSwapPrice2
                - zeroCpnSpecializedBondAssetSwapPrice2);
        // Official message omits the scientific manipulators for error15.
        checkBondPair("wrong clean price for zerocpn bond:",
                "generic zero cpn bond's clean price:",
                zeroCpnBondAssetSwapPrice2,
                "equivalent specialized bond's price:",
                zeroCpnSpecializedBondAssetSwapPrice2,
                error15, tolerance, false);

        // market executable price as of 4th sept 2007
        double zeroCpnBondMktPrice2 = 72.277;
        AssetSwap zeroCpnBondASW2 = new AssetSwap(payFixedRate,
                zeroCpnBond2, zeroCpnBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnBondASW2.setPricingEngine(swapEngine);
        AssetSwap zeroCpnSpecializedBondASW2 = new AssetSwap(payFixedRate,
                zeroCpnSpecializedBond2, zeroCpnBondMktPrice2,
                vars.iborIndex, vars.spread,
                new Schedule(),
                vars.iborIndex.dayCounter(),
                parAssetSwap);
        zeroCpnSpecializedBondASW2.setPricingEngine(swapEngine);
        double zeroCpnBondASWSpread2 = zeroCpnBondASW2.fairSpread();
        double zeroCpnSpecializedBondASWSpread2 =
                zeroCpnSpecializedBondASW2.fairSpread();
        double error16 =
                Math.abs(zeroCpnBondASWSpread2 - zeroCpnSpecializedBondASWSpread2);
        checkBondPair("wrong asw spread for zeroCpn bond:",
                "generic zeroCpn bond's  asw spread:",
                zeroCpnBondASWSpread2,
                "equivalent specialized bond's asw spread:",
                zeroCpnSpecializedBondASWSpread2,
                error16, tolerance, true);
    }
}
