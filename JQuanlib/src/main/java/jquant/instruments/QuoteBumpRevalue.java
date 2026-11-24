package jquant.instruments;

import jquant.Instrument;
import jquant.quotes.SimpleQuote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Minimal quote bump-and-revalue utility for a single bond.
 *
 * <p>This is a <b>new business API</b> for JQuant, not a translation of an
 * official QuantLib class. It is deliberately outside the yield / coupon
 * BPV measures: {@link jquant.cashflows.CashFlows#bps},
 * {@link jquant.pricingengines.bond.BondFunctions#basisPointValue} and
 * {@link jquant.pricingengines.bond.BondFunctions#yieldValueBasisPoint} are
 * yield or coupon-annuity sensitivities of the cash-flow leg (already
 * documented as unscaled cash amounts). They must not be mixed with the
 * numbers returned here.
 *
 * <h2>Measure</h2>
 *
 * The default measure is {@link Bond#settlementValue()}: the <b>cash dirty
 * amount at the bond settlement date</b>, in the same units as the official
 * {@code settlementValue}. It is <b>not</b> a clean or dirty price per 100 of
 * notional, and it is <b>not</b> {@link Instrument#NPV()}, which is the value
 * as of the curve reference date.
 *
 * <h2>Formula and units</h2>
 *
 * For an explicit bump {@code h > 0} the utility evaluates, with all supplied
 * quotes moved simultaneously:
 *
 * <pre>
 *   basePv = V(0)
 *   upPv   = V(price with every quote at original + h)
 *   downPv = V(price with every quote at original - h)
 *
 *   centralPvChange = (upPv - downPv) / 2
 *   upDelta         = upPv   - basePv
 *   downDelta       = downPv - basePv
 * </pre>
 *
 * {@code centralPvChange} is the <b>monetary settlement-value change for a
 * +1 bump of size {@code h}</b>, estimated by a central difference. It is
 * <b>not</b> the derivative {@code (upPv - downPv) / (2h)}. Because the
 * measure rises when rates fall, a long bond against a discount-rate quote
 * normally returns a <b>negative</b> {@code centralPvChange}; the sign is
 * reported honestly and never flipped to a positive "DV01" convention.
 *
 * <p>For rate quotes the default bump is {@link #ONE_BASIS_POINT}
 * ({@code 1.0e-4}, one market basis point of the quote's raw value). A
 * general quote (for example a spread or a non-rate factor) must use
 * {@link #centralPvChange(Bond, double, SimpleQuote...)} with an explicit,
 * caller-chosen bump; no default is ever applied to an unnamed quote.
 *
 * <h2>Quotes</h2>
 *
 * The caller passes every {@link SimpleQuote} to bump; the utility never
 * walks the market graph to discover quotes. A quote object passed more than
 * once is deduplicated (identity) and bumped once, so a quote shared by the
 * discount and forecast paths is not double-shocked. All quotes are moved
 * together as one scenario, not one at a time.
 *
 * <p>The quotes must be the ones actually wired into the bond's curves (for
 * example a {@code FlatForward} built on a {@code Handle<Quote>}). If a quote
 * does not reach the bond through the normal observer chain, bumping it will
 * not invalidate the bond's cache and the result will show no change.
 *
 * <h2>Exception safety and threading</h2>
 *
 * Every bumped quote is restored in a {@code finally} block. After a
 * successful call the bond is consistent with the original market and a
 * subsequent {@link Bond#settlementValue()} or {@link Bond#cleanPrice()}
 * equals the pre-call value. If pricing fails on either side, the quotes are
 * restored and the original exception is rethrown; a restore failure is added
 * to it as a suppressed exception instead of masking it. With no pricing
 * failure, a restore failure is thrown.
 *
 * <p><b>Not thread-safe.</b> The tool mutates the shared market graph
 * (quotes, curves, handles, bond cache) in place. It is same-thread only and
 * must never be used concurrently on a market graph shared with other
 * threads. It is not ThreadLocal-safe.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * SimpleQuote discountQuote = new SimpleQuote(0.03);
 * FlatForward discountCurve =
 *         new FlatForward(today, new Handle<>(discountQuote, true),
 *                         new Actual365Fixed());
 * bond.setPricingEngine(new DiscountingBondEngine(
 *         new RelinkableHandle<>(discountCurve, true)));
 *
 * QuoteBumpRevalue.Result result =
 *         QuoteBumpRevalue.centralPvChangeForRateQuotes(bond, discountQuote);
 *
 * double base = result.basePv();             // settlementValue() as found
 * double pvChange = result.centralPvChange();// cash change for +1bp, usually < 0
 * double up = result.upDelta();              // cash change at +1bp
 * double down = result.downDelta();          // cash change at -1bp
 * }</pre>
 */
public final class QuoteBumpRevalue {

    /** One market basis point of a quote's raw value: {@code 1.0e-4}. */
    public static final double ONE_BASIS_POINT = 1.0e-4;

    private QuoteBumpRevalue() {
    }

    /**
     * Central bump-and-revalue with the rate-quote default bump,
     * {@link #ONE_BASIS_POINT}.
     *
     * <p>Only for quotes whose raw value is a rate; see the class Javadoc.
     * For any other quote use {@link #centralPvChange(Bond, double,
     * SimpleQuote...)} with an explicit bump.
     */
    public static Result centralPvChangeForRateQuotes(Bond bond,
                                                     SimpleQuote... quotes) {
        return centralPvChange(bond, ONE_BASIS_POINT, quotes);
    }

    /**
     * Central bump-and-revalue with an explicit bump size.
     *
     * <p>The supplied quotes are deduplicated by identity, bumped together to
     * {@code original + bump}, priced, bumped together to
     * {@code original - bump}, priced, and restored in a {@code finally}
     * block. The result is the monetary settlement-value change for one bump
     * of size {@code bump}; it is not divided by {@code bump}. See the class
     * Javadoc for units, sign, exception safety and the same-thread limit.
     *
     * @param bond  bond with a pricing engine already set
     * @param bump  positive, finite bump of the quotes' raw values
     * @param quotes quotes to bump; at least one, deduplicated by identity
     * @throws IllegalArgumentException if an argument is invalid
     */
    public static Result centralPvChange(Bond bond,
                                         double bump,
                                         SimpleQuote... quotes) {
        QL_REQUIRE(bond != null, "null bond");
        QL_REQUIRE(bump > 0.0 && !Double.isInfinite(bump),
                "bump must be positive and finite");
        List<SimpleQuote> unique = uniqueQuotes(quotes);

        double[] original = new double[unique.size()];
        for (int i = 0; i < unique.size(); ++i)
            original[i] = unique.get(i).value();

        // No quote has been touched yet, so a failure here needs no restore.
        double basePv = bond.settlementValue();

        double upPv = Double.NaN;
        double downPv = Double.NaN;
        Throwable primary = null;
        try {
            setAll(unique, original, bump);
            upPv = bond.settlementValue();

            setAll(unique, original, -bump);
            downPv = bond.settlementValue();
        } catch (RuntimeException | Error e) {
            primary = e;
            throw e;
        } finally {
            restoreAll(unique, original, primary);
        }

        return new Result(basePv, upPv, downPv, bump);
    }

    private static List<SimpleQuote> uniqueQuotes(SimpleQuote[] quotes) {
        QL_REQUIRE(quotes != null && quotes.length > 0,
                "no quotes given");
        Set<SimpleQuote> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());
        List<SimpleQuote> unique = new ArrayList<>(quotes.length);
        for (SimpleQuote quote : quotes) {
            QL_REQUIRE(quote != null, "null quote");
            if (seen.add(quote))
                unique.add(quote);
        }
        return unique;
    }

    private static void setAll(List<SimpleQuote> quotes,
                               double[] original,
                               double shift) {
        for (int i = 0; i < quotes.size(); ++i)
            quotes.get(i).setValue(original[i] + shift);
    }

    /**
     * Restores every quote. Failures do not stop the remaining restores. A
     * restore failure is suppressed onto the primary pricing failure when one
     * is in flight; otherwise it is rethrown.
     */
    private static void restoreAll(List<SimpleQuote> quotes,
                                   double[] original,
                                   Throwable primary) {
        Throwable restoreFailure = null;
        for (int i = 0; i < quotes.size(); ++i) {
            try {
                quotes.get(i).setValue(original[i]);
            } catch (RuntimeException | Error e) {
                if (restoreFailure == null)
                    restoreFailure = e;
                else
                    restoreFailure.addSuppressed(e);
            }
        }
        if (restoreFailure == null)
            return;
        if (primary != null) {
            primary.addSuppressed(restoreFailure);
        } else if (restoreFailure instanceof RuntimeException runtime) {
            throw runtime;
        } else {
            throw (Error) restoreFailure;
        }
    }

    /**
     * Immutable raw ingredients of one central bump-and-revalue calculation.
     *
     * <p>All PVs are cash dirty amounts at the bond settlement date, in the
     * same units as {@link Bond#settlementValue()}, for the market state
     * described by {@link #basePv()}, {@link #upPv()} and {@link #downPv()}.
     */
    public static final class Result {
        private final double basePv;
        private final double upPv;
        private final double downPv;
        private final double bump;
        private final double centralPvChange;

        private Result(double basePv, double upPv, double downPv, double bump) {
            this.basePv = basePv;
            this.upPv = upPv;
            this.downPv = downPv;
            this.bump = bump;
            this.centralPvChange = (upPv - downPv) / 2.0;
        }

        /** {@code V(0)}: settlement value before any bump, unchanged state. */
        public double basePv() {
            return basePv;
        }

        /** {@code V(+h)}: settlement value with every quote at original + bump. */
        public double upPv() {
            return upPv;
        }

        /** {@code V(-h)}: settlement value with every quote at original - bump. */
        public double downPv() {
            return downPv;
        }

        /** The explicit bump {@code h} applied to the quotes' raw values. */
        public double bump() {
            return bump;
        }

        /**
         * {@code (upPv - downPv) / 2}: monetary settlement-value change for a
         * +1 bump of size {@link #bump()}. <b>Not</b> the derivative; the
         * sign is not flipped.
         */
        public double centralPvChange() {
            return centralPvChange;
        }

        /** {@code upPv - basePv}. */
        public double upDelta() {
            return upPv - basePv;
        }

        /** {@code downPv - basePv}. */
        public double downDelta() {
            return downPv - basePv;
        }

        @Override
        public String toString() {
            return "QuoteBumpRevalue.Result{bump=" + bump
                    + ", basePv=" + basePv
                    + ", upPv=" + upPv
                    + ", downPv=" + downPv
                    + ", centralPvChange=" + centralPvChange
                    + ", upDelta=" + upDelta()
                    + ", downDelta=" + downDelta() + '}';
        }
    }
}
