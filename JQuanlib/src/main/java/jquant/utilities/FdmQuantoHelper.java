package jquant.utilities;

import jquant.Compounding;
import jquant.math.Array;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Frequency;

import java.util.HashSet;
import java.util.Set;

public class FdmQuantoHelper implements Observable {
    public YieldTermStructure rTS_, fTS_;
    public BlackVolTermStructure fxVolTS_;
    public double equityFxCorrelation_;
    public double exchRateATMlevel_;
    private final Set<Observer> observers = new HashSet<>();

    public FdmQuantoHelper(YieldTermStructure rTS,
                           YieldTermStructure fTS,
                           BlackVolTermStructure fxVolTS,
                           double equityFxCorrelation,
                           double exchRateATMlevel) {
        rTS_ = rTS;
        fTS_ = fTS;
        fxVolTS_ = fxVolTS;
        equityFxCorrelation_ = equityFxCorrelation;
        exchRateATMlevel_ = exchRateATMlevel;
    }

    public double quantoAdjustment(double equityVol,
                                   double t1, double t2) {
        final double rDomestic = rTS_.forwardRate(t1, t2, Compounding.Continuous, Frequency.ANNUAL, false).rate();
        final double rForeign = fTS_.forwardRate(t1, t2, Compounding.Continuous, Frequency.ANNUAL, false).rate();
        final double fxVol
                = fxVolTS_.blackForwardVol(t1, t2, exchRateATMlevel_, false);

        return rDomestic - rForeign + equityVol * fxVol * equityFxCorrelation_;
    }

    public Array quantoAdjustment(final Array equityVol, double t1, double t2) {

        final double rDomestic = rTS_.forwardRate(t1, t2, Compounding.Continuous, Frequency.ANNUAL, false).rate();
        final double rForeign = fTS_.forwardRate(t1, t2, Compounding.Continuous, Frequency.ANNUAL, false).rate();
        final double fxVol
                = fxVolTS_.blackForwardVol(t1, t2, exchRateATMlevel_, false);

        Array retVal = new Array(equityVol.size());
        for (int i = 0; i < retVal.size(); ++i) {
            retVal.set(i, rDomestic - rForeign + equityVol.get(i) * fxVol * equityFxCorrelation_);
        }
        return retVal;
    }

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers);
        } else {
            for (Observer observer : observers) {
                try {
                    observer.update();
                } catch (Exception e) {
                    System.err.println("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }
}
