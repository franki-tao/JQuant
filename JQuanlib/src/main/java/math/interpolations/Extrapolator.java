package math.interpolations;

public abstract class Extrapolator {
    private boolean extrapolate_ = false;  //是否允许外插

    public void enableExtrapolation() {
        this.extrapolate_ = true;
    }

    public void disableExtrapolation() {
        this.extrapolate_ = false;
    }

    public boolean allowsExtrapolation() {
        return this.extrapolate_;
    }

}
