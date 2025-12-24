package jquant;

public abstract class StochasticProcess1DImpl {
    public abstract double drift(final StochasticProcess1D s,
                                 double t0, double x0, double dt);

    public abstract double diffusion(final StochasticProcess1D s,
                                     double t0, double x0, double dt);

    public abstract double variance(final StochasticProcess1D s,
                                    double t0, double x0, double dt);
}
