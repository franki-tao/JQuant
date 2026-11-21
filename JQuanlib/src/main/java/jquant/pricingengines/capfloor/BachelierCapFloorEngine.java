package jquant.pricingengines.capfloor;

import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.instruments.CapFloor;
import jquant.pricingengines.BlackFormula;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.Normal;

/**
 * Bachelier-Black-formula cap/floor engine.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/capfloor/bacheliercapfloorengine.{hpp,cpp}}.
 */
public class BachelierCapFloorEngine extends CapFloor.engine {

    private final Handle<YieldTermStructure> discountCurve_;
    private final Handle<OptionletVolatilityStructure> vol_;

    public BachelierCapFloorEngine(Handle<YieldTermStructure> discountCurve,
                                   double v) {
        this(discountCurve, v, new Actual365Fixed());
    }

    public BachelierCapFloorEngine(Handle<YieldTermStructure> discountCurve,
                                   double v,
                                   DayCounter dc) {
        discountCurve_ = discountCurve;
        vol_ = new Handle<>(new ConstantOptionletVolatility(0,
                new NullCalendar(), BusinessDayConvention.FOLLOWING, v, dc), true);
        registerWith(discountCurve_.get());
    }

    public BachelierCapFloorEngine(Handle<YieldTermStructure> discountCurve,
                                   Handle<Quote> v) {
        this(discountCurve, v, new Actual365Fixed());
    }

    public BachelierCapFloorEngine(Handle<YieldTermStructure> discountCurve,
                                   Handle<Quote> v,
                                   DayCounter dc) {
        discountCurve_ = discountCurve;
        vol_ = new Handle<>(new ConstantOptionletVolatility(0,
                new NullCalendar(), BusinessDayConvention.FOLLOWING, v, dc), true);
        registerWith(discountCurve_.get());
        registerWith(vol_.get());
    }

    /**
     * Java factory mirroring the official constructor taking a
     * {@code Handle<OptionletVolatilityStructure>}; see
     * {@link BlackCapFloorEngine#withVolatility}.
     */
    public static BachelierCapFloorEngine withVolatility(
            Handle<YieldTermStructure> discountCurve,
            Handle<OptionletVolatilityStructure> volatility) {
        return new BachelierCapFloorEngine(discountCurve, volatility, Boolean.TRUE);
    }

    private BachelierCapFloorEngine(Handle<YieldTermStructure> discountCurve,
                                    Handle<OptionletVolatilityStructure> volatility,
                                    Boolean ignored) {
        discountCurve_ = discountCurve;
        vol_ = volatility;
        QL_REQUIRE(vol_.currentLink().volatilityType() == Normal,
                "BachelierCapFloorEngine should only be used for vol "
                        + "surfaces stripped with normal model. Options were stripped "
                        + "with model " + vol_.currentLink().volatilityType());
        registerWith(discountCurve_.get());
        registerWith(vol_.get());
    }

    public Handle<YieldTermStructure> termStructure() {
        return discountCurve_;
    }

    public Handle<OptionletVolatilityStructure> volatility() {
        return vol_;
    }

    @Override
    public void calculate() {
        double value = 0.0;
        double vega = 0.0;
        int optionlets = arguments_.startDates.size();
        List<Double> values = new ArrayList<>(Collections.nCopies(optionlets, 0.0));
        List<Double> deltas = new ArrayList<>(Collections.nCopies(optionlets, 0.0));
        List<Double> vegas = new ArrayList<>(Collections.nCopies(optionlets, 0.0));
        List<Double> stdDevs = new ArrayList<>(Collections.nCopies(optionlets, 0.0));
        List<Double> discountFactors =
                new ArrayList<>(Collections.nCopies(optionlets, 0.0));
        CapFloor.Type type = arguments_.type;
        Date today = vol_.currentLink().referenceDate();
        Date settlement = discountCurve_.currentLink().referenceDate();

        for (int i = 0; i < optionlets; ++i) {
            Date paymentDate = arguments_.endDates.get(i);
            // handling of settlementDate, npvDate and includeSettlementFlows
            // should be implemented.
            // For the time being just discard expired caplets
            if (TimeUtils.greater(paymentDate, settlement)) {
                double d = discountCurve_.currentLink().discount(paymentDate, false);
                discountFactors.set(i, d);
                double accrualFactor = arguments_.nominals.get(i)
                        * arguments_.gearings.get(i)
                        * arguments_.accrualTimes.get(i);
                double discountedAccrual = d * accrualFactor;

                double forward = arguments_.forwards.get(i);

                Date fixingDate = arguments_.fixingDates.get(i);
                double sqrtTime = 0.0;
                if (TimeUtils.greater(fixingDate, today))
                    sqrtTime = Math.sqrt(vol_.currentLink().timeFromReference(fixingDate));

                if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                    double strike = arguments_.capRates.get(i);
                    if (sqrtTime > 0.0) {
                        stdDevs.set(i, Math.sqrt(vol_.currentLink()
                                .blackVariance(fixingDate, strike, false)));
                        vegas.set(i, BlackFormula.bachelierBlackFormulaStdDevDerivative(
                                strike, forward, stdDevs.get(i), discountedAccrual)
                                * sqrtTime);
                        deltas.set(i, BlackFormula.bachelierBlackFormulaAssetItmProbability(
                                Option.Type.Call, strike, forward, stdDevs.get(i)));
                    }
                    // include caplets with past fixing date
                    values.set(i, BlackFormula.bachelierBlackFormula(Option.Type.Call,
                            strike, forward, stdDevs.get(i), discountedAccrual));
                }
                if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                    double strike = arguments_.floorRates.get(i);
                    double floorletVega = 0.0;
                    double floorletDelta = 0.0;
                    if (sqrtTime > 0.0) {
                        stdDevs.set(i, Math.sqrt(vol_.currentLink()
                                .blackVariance(fixingDate, strike, false)));
                        floorletVega = BlackFormula.bachelierBlackFormulaStdDevDerivative(
                                strike, forward, stdDevs.get(i), discountedAccrual)
                                * sqrtTime;
                        floorletDelta = -1.0
                                * BlackFormula.bachelierBlackFormulaAssetItmProbability(
                                Option.Type.Put, strike, forward, stdDevs.get(i));
                    }
                    double floorlet = BlackFormula.bachelierBlackFormula(
                            Option.Type.Put, strike, forward, stdDevs.get(i),
                            discountedAccrual);
                    if (type == CapFloor.Type.Floor) {
                        values.set(i, floorlet);
                        vegas.set(i, floorletVega);
                        deltas.set(i, floorletDelta);
                    } else {
                        // a collar is long a cap and short a floor
                        values.set(i, values.get(i) - floorlet);
                        vegas.set(i, vegas.get(i) - floorletVega);
                        deltas.set(i, deltas.get(i) - floorletDelta);
                    }
                }
                value += values.get(i);
                vega += vegas.get(i);
            }
        }
        results_.value = value;
        results_.additionalResults.put("vega", vega);

        results_.additionalResults.put("optionletsPrice", values);
        results_.additionalResults.put("optionletsVega", vegas);
        results_.additionalResults.put("optionletsDelta", deltas);
        results_.additionalResults.put("optionletsDiscountFactor", discountFactors);
        results_.additionalResults.put("optionletsAtmForward",
                new ArrayList<>(arguments_.forwards));
        if (type != CapFloor.Type.Collar)
            results_.additionalResults.put("optionletsStdDev", stdDevs);
    }
}
