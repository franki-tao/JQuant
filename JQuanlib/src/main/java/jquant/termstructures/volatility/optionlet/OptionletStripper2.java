package jquant.termstructures.volatility.optionlet;

import jquant.CashFlow;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.cashflows.CashFlows;
import jquant.cashflows.FloatingRateCoupon;
import jquant.instruments.CapFloor;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.capfloor.CapFloorTermVolCurve;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.close;

/**
 * Helper class to extend an {@link OptionletStripper1} object stripping
 * additional optionlet (i.e. caplet/floorlet) volatilities (a.k.a.
 * forward-forward volatilities) from the (cap/floor) at-the-money term
 * volatilities of a {@link CapFloorTermVolCurve}.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/optionlet/optionletstripper2.{hpp,cpp}}.
 */
public class OptionletStripper2 extends OptionletStripper {

    private final OptionletStripper1 stripper1_;
    private final CapFloorTermVolCurve atmCapFloorTermVolCurve_;
    private final DayCounter dc_;
    private final int nOptionExpiries_;

    private final double[] atmCapFloorStrikes_;
    private final double[] atmCapFloorPrices_;
    private final double[] spreadsVolImplied_;
    private final List<CapFloor> caps_;

    private final int maxEvaluations_ = 10000;
    private final double accuracy_ = 1.e-6;

    public OptionletStripper2(OptionletStripper1 optionletStripper1,
                              CapFloorTermVolCurve atmCapFloorTermVolCurve) {
        super(optionletStripper1.termVolSurface(),
                optionletStripper1.iborIndex(),
                new RelinkableHandle<YieldTermStructure>(),
                optionletStripper1.volatilityType(),
                optionletStripper1.displacement(),
                optionletStripper1.optionletFrequency(),
                optionletStripper1.paymentLag());
        stripper1_ = optionletStripper1;
        atmCapFloorTermVolCurve_ = atmCapFloorTermVolCurve;
        dc_ = stripper1_.termVolSurface().dayCounter();
        nOptionExpiries_ = atmCapFloorTermVolCurve_.optionTenors().size();
        atmCapFloorStrikes_ = new double[nOptionExpiries_];
        atmCapFloorPrices_ = new double[nOptionExpiries_];
        spreadsVolImplied_ = new double[nOptionExpiries_];
        caps_ = new ArrayList<>(Collections.nCopies(nOptionExpiries_, null));

        registerWith(stripper1_);
        registerWith(atmCapFloorTermVolCurve_);

        QL_REQUIRE(TimeUtils.equals(dc_, atmCapFloorTermVolCurve_.dayCounter()),
                "different day counters provided");
    }

    @Override
    public void performCalculations() {

        //// optionletStripper data
        List<Date> optionletDates = stripper1_.optionletFixingDates();
        List<Date> optionletPaymentDates = stripper1_.optionletPaymentDates();
        double[] optionletAccrualPeriods = stripper1_.optionletAccrualPeriods();
        double[] optionletTimes = stripper1_.optionletFixingTimes();
        double[] atmOptionletRates = stripper1_.atmOptionletRates();
        for (int i = 0; i < optionletStrikes_.size(); ++i) {
            optionletDates_.set(i, optionletDates.get(i));
            optionletPaymentDates_.set(i, optionletPaymentDates.get(i));
            optionletAccrualPeriods_[i] = optionletAccrualPeriods[i];
            optionletTimes_[i] = optionletTimes[i];
            atmOptionletRate_[i] = atmOptionletRates[i];
            // copy the source strips: the ATM points are inserted below and
            // must not leak back into OptionletStripper1
            optionletStrikes_.set(i,
                    new ArrayList<>(stripper1_.optionletStrikes(i)));
            optionletVolatilities_.set(i,
                    new ArrayList<>(stripper1_.optionletVolatilities(i)));
        }

        // atmCapFloorTermVolCurve data
        List<Period> optionExpiriesTenors =
                atmCapFloorTermVolCurve_.optionTenors();
        double[] optionExpiriesTimes = atmCapFloorTermVolCurve_.optionTimes();
        int[] capOptionletCounts = new int[nOptionExpiries_];

        for (int j = 0; j < nOptionExpiries_; ++j) {
            double atmOptionVol = atmCapFloorTermVolCurve_.volatility(
                    optionExpiriesTimes[j], 33.3333, false); // dummy strike
            Handle<Quote> atmOptionVolHandle = new Handle<>(
                    new SimpleQuote(atmOptionVol), true);
            PricingEngine engine = makeCapFloorPricingEngine(
                    iborIndex_.forwardingTermStructure(), atmOptionVolHandle);

            List<CashFlow> leg = makeCapFloorLeg(optionExpiriesTenors.get(j));
            capOptionletCounts[j] =
                    Math.min(leg.size(), optionletVolatilities_.size());
            FloatingRateCoupon firstCoupon =
                    (FloatingRateCoupon) leg.get(0);
            if (TimeUtils.less(firstCoupon.fixingDate(), optionletDates_.get(0))) {
                QL_REQUIRE(optionletVolatilities_.size() >= 2,
                        "cannot extrapolate an ATM cap/floor fixing before the "
                                + "first optionlet date from fewer than two "
                                + "optionlet nodes");
                capOptionletCounts[j] = Math.max(capOptionletCounts[j], 2);
            }
            atmCapFloorStrikes_[j] = CashFlows.atmRate(
                    leg, iborIndex_.forwardingTermStructure().currentLink(),
                    false,
                    iborIndex_.forwardingTermStructure().currentLink()
                            .referenceDate());
            CapFloor cap = new CapFloor(CapFloor.Type.Cap, leg,
                    Collections.singletonList(atmCapFloorStrikes_[j]));
            cap.setPricingEngine(engine);
            caps_.set(j, cap);
            atmCapFloorPrices_[j] = cap.NPV();
        }

        Handle<OptionletVolatilityStructure> adapter = new Handle<>(
                new StrippedOptionletAdapter(stripper1_), true);
        adapter.currentLink().enableExtrapolation();
        double[] spreads = spreadsVolImplied(adapter);
        System.arraycopy(spreads, 0, spreadsVolImplied_, 0, spreads.length);

        for (int j = 0; j < nOptionExpiries_; ++j) {
            for (int i = 0; i < capOptionletCounts[j]; ++i) {
                double unadjustedVol = adapter.currentLink().volatility(
                        optionletTimes_[i], atmCapFloorStrikes_[j], false);
                double adjustedVol = unadjustedVol + spreadsVolImplied_[j];

                // insert adjusted volatility
                List<Double> strikes = optionletStrikes_.get(i);
                int insertIndex = lowerBound(strikes, atmCapFloorStrikes_[j]);

                strikes.add(insertIndex, atmCapFloorStrikes_[j]);
                optionletVolatilities_.get(i).add(insertIndex, adjustedVol);
            }
        }
    }

    private double[] spreadsVolImplied(
            Handle<OptionletVolatilityStructure> baseVolatility) {
        Brent solver = new Brent();
        double[] result = new double[nOptionExpiries_];
        final double maxSpread = 0.1;
        for (int j = 0; j < nOptionExpiries_; ++j) {
            double minBaseVol = QL_MAX_REAL;
            for (CashFlow cashflow : caps_.get(j).floatingLeg()) {
                FloatingRateCoupon coupon = (FloatingRateCoupon) cashflow;
                minBaseVol = Math.min(minBaseVol,
                        baseVolatility.currentLink().volatility(
                                coupon.fixingDate(),
                                atmCapFloorStrikes_[j], true));
            }
            QL_REQUIRE(minBaseVol != QL_MAX_REAL,
                    "empty cap/floor leg for option expiry "
                            + atmCapFloorTermVolCurve_.optionTenors().get(j));
            QL_REQUIRE(minBaseVol >= 0.0,
                    "negative optionlet volatility at ATM strike "
                            + atmCapFloorStrikes_[j]);

            SimpleQuote spreadQuote = new SimpleQuote(-1.0);
            Handle<Quote> spreadQuoteHandle = new Handle<>(spreadQuote, true);
            Handle<OptionletVolatilityStructure> spreadedVolatility =
                    new Handle<>(new SpreadedOptionletVolatility(
                            baseVolatility, spreadQuoteHandle), true);
            caps_.get(j).setPricingEngine(
                    makeCapFloorPricingEngineWithOptionletVolatility(
                            iborIndex_.forwardingTermStructure(),
                            spreadedVolatility));

            final int expiry = j;
            Function f = s -> {
                if (s != spreadQuote.value())
                    spreadQuote.setValue(s);
                return caps_.get(expiry).NPV() - atmCapFloorPrices_[expiry];
            };
            solver.setMaxEvaluations(maxEvaluations_);
            double valueAtZero = f.value(0.0);
            if (close(valueAtZero, 0.0)) {
                result[j] = 0.0;
            } else if (valueAtZero > 0.0) {
                QL_REQUIRE(minBaseVol > 0.0,
                        "ATM cap/floor price for option expiry "
                                + atmCapFloorTermVolCurve_.optionTenors().get(j)
                                + " is below the minimum attainable price");
                double minSpread = -minBaseVol;
                double valueAtMinSpread = f.value(minSpread);
                QL_REQUIRE(valueAtMinSpread <= 0.0,
                        "ATM cap/floor price for option expiry "
                                + atmCapFloorTermVolCurve_.optionTenors().get(j)
                                + " is below the minimum attainable price");
                result[j] = solver.solve(
                        f, accuracy_, 0.5 * minSpread, minSpread, 0.0);
            } else {
                double valueAtMaxSpread = f.value(maxSpread);
                QL_REQUIRE(valueAtMaxSpread >= 0.0,
                        "ATM cap/floor price for option expiry "
                                + atmCapFloorTermVolCurve_.optionTenors().get(j)
                                + " requires a volatility spread greater than "
                                + maxSpread);
                result[j] = solver.solve(
                        f, accuracy_, 0.5 * maxSpread, 0.0, maxSpread);
            }
        }
        return result;
    }

    public double[] spreadsVol() {
        calculate();
        return spreadsVolImplied_.clone();
    }

    public double[] atmCapFloorStrikes() {
        calculate();
        return atmCapFloorStrikes_.clone();
    }

    public double[] atmCapFloorPrices() {
        calculate();
        return atmCapFloorPrices_.clone();
    }

    private static int lowerBound(List<Double> values, double value) {
        int first = 0;
        int count = values.size();
        while (count > 0) {
            int step = count / 2;
            int it = first + step;
            if (values.get(it) < value) {
                first = it + 1;
                count -= step + 1;
            } else {
                count = step;
            }
        }
        return first;
    }
}
