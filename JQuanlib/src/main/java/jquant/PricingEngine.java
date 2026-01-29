package jquant;

import jquant.patterns.Observable;

public interface PricingEngine extends Observable {
    interface arguments {
        void validate();
    }

    interface results {
        void reset();
    }

    arguments getArguments();
    results getResults();
    void reset();
    void calculate();
}
