package jquant.indexes;

import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.Locale;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Class for swap-rate spread indexes.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/coupons/swapspreadindex.{hpp,cpp}}. The index
 * forecasts the geared difference of two swap rates, e.g. the 10Y-2Y CMS
 * spread with the default gearings {@code 1.0} and {@code -1.0}.
 */
public class SwapSpreadIndex extends InterestRateIndex {

    private final SwapIndex swapIndex1_;
    private final SwapIndex swapIndex2_;
    private final double gearing1_;
    private final double gearing2_;

    public SwapSpreadIndex(final String familyName,
                           final SwapIndex swapIndex1,
                           final SwapIndex swapIndex2,
                           double gearing1,
                           double gearing2) {
        super(familyName,
                swapIndex1.tenor(), // does not make sense, but we have to provide one
                swapIndex1.fixingDays(),
                swapIndex1.currency(),
                swapIndex1.fixingCalendar(),
                swapIndex1.dayCounter());
        swapIndex1_ = swapIndex1;
        swapIndex2_ = swapIndex2;
        gearing1_ = gearing1;
        gearing2_ = gearing2;

        registerWith(swapIndex1_);
        registerWith(swapIndex2_);

        // Official std::setprecision(4) << std::fixed
        name_ = String.format(Locale.ROOT, "%s(%.4f) + %s(%.4f)",
                swapIndex1_.name(), gearing1,
                swapIndex2_.name(), gearing2);

        QL_REQUIRE(swapIndex1_.fixingDays() == swapIndex2_.fixingDays(),
                "index1 fixing days ("
                        + swapIndex1_.fixingDays() + ")"
                        + "must be equal to index2 fixing days ("
                        + swapIndex2_.fixingDays() + ")");

        QL_REQUIRE(TimeUtils.equals(swapIndex1_.fixingCalendar(),
                        swapIndex2_.fixingCalendar()),
                "index1 fixingCalendar ("
                        + swapIndex1_.fixingCalendar() + ")"
                        + "must be equal to index2 fixingCalendar ("
                        + swapIndex2_.fixingCalendar() + ")");

        QL_REQUIRE(swapIndex1_.currency().equals(swapIndex2_.currency()),
                "index1 currency (" + swapIndex1_.currency() + ")"
                        + "must be equal to index2 currency ("
                        + swapIndex2_.currency() + ")");

        QL_REQUIRE(TimeUtils.equals(swapIndex1_.dayCounter(),
                        swapIndex2_.dayCounter()),
                "index1 dayCounter ("
                        + swapIndex1_.dayCounter() + ")"
                        + "must be equal to index2 dayCounter ("
                        + swapIndex2_.dayCounter() + ")");

        QL_REQUIRE(TimeUtils.equals(swapIndex1_.fixedLegTenor(),
                        swapIndex2_.fixedLegTenor()),
                "index1 fixedLegTenor ("
                        + swapIndex1_.fixedLegTenor() + ")"
                        + "must be equal to index2 fixedLegTenor ("
                        + swapIndex2_.fixedLegTenor()); // official typo: missing ')'

        QL_REQUIRE(swapIndex1_.fixedLegConvention() ==
                        swapIndex2_.fixedLegConvention(),
                "index1 fixedLegConvention ("
                        + swapIndex1_.fixedLegConvention() + ")"
                        + "must be equal to index2 fixedLegConvention ("
                        + swapIndex2_.fixedLegConvention()); // official typo: missing ')'
    }

    @Override
    public Date maturityDate(final Date valueDate) {
        QL_FAIL("SwapSpreadIndex does not provide a single maturity date");
        return null;
    }

    /** Official default gearings {@code 1.0} and {@code -1.0}. */
    public SwapSpreadIndex(final String familyName,
                           final SwapIndex swapIndex1,
                           final SwapIndex swapIndex2) {
        this(familyName, swapIndex1, swapIndex2, 1.0, -1.0);
    }

    @Override
    public double forecastFixing(final Date fixingDate) {
        // this also handles the case when one of indices has
        // a historic fixing on the evaluation date
        return gearing1_ * swapIndex1_.fixing(fixingDate, false) +
                gearing2_ * swapIndex2_.fixing(fixingDate, false);
    }

    @Override
    public double pastFixing(final Date fixingDate) {
        double f1 = swapIndex1_.pastFixing(fixingDate);
        double f2 = swapIndex2_.pastFixing(fixingDate);
        // if one of the fixings is missing we return null, indicating
        // a missing fixing for the spread index
        if (Double.isNaN(f1) || Double.isNaN(f2))
            return Double.NaN;
        else
            return gearing1_ * f1 + gearing2_ * f2;
    }

    @Override
    public boolean allowsNativeFixings() {
        return false;
    }

    public SwapIndex swapIndex1() {
        return swapIndex1_;
    }

    public SwapIndex swapIndex2() {
        return swapIndex2_;
    }

    public double gearing1() {
        return gearing1_;
    }

    public double gearing2() {
        return gearing2_;
    }

}
