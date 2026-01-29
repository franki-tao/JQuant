package jquant;

//! additional %option results
public class Greeks implements PricingEngine.results{
    public double delta;
    public double gamma;
    public double theta;
    public double vega;
    public double rho;
    public double dividendRho;

    @Override
    public void reset() {
        delta = gamma = theta = vega = rho = dividendRho = Double.NaN;
    }
}
