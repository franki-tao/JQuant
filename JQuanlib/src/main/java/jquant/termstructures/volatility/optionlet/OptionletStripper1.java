package jquant.termstructures.volatility.optionlet;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.Settings;
import jquant.cashflows.FloatingRateCoupon;
import jquant.indexes.IborIndex;
import jquant.instruments.CapFloor;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.capfloor.CapFloorTermVolSurface;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;

import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Helper class to strip optionlet (i.e. caplet/floorlet) volatilities
 * (a.k.a. forward-forward volatilities) from the (cap/floor) term
 * volatilities of a CapFloorTermVolSurface.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/optionlet/optionletstripper1.{hpp,cpp}}.
 */
public class OptionletStripper1 extends OptionletStripper {

    private final double[][] capFloorPrices_;
    private final double[][] optionletPrices_;
    private final double[][] capFloorVols_;
    private final double[][] optionletStDevs_;
    private final double[][] capletVols_;

    private final boolean floatingSwitchStrike_;

    private double switchStrike_;
    private final double accuracy_;
    private final int maxIter_;
    private final boolean dontThrow_;

    public OptionletStripper1(CapFloorTermVolSurface termVolSurface,
                              IborIndex index,
                              Double switchStrike,
                              double accuracy) {
        this(termVolSurface, index, switchStrike, accuracy, 100, null,
                Sarb.VolatilityType.ShiftedLognormal, 0.0, false, 0);
    }

    public OptionletStripper1(CapFloorTermVolSurface termVolSurface,
                              IborIndex index,
                              Double switchStrike,
                              double accuracy,
                              int maxIter,
                              Handle<YieldTermStructure> discount,
                              Sarb.VolatilityType type,
                              double displacement,
                              boolean dontThrow,
                              int paymentLag) {
        super(termVolSurface, index, discount, type, displacement, paymentLag);
        floatingSwitchStrike_ = (switchStrike == null);
        switchStrike_ = floatingSwitchStrike_ ? 0.0 : switchStrike;
        accuracy_ = accuracy;
        maxIter_ = maxIter;
        dontThrow_ = dontThrow;

        capFloorPrices_ = new double[nOptionletTenors_][nStrikes_];
        optionletPrices_ = new double[nOptionletTenors_][nStrikes_];
        capletVols_ = new double[nOptionletTenors_][nStrikes_];
        capFloorVols_ = new double[nOptionletTenors_][nStrikes_];

        double firstGuess = 0.14; // guess is only used for shifted lognormal vols
        optionletStDevs_ = new double[nOptionletTenors_][nStrikes_];
        for (int i = 0; i < nOptionletTenors_; ++i)
            for (int j = 0; j < nStrikes_; ++j)
                optionletStDevs_[i][j] = firstGuess;
    }

    @Override
    public void performCalculations() {

        // update dates
        Date referenceDate = termVolSurface_.referenceDate();
        DayCounter dc = termVolSurface_.dayCounter();

        PricingEngine dummy = new BlackCapFloorEngine(
                iborIndex_.forwardingTermStructure(), 0.20, dc);
        for (int i = 0; i < nOptionletTenors_; ++i) {
            CapFloor temp = makeCapFloor(CapFloor.Type.Cap, i, 0.04, dummy);
            FloatingRateCoupon lFRC = temp.lastFloatingRateCoupon();
            optionletDates_.set(i, lFRC.fixingDate());
            optionletPaymentDates_.set(i, lFRC.date());
            optionletAccrualPeriods_[i] = lFRC.accrualPeriod();
            optionletTimes_[i] = dc.yearFraction(referenceDate,
                    optionletDates_.get(i), new Date(), new Date());
            atmOptionletRate_[i] = lFRC.indexFixing();
        }

        if (floatingSwitchStrike_) {
            double averageAtmOptionletRate = 0.0;
            for (int i = 0; i < nOptionletTenors_; ++i)
                averageAtmOptionletRate += atmOptionletRate_[i];
            switchStrike_ = averageAtmOptionletRate / nOptionletTenors_;
        }

        Handle<YieldTermStructure> discountCurve =
                (discount_ == null || discount_.empty())
                        ? iborIndex_.forwardingTermStructure()
                        : discount_;

        double[] strikes = termVolSurface_.strikes();

        SimpleQuote volQuote = new SimpleQuote();
        Handle<Quote> volHandle = new Handle<>(volQuote, true);
        PricingEngine capFloorEngine = makeCapFloorPricingEngine(
                discountCurve, volHandle);

        for (int j = 0; j < nStrikes_; ++j) {
            // using out-of-the-money options
            CapFloor.Type capFloorType = strikes[j] < switchStrike_
                    ? CapFloor.Type.Floor : CapFloor.Type.Cap;
            Option.Type optionletType = strikes[j] < switchStrike_
                    ? Option.Type.Put : Option.Type.Call;

            double previousCapFloorPrice = 0.0;
            for (int i = 0; i < nOptionletTenors_; ++i) {
                capFloorVols_[i][j] = termVolSurface_.volatility(
                        capFloorLengths_.get(i), strikes[j], true);
                volQuote.setValue(capFloorVols_[i][j]);
                CapFloor capFloor = makeCapFloor(capFloorType, i, strikes[j],
                        capFloorEngine);
                capFloorPrices_[i][j] = capFloor.NPV();
                optionletPrices_[i][j] = capFloorPrices_[i][j]
                        - previousCapFloorPrice;
                previousCapFloorPrice = capFloorPrices_[i][j];
                double d = discountCurve.currentLink().discount(
                        optionletPaymentDates_.get(i));
                double optionletAnnuity = optionletAccrualPeriods_[i] * d;
                try {
                    if (volatilityType() == Sarb.VolatilityType.ShiftedLognormal) {
                        optionletStDevs_[i][j] = BlackFormula
                                .blackFormulaImpliedStdDev(optionletType,
                                        strikes[j], atmOptionletRate_[i],
                                        optionletPrices_[i][j], optionletAnnuity,
                                        displacement(), optionletStDevs_[i][j],
                                        accuracy_, maxIter_);
                    } else {
                        QL_FAIL("Unknown volatility type: " + volatilityType());
                    }
                } catch (RuntimeException e) {
                    if (dontThrow_)
                        optionletStDevs_[i][j] = 0.0;
                    else
                        QL_FAIL("could not bootstrap optionlet:"
                                + "\n type:    " + optionletType
                                + "\n strike:  " + strikes[j]
                                + "\n atm:     " + atmOptionletRate_[i]
                                + "\n price:   " + optionletPrices_[i][j]
                                + "\n annuity: " + optionletAnnuity
                                + "\n expiry:  " + optionletDates_.get(i)
                                + "\n error:   " + e.getMessage());
                }
                optionletVolatilities_.get(i).set(j,
                        optionletStDevs_[i][j] / Math.sqrt(optionletTimes_[i]));
            }
        }
    }

    private CapFloor makeCapFloor(CapFloor.Type capFloorType,
                                  int optionletIndex,
                                  double strike,
                                  PricingEngine engine) {
        List<CashFlow> leg = makeCapFloorLeg(capFloorLengths_.get(optionletIndex));
        CapFloor capFloor = new CapFloor(capFloorType, leg,
                Collections.singletonList(strike));
        capFloor.setPricingEngine(engine);
        return capFloor;
    }

    public double[][] capletVols() {
        calculate();
        return capletVols_;
    }

    public double[][] capFloorPrices() {
        calculate();
        return capFloorPrices_;
    }

    public double[][] capFloorVolatilities() {
        calculate();
        return capFloorVols_;
    }

    public double[][] optionletPrices() {
        calculate();
        return optionletPrices_;
    }

    public double switchStrike() {
        if (floatingSwitchStrike_)
            calculate();
        return switchStrike_;
    }
}
