package jquant;

public interface StochasticProcess1DImpl {
    double drift(final StochasticProcess1D s,
                                 double t0, double x0, double dt);

    double diffusion(final StochasticProcess1D s,
                                     double t0, double x0, double dt);

    double variance(final StochasticProcess1D s,
                                    double t0, double x0, double dt);
}
