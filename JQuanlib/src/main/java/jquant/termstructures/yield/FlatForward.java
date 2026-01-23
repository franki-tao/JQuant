package jquant.termstructures.yield;

import jquant.Compounding;
import jquant.Handle;
import jquant.InterestRate;
import jquant.Quote;
import jquant.patterns.LazyObject;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;

public class FlatForward extends YieldTermStructure {
    private Handle<Quote> forward_;
    private Compounding compounding_;
    private Frequency frequency_;
    private InterestRate rate_;
    private LazyObject lo_;

    public FlatForward(final Date referenceDate,
                       Handle<Quote> forward,
                       final DayCounter dayCounter,
                       Compounding compounding,
                       Frequency frequency) {
        super(referenceDate, new NullCalendar(), dayCounter, new ArrayList<>(), new ArrayList<>());
        forward_ = forward;
        compounding_ = compounding;
        frequency_ = frequency;
        init();
        registerWith(forward_.getValue());
    }

    public FlatForward(final Date referenceDate,
                       double forward,
                       final DayCounter dayCounter,
                       Compounding compounding,
                       Frequency frequency) {
        super(referenceDate, new NullCalendar(), dayCounter, new ArrayList<>(), new ArrayList<>());
        forward_ = new Handle<>(new SimpleQuote(forward), true);
        compounding_ = compounding;
        frequency_ = frequency;
        init();
        registerWith(forward_.currentLink());
    }

    public FlatForward(int settlementDays,
                       final Calendar calendar,
                       Handle<Quote> forward,
                       final DayCounter dayCounter,
                       Compounding compounding,
                       Frequency frequency) {
        super(settlementDays, calendar, dayCounter, new ArrayList<>(), new ArrayList<>());
        forward_ = forward;
        compounding_ = compounding;
        frequency_ = frequency;
        init();
        registerWith(forward_.currentLink());
    }

    public FlatForward(int settlementDays,
                       final Calendar calendar,
                       double forward,
                       final DayCounter dayCounter,
                       Compounding compounding,
                       Frequency frequency) {
        super(settlementDays, calendar, dayCounter, new ArrayList<>(), new ArrayList<>());
        forward_ = new Handle<>(new SimpleQuote(forward), true);
        compounding_ = compounding;
        frequency_ = frequency;
        init();
        registerWith(forward_.currentLink());
    }

    public Compounding compounding() { return compounding_; }
    public Frequency compoundingFrequency() { return frequency_; }
    public Date maxDate()  { return Date.maxDate(); }
    public void update() {
        lo_.update();
        super.update();
    }
    public double discountImpl(double t) {
        lo_.calculate();
        return rate_.discountFactor(t);
    }

    private void init() {
        lo_ = new LazyObject() {
            @Override
            protected void performCalculations() {
                rate_ = new InterestRate(forward_.currentLink().value(), dayCounter(), compounding_, frequency_);
            }
        };
    }
}
