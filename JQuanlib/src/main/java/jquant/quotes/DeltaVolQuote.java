package jquant.quotes;

import jquant.Handle;
import jquant.Quote;
import jquant.patterns.Observable;
import jquant.patterns.Observer;

import java.util.HashSet;
import java.util.Set;

/**
 * Class for the quotation of delta vs vol.
 *
 * <p>Java port of {@code DeltaVolQuote} from
 * {@code ql/quotes/deltavolquote.{hpp,cpp}}. It includes the various delta
 * quotation types used in FX markets as well as ATM types.
 */
public class DeltaVolQuote extends Quote implements Observer {

    /** Delta quotation type. */
    public enum DeltaType {
        /** Spot Delta, e.g. usual Black Scholes delta. */
        Spot,
        /** Forward Delta. */
        Fwd,
        /** Premium Adjusted Spot Delta. */
        PaSpot,
        /** Premium Adjusted Forward Delta. */
        PaFwd
    }

    /** ATM quotation type. */
    public enum AtmType {
        /** Default, if not an atm quote. */
        AtmNull,
        /** K = S_0. */
        AtmSpot,
        /** K = F. */
        AtmFwd,
        /** Call Delta = Put Delta. */
        AtmDeltaNeutral,
        /** K such that Vega is Maximum. */
        AtmVegaMax,
        /** K such that Gamma is Maximum. */
        AtmGammaMax,
        /** K such that Call Delta = 0.50 (only for Fwd Delta). */
        AtmPutCall50
    }

    private final double delta_;
    private final Handle<Quote> vol_;
    private final DeltaType deltaType_;
    private final double maturity_;
    private final AtmType atmType_;
    private final Set<Observable> registeredObservables = new HashSet<>();

    /** Standard constructor delta vs vol. */
    public DeltaVolQuote(double delta,
                         Handle<Quote> vol,
                         double maturity,
                         DeltaType deltaType) {
        delta_ = delta;
        vol_ = vol;
        deltaType_ = deltaType;
        maturity_ = maturity;
        atmType_ = AtmType.AtmNull;

        registerWith(vol_ == null ? null : vol_.get()); // observe vol
    }

    /** Additional constructor, if special atm quote is used. */
    public DeltaVolQuote(Handle<Quote> vol,
                         DeltaType deltaType,
                         double maturity,
                         AtmType atmType) {
        delta_ = 0.0;
        vol_ = vol;
        deltaType_ = deltaType;
        maturity_ = maturity;
        atmType_ = atmType;

        registerWith(vol_ == null ? null : vol_.get());
    }

    @Override
    public double value() {
        return vol_.getValue().value();
    }

    public double delta() {
        return delta_;
    }

    public double maturity() {
        return maturity_;
    }

    @Override
    public boolean isValid() {
        return !vol_.empty() && vol_.getValue().isValid();
    }

    @Override
    public void update() {
        notifyObservers(); // let observers know that something has changed
    }

    public AtmType atmType() {
        return atmType_;
    }

    public DeltaType deltaType() {
        return deltaType_;
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable observable) {
        if (observable != null) {
            observable.unregisterObserver(this);
            registeredObservables.remove(observable);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable observable : registeredObservables) {
            observable.unregisterObserver(this);
        }
        registeredObservables.clear();
    }
}
