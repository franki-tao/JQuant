package jquant.termstructures.yield;

import jquant.Compounding;
import jquant.Handle;
import jquant.InterestRate;
import jquant.Quote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Term structure with an added spread on the zero yield rate.
 *
 * <p>Port of QuantLib's {@code ZeroSpreadedTermStructure} in
 * {@code ql/termstructures/yield/zerospreadedtermstructure.hpp}. The
 * structure remains linked to the original curve, so changes in either the
 * curve or the spread are reflected here.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/zerospreadedtermstructure.hpp
 */
public class ZeroSpreadedTermStructure extends YieldTermStructure {
    private final Handle<YieldTermStructure> originalCurve_;
    private final Handle<Quote> spread_;
    private final Compounding comp_;
    private final Frequency freq_;

    public ZeroSpreadedTermStructure(Handle<YieldTermStructure> originalCurve,
                                     Handle<Quote> spread) {
        this(originalCurve, spread, Compounding.Continuous, Frequency.NO_FREQUENCY);
    }

    public ZeroSpreadedTermStructure(Handle<YieldTermStructure> originalCurve,
                                     Handle<Quote> spread,
                                     Compounding comp,
                                     Frequency freq) {
        // The underlying curve may still be empty here (as in the official
        // multi-curve tests); all accessors delegate to it dynamically.
        super((DayCounter) null);
        QL_REQUIRE(originalCurve != null, "null original curve handle");
        QL_REQUIRE(spread != null, "null spread handle");
        originalCurve_ = originalCurve;
        spread_ = spread;
        comp_ = comp;
        freq_ = freq;
        registerWith(originalCurve_.get());
        registerWith(spread_.getValue());
    }

    @Override
    public DayCounter dayCounter() {
        return originalCurve_.currentLink().dayCounter();
    }

    @Override
    public Date referenceDate() {
        return originalCurve_.currentLink().referenceDate();
    }

    @Override
    public Calendar calendar() {
        return originalCurve_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return originalCurve_.currentLink().settlementDays();
    }

    @Override
    public Date maxDate() {
        return originalCurve_.currentLink().maxDate();
    }

    @Override
    public void update() {
        // Mirrors RelativeDerivedYieldTermStructure::update.  The underlying
        // jump machinery is not used here, and the original curve may still
        // be empty while the multi-curve cycle is being assembled.
        if (!originalCurve_.empty()) {
            if (originalCurve_.currentLink().allowsExtrapolation())
                enableExtrapolation();
            else
                disableExtrapolation();
        }
        notifyObservers();
    }

    /** The spreaded zero yield, continuously compounded. */
    @Override
    protected double discountImpl(double t) {
        if (t == 0.0)
            return 1.0;
        return Math.exp(-zeroYieldImpl(t) * t);
    }

    private double zeroYieldImpl(double t) {
        InterestRate zeroRate = originalCurve_.currentLink()
                .zeroRate(t, comp_, freq_, true);
        InterestRate spreadedRate = new InterestRate(zeroRate.rate() + spread_.getValue().value(),
                zeroRate.dayCounter(), zeroRate.compounding(), zeroRate.frequency());
        return spreadedRate.equivalentRate(Compounding.Continuous,
                Frequency.NO_FREQUENCY, t).rate();
    }
}
