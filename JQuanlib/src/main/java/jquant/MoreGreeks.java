package jquant;

//! more additional %option results
public class MoreGreeks implements PricingEngine.results{
    public double itmCashProbability;
    public double deltaForward;
    public double elasticity;
    public double thetaPerDay;
    public double strikeSensitivity;

    @Override
    public void reset() {
        itmCashProbability = deltaForward = elasticity = thetaPerDay =
                strikeSensitivity = Double.NaN;
    }
}
