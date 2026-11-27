package jquant.models;

import jquant.Handle;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.patterns.LazyObject;
import jquant.termstructures.volatility.Sarb.VolatilityType;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Liquid Black76 market instrument used during calibration.
 *
 * <p>Official QuantLib {@code ql/models/calibrationhelper.{hpp,cpp}} port.
 */
public abstract class BlackCalibrationHelper extends LazyObject
        implements CalibrationHelper {

    public enum CalibrationErrorType {
        RelativePriceError, PriceError, ImpliedVolError
    }

    protected double marketValue_;
    protected Handle<Quote> volatility_;
    protected PricingEngine engine_;
    protected final VolatilityType volatilityType_;
    protected final double shift_;
    private final CalibrationErrorType calibrationErrorType_;

    public BlackCalibrationHelper(Handle<Quote> volatility,
                                  CalibrationErrorType calibrationErrorType,
                                  VolatilityType type,
                                  double shift) {
        volatility_ = volatility;
        volatilityType_ = type;
        shift_ = shift;
        calibrationErrorType_ = calibrationErrorType;
        registerWith(volatility_.get());
    }

    public BlackCalibrationHelper(Handle<Quote> volatility) {
        this(volatility, CalibrationErrorType.RelativePriceError,
                ShiftedLognormal, 0.0);
    }

    @Override
    protected void performCalculations() {
        marketValue_ = blackPrice(volatility_.currentLink().value());
    }

    /** Returns the volatility handle. */
    public Handle<Quote> volatility() {
        return volatility_;
    }

    /** Returns the volatility type. */
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    /** Returns the actual price of the instrument (from volatility). */
    public double marketValue() {
        calculate();
        return marketValue_;
    }

    /** Returns the price of the instrument according to the model. */
    public abstract double modelValue();

    /** Returns the error resulting from the model valuation. */
    @Override
    public double calibrationError() {
        double error;

        switch (calibrationErrorType_) {
            case RelativePriceError:
                error = Math.abs(marketValue() - modelValue()) / marketValue();
                break;
            case PriceError:
                error = marketValue() - modelValue();
                break;
            case ImpliedVolError: {
                double minVol = volatilityType_ == ShiftedLognormal ? 0.0010 : 0.00005;
                double maxVol = volatilityType_ == ShiftedLognormal ? 10.0 : 0.50;
                final double lowerPrice = blackPrice(minVol);
                final double upperPrice = blackPrice(maxVol);
                final double modelPrice = modelValue();

                double implied;
                if (modelPrice <= lowerPrice)
                    implied = minVol;
                else if (modelPrice >= upperPrice)
                    implied = maxVol;
                else
                    implied = impliedVolatility(modelPrice, 1e-12, 5000, minVol, maxVol);
                error = implied - volatility_.currentLink().value();
                break;
            }
            default:
                QL_FAIL("unknown Calibration Error Type");
                throw new IllegalStateException("unreachable");
        }

        return error;
    }

    /** Black volatility implied by the model. */
    public double impliedVolatility(double targetValue,
                                    double accuracy,
                                    int maxEvaluations,
                                    double minVol,
                                    double maxVol) {
        Function error = x -> targetValue - blackPrice(x);
        Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(error, accuracy,
                volatility_.currentLink().value(), minVol, maxVol);
    }

    /** Black or Bachelier price given a volatility. */
    public abstract double blackPrice(double volatility);

    public void setPricingEngine(PricingEngine engine) {
        engine_ = engine;
    }
}
