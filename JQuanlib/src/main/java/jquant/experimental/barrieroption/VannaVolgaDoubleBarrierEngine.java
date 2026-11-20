package jquant.experimental.barrieroption;

import jquant.Compounding;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.Settings;
import jquant.math.Array;
import jquant.math.Interpolation;
import jquant.math.Matrix;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;
import jquant.pricingengines.BlackDeltaCalculator;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.barrier.AnalyticDoubleBarrierEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.DeltaVolQuote;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Vanna/Volga double-barrier option engine.
 *
 * <p>Java port of {@code VannaVolgaDoubleBarrierEngine<DoubleBarrierEngine>}
 * from
 * {@code ql/experimental/barrieroption/vannavolgadoublebarrierengine.hpp}. The
 * official engine is templated on the Black-Scholes double-barrier engine; in
 * Java the choice is expressed with {@link Kernel}.
 */
public class VannaVolgaDoubleBarrierEngine extends DoubleBarrierOption.engine {

    /** Black-Scholes double-barrier engine used inside the Vanna-Volga method. */
    public enum Kernel {
        SUO_WANG,
        ANALYTIC
    }

    private final Handle<DeltaVolQuote> atmVol_;
    private final Handle<DeltaVolQuote> vol25Put_;
    private final Handle<DeltaVolQuote> vol25Call_;
    private final double T_;
    private final Handle<Quote> spotFX_;
    private final Handle<YieldTermStructure> domesticTS_;
    private final Handle<YieldTermStructure> foreignTS_;
    private final boolean adaptVanDelta_;
    private final double bsPriceWithSmile_;
    private final Kernel kernel_;
    private final int series_;

    public VannaVolgaDoubleBarrierEngine(Handle<DeltaVolQuote> atmVol,
                                         Handle<DeltaVolQuote> vol25Put,
                                         Handle<DeltaVolQuote> vol25Call,
                                         Handle<Quote> spotFX,
                                         Handle<YieldTermStructure> domesticTS,
                                         Handle<YieldTermStructure> foreignTS,
                                         boolean adaptVanDelta,
                                         double bsPriceWithSmile,
                                         Kernel kernel,
                                         int series) {
        atmVol_ = atmVol;
        vol25Put_ = vol25Put;
        vol25Call_ = vol25Call;
        T_ = atmVol_.getValue().maturity();
        spotFX_ = spotFX;
        domesticTS_ = domesticTS;
        foreignTS_ = foreignTS;
        adaptVanDelta_ = adaptVanDelta;
        bsPriceWithSmile_ = bsPriceWithSmile;
        kernel_ = kernel;
        series_ = series;

        QL_REQUIRE(vol25Put_.getValue().delta() == -0.25,
                "25 delta put is required by vanna volga method");
        QL_REQUIRE(vol25Call_.getValue().delta() == 0.25,
                "25 delta call is required by vanna volga method");

        QL_REQUIRE(vol25Put_.getValue().maturity() == vol25Call_.getValue().maturity()
                        && vol25Put_.getValue().maturity() == atmVol_.getValue().maturity(),
                "Maturity of 3 vols are not the same");

        QL_REQUIRE(!domesticTS_.empty(), "domestic yield curve is not defined");
        QL_REQUIRE(!foreignTS_.empty(), "foreign yield curve is not defined");

        registerWith(atmVol_.get());
        registerWith(vol25Put_.get());
        registerWith(vol25Call_.get());
        registerWith(spotFX_.get());
        registerWith(domesticTS_.get());
        registerWith(foreignTS_.get());
    }

    public VannaVolgaDoubleBarrierEngine(Handle<DeltaVolQuote> atmVol,
                                         Handle<DeltaVolQuote> vol25Put,
                                         Handle<DeltaVolQuote> vol25Call,
                                         Handle<Quote> spotFX,
                                         Handle<YieldTermStructure> domesticTS,
                                         Handle<YieldTermStructure> foreignTS,
                                         boolean adaptVanDelta,
                                         double bsPriceWithSmile,
                                         Kernel kernel) {
        this(atmVol, vol25Put, vol25Call, spotFX, domesticTS, foreignTS,
                adaptVanDelta, bsPriceWithSmile, kernel, 5);
    }

    @Override
    public void calculate() {

        final double sigmaShift_vega = 0.001;
        final double sigmaShift_volga = 0.0001;
        final double spotShift_delta = 0.0001 * spotFX_.getValue().value();
        final double sigmaShift_vanna = 0.0001;

        QL_REQUIRE(arguments_.barrierType == DoubleBarrier.Type.KnockIn
                        || arguments_.barrierType == DoubleBarrier.Type.KnockOut,
                "Only same type barrier supported");

        Handle<Quote> x0Quote = new Handle<>(
                new SimpleQuote(spotFX_.getValue().value()), true); // used for shift
        Handle<Quote> atmVolQuote = new Handle<>(
                new SimpleQuote(atmVol_.getValue().value()), true); // used for shift

        BlackVolTermStructure blackVolTS = new BlackConstantVol(
                Settings.instance.evaluationDate().Date(),
                new NullCalendar(), atmVolQuote, new Actual365Fixed());
        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                x0Quote,
                foreignTS_,
                domesticTS_,
                new Handle<>(blackVolTS, true));

        PricingEngine engineBS;
        switch (kernel_) {
            case SUO_WANG:
                engineBS = new SuoWangDoubleBarrierEngine(stochProcess, series_);
                break;
            case ANALYTIC:
            default:
                engineBS = new AnalyticDoubleBarrierEngine(stochProcess, series_);
                break;
        }

        BlackDeltaCalculator blackDeltaCalculatorAtm = new BlackDeltaCalculator(
                Option.Type.Call, atmVol_.getValue().deltaType(),
                x0Quote.getValue().value(),
                domesticTS_.getValue().discount(T_),
                foreignTS_.getValue().discount(T_),
                atmVol_.getValue().value() * Math.sqrt(T_));
        double atmStrike = blackDeltaCalculatorAtm.atmStrike(
                atmVol_.getValue().atmType());

        double call25Vol = vol25Call_.getValue().value();
        double put25Vol = vol25Put_.getValue().value();

        BlackDeltaCalculator blackDeltaCalculatorPut25 = new BlackDeltaCalculator(
                Option.Type.Put, vol25Put_.getValue().deltaType(),
                x0Quote.getValue().value(),
                domesticTS_.getValue().discount(T_),
                foreignTS_.getValue().discount(T_),
                put25Vol * Math.sqrt(T_));
        double put25Strike = blackDeltaCalculatorPut25.strikeFromDelta(-0.25);
        BlackDeltaCalculator blackDeltaCalculatorCall25 = new BlackDeltaCalculator(
                Option.Type.Call, vol25Call_.getValue().deltaType(),
                x0Quote.getValue().value(),
                domesticTS_.getValue().discount(T_),
                foreignTS_.getValue().discount(T_),
                call25Vol * Math.sqrt(T_));
        double call25Strike = blackDeltaCalculatorCall25.strikeFromDelta(0.25);

        // here use vanna volga interpolated smile to price vanilla
        double[] strikes = new double[3];
        double[] vols = new double[3];
        strikes[0] = put25Strike;
        vols[0] = put25Vol;
        strikes[1] = atmStrike;
        vols[1] = atmVol_.getValue().value();
        strikes[2] = call25Strike;
        vols[2] = call25Vol;
        // Official quirk: the foreign discount is passed twice.
        VannaVolgaInterpolation.VannaVolga vannaVolga =
                new VannaVolgaInterpolation.VannaVolga(
                        x0Quote.getValue().value(),
                        foreignTS_.getValue().discount(T_),
                        foreignTS_.getValue().discount(T_), T_);
        Interpolation interpolation = vannaVolga.interpolate(strikes, vols);
        interpolation.enableExtrapolation();
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;
        double strikeVol = interpolation.value(payoff.strike(), true);

        // vanilla option price
        double forward = x0Quote.getValue().value()
                * foreignTS_.getValue().discount(T_)
                / domesticTS_.getValue().discount(T_);
        double vanillaOption = BlackFormula.blackFormula(payoff.optionType(),
                payoff.strike(),
                forward,
                strikeVol * Math.sqrt(T_),
                domesticTS_.getValue().discount(T_), 0.0);

        // already out
        if ((x0Quote.getValue().value() > arguments_.barrier_hi
                || x0Quote.getValue().value() < arguments_.barrier_lo)
                && arguments_.barrierType == DoubleBarrier.Type.KnockOut) {
            results_.value = 0.0;
            results_.additionalResults.put("VanillaPrice",
                    adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption);
            results_.additionalResults.put("BarrierInPrice",
                    adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption);
            results_.additionalResults.put("BarrierOutPrice", 0.0);
        }
        // already in
        else if ((x0Quote.getValue().value() > arguments_.barrier_hi
                || x0Quote.getValue().value() < arguments_.barrier_lo)
                && arguments_.barrierType == DoubleBarrier.Type.KnockIn) {
            results_.value = adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption;
            results_.additionalResults.put("VanillaPrice",
                    adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption);
            results_.additionalResults.put("BarrierInPrice",
                    adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption);
            results_.additionalResults.put("BarrierOutPrice", 0.0);
        } else {

            // set up BS barrier option pricing
            // only calculate out barrier option price
            // in barrier price = vanilla - out barrier
            DoubleBarrierOption doubleBarrierOption = new DoubleBarrierOption(
                    DoubleBarrier.Type.KnockOut,
                    arguments_.barrier_lo,
                    arguments_.barrier_hi,
                    arguments_.rebate,
                    payoff,
                    arguments_.exercise);

            doubleBarrierOption.setPricingEngine(engineBS);

            // BS price
            double priceBS = doubleBarrierOption.NPV();

            double priceAtmCallBS = BlackFormula.blackFormula(Option.Type.Call,
                    atmStrike,
                    forward,
                    atmVol_.getValue().value() * Math.sqrt(T_),
                    domesticTS_.getValue().discount(T_), 0.0);
            double price25CallBS = BlackFormula.blackFormula(Option.Type.Call,
                    call25Strike,
                    forward,
                    atmVol_.getValue().value() * Math.sqrt(T_),
                    domesticTS_.getValue().discount(T_), 0.0);
            double price25PutBS = BlackFormula.blackFormula(Option.Type.Put,
                    put25Strike,
                    forward,
                    atmVol_.getValue().value() * Math.sqrt(T_),
                    domesticTS_.getValue().discount(T_), 0.0);

            // market price
            double priceAtmCallMkt = BlackFormula.blackFormula(Option.Type.Call,
                    atmStrike,
                    forward,
                    atmVol_.getValue().value() * Math.sqrt(T_),
                    domesticTS_.getValue().discount(T_), 0.0);
            double price25CallMkt = BlackFormula.blackFormula(Option.Type.Call,
                    call25Strike,
                    forward,
                    call25Vol * Math.sqrt(T_),
                    domesticTS_.getValue().discount(T_), 0.0);
            double price25PutMkt = BlackFormula.blackFormula(Option.Type.Put,
                    put25Strike,
                    forward,
                    put25Vol * Math.sqrt(T_),
                    domesticTS_.getValue().discount(T_), 0.0);

            // Analytical Black Scholes formula
            NormalDistribution norm = new NormalDistribution();
            double d1atm = (Math.log(forward / atmStrike)
                    + 0.5 * atmVolQuote.getValue().value() * atmVolQuote.getValue().value()
                    * T_) / (atmVolQuote.getValue().value() * Math.sqrt(T_));
            double vegaAtm_Analytical = x0Quote.getValue().value() * norm.value(d1atm)
                    * Math.sqrt(T_) * foreignTS_.getValue().discount(T_);
            double vannaAtm_Analytical = vegaAtm_Analytical
                    / x0Quote.getValue().value()
                    * (1.0 - d1atm / (atmVolQuote.getValue().value() * Math.sqrt(T_)));
            double volgaAtm_Analytical = vegaAtm_Analytical * d1atm
                    * (d1atm - atmVolQuote.getValue().value() * Math.sqrt(T_))
                    / atmVolQuote.getValue().value();

            double d125call = (Math.log(forward / call25Strike)
                    + 0.5 * atmVolQuote.getValue().value()
                    * atmVolQuote.getValue().value() * T_)
                    / (atmVolQuote.getValue().value() * Math.sqrt(T_));
            double vega25Call_Analytical = x0Quote.getValue().value()
                    * norm.value(d125call) * Math.sqrt(T_)
                    * foreignTS_.getValue().discount(T_);
            double vanna25Call_Analytical = vega25Call_Analytical
                    / x0Quote.getValue().value()
                    * (1.0 - d125call / (atmVolQuote.getValue().value()
                    * Math.sqrt(T_)));
            double volga25Call_Analytical = vega25Call_Analytical * d125call
                    * (d125call - atmVolQuote.getValue().value() * Math.sqrt(T_))
                    / atmVolQuote.getValue().value();

            double d125Put = (Math.log(forward / put25Strike)
                    + 0.5 * atmVolQuote.getValue().value()
                    * atmVolQuote.getValue().value() * T_)
                    / (atmVolQuote.getValue().value() * Math.sqrt(T_));
            double vega25Put_Analytical = x0Quote.getValue().value()
                    * norm.value(d125Put) * Math.sqrt(T_)
                    * foreignTS_.getValue().discount(T_);
            double vanna25Put_Analytical = vega25Put_Analytical
                    / x0Quote.getValue().value()
                    * (1.0 - d125Put / (atmVolQuote.getValue().value()
                    * Math.sqrt(T_)));
            double volga25Put_Analytical = vega25Put_Analytical * d125Put
                    * (d125Put - atmVolQuote.getValue().value() * Math.sqrt(T_))
                    / atmVolQuote.getValue().value();

            // BS vega
            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value() + sigmaShift_vega);
            doubleBarrierOption.recalculate();
            double vegaBarBS = (doubleBarrierOption.NPV() - priceBS) / sigmaShift_vega;

            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value() - sigmaShift_vega); // setback

            // BS volga

            // vegaBar2
            // base NPV
            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value() + sigmaShift_volga);
            doubleBarrierOption.recalculate();
            double priceBS2 = doubleBarrierOption.NPV();

            // shifted npv
            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value() + sigmaShift_vega);
            doubleBarrierOption.recalculate();
            double vegaBarBS2 = (doubleBarrierOption.NPV() - priceBS2) / sigmaShift_vega;
            double volgaBarBS = (vegaBarBS2 - vegaBarBS) / sigmaShift_volga;

            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value()
                            - sigmaShift_volga
                            - sigmaShift_vega); // setback

            // BS Delta
            // base delta
            ((SimpleQuote) x0Quote.currentLink()).setValue(
                    x0Quote.getValue().value() + spotShift_delta); // shift forth
            doubleBarrierOption.recalculate();
            double priceBS_delta1 = doubleBarrierOption.NPV();

            ((SimpleQuote) x0Quote.currentLink()).setValue(
                    x0Quote.getValue().value() - 2 * spotShift_delta); // shift back
            doubleBarrierOption.recalculate();
            double priceBS_delta2 = doubleBarrierOption.NPV();

            ((SimpleQuote) x0Quote.currentLink()).setValue(
                    x0Quote.getValue().value() + spotShift_delta); // set back
            double deltaBar1 = (priceBS_delta1 - priceBS_delta2) / (2.0 * spotShift_delta);

            // shifted vanna
            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value() + sigmaShift_vanna); // shift sigma
            // shifted delta
            ((SimpleQuote) x0Quote.currentLink()).setValue(
                    x0Quote.getValue().value() + spotShift_delta); // shift forth
            doubleBarrierOption.recalculate();
            priceBS_delta1 = doubleBarrierOption.NPV();

            ((SimpleQuote) x0Quote.currentLink()).setValue(
                    x0Quote.getValue().value() - 2 * spotShift_delta); // shift back
            doubleBarrierOption.recalculate();
            priceBS_delta2 = doubleBarrierOption.NPV();

            ((SimpleQuote) x0Quote.currentLink()).setValue(
                    x0Quote.getValue().value() + spotShift_delta); // set back
            double deltaBar2 = (priceBS_delta1 - priceBS_delta2) / (2.0 * spotShift_delta);

            double vannaBarBS = (deltaBar2 - deltaBar1) / sigmaShift_vanna;

            ((SimpleQuote) atmVolQuote.currentLink()).setValue(
                    atmVolQuote.getValue().value() - sigmaShift_vanna); // set back

            // Matrix
            Matrix A = new Matrix(3, 3, 0.0);

            // analytical
            A.set(0, 0, vegaAtm_Analytical);
            A.set(0, 1, vega25Call_Analytical);
            A.set(0, 2, vega25Put_Analytical);
            A.set(1, 0, vannaAtm_Analytical);
            A.set(1, 1, vanna25Call_Analytical);
            A.set(1, 2, vanna25Put_Analytical);
            A.set(2, 0, volgaAtm_Analytical);
            A.set(2, 1, volga25Call_Analytical);
            A.set(2, 2, volga25Put_Analytical);

            Array b = new Array(3, 0.0);
            b.set(0, vegaBarBS);
            b.set(1, vannaBarBS);
            b.set(2, volgaBarBS);

            Array q = A.inverse().mutiply(b);

            double H = arguments_.barrier_hi;
            double L = arguments_.barrier_lo;
            double theta_tilt_minus = ((domesticTS_.getValue()
                    .zeroRate(T_, Compounding.Continuous, Frequency.ANNUAL, false).rate()
                    - foreignTS_.getValue()
                    .zeroRate(T_, Compounding.Continuous, Frequency.ANNUAL, false).rate())
                    / atmVol_.getValue().value()
                    - atmVol_.getValue().value() / 2.0)
                    * Math.sqrt(T_);
            double h = 1.0 / atmVol_.getValue().value()
                    * Math.log(H / x0Quote.getValue().value()) / Math.sqrt(T_);
            double l = 1.0 / atmVol_.getValue().value()
                    * Math.log(L / x0Quote.getValue().value()) / Math.sqrt(T_);
            CumulativeNormalDistribution cnd = new CumulativeNormalDistribution();

            double doubleNoTouch = 0.0;
            for (int j = -series_; j < series_; j++) {
                double e_minus = 2 * j * (h - l) - theta_tilt_minus;
                doubleNoTouch +=
                        Math.exp(-2.0 * j * theta_tilt_minus * (h - l))
                                * (cnd.value(h + e_minus) - cnd.value(l + e_minus))
                                - Math.exp(-2.0 * j * theta_tilt_minus * (h - l)
                                + 2.0 * theta_tilt_minus * h)
                                * (cnd.value(h - 2.0 * h + e_minus)
                                - cnd.value(l - 2.0 * h + e_minus));
            }

            double p_survival = doubleNoTouch;

            double lambda = p_survival;
            double adjust = q.get(0) * (priceAtmCallMkt - priceAtmCallBS)
                    + q.get(1) * (price25CallMkt - price25CallBS)
                    + q.get(2) * (price25PutMkt - price25PutBS);
            double outPrice = priceBS + lambda * adjust; //
            double inPrice;

            // adapt Vanilla delta
            if (adaptVanDelta_) {
                outPrice += lambda * (bsPriceWithSmile_ - vanillaOption);
                // capfloored by (0, vanilla)
                outPrice = Math.max(0.0, Math.min(bsPriceWithSmile_, outPrice));
                inPrice = bsPriceWithSmile_ - outPrice;
            } else {
                // capfloored by (0, vanilla)
                outPrice = Math.max(0.0, Math.min(vanillaOption, outPrice));
                inPrice = vanillaOption - outPrice;
            }

            if (arguments_.barrierType == DoubleBarrier.Type.KnockOut)
                results_.value = outPrice;
            else
                results_.value = inPrice;
            results_.additionalResults.put("VanillaPrice", vanillaOption);
            results_.additionalResults.put("BarrierInPrice", inPrice);
            results_.additionalResults.put("BarrierOutPrice", outPrice);
            results_.additionalResults.put("lambda", lambda);
        }
    }
}
