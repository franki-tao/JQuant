package jquant;

//! Early-exercise base class
/*! The payoff can be at exercise (the default) or at expiry */
public class EarlyExercise extends Exercise {
    private boolean payoffAtExpiry_;

    public EarlyExercise(Type type) {
        super(type);
        payoffAtExpiry_ = false;
    }

    public EarlyExercise(Type type, boolean payoffAtExpiry) {
        super(type);
        payoffAtExpiry_ = payoffAtExpiry;
    }

    public boolean payoffAtExpiry() {
        return payoffAtExpiry_;
    }
}
