package jquant.methods.finitedifferences;

public class PdeConstantCoeff implements PdeSecondOrderParabolic {
    private double diffusion_;
    private double drift_;
    private double discount_;

    public PdeConstantCoeff(final PdeSecondOrderParabolic process, double t, double x) {
        diffusion_ = process.diffusion(t, x);
        drift_ = process.drift(t, x);
        discount_ = process.discount(t, x);
    }

    @Override
    public double diffusion(double t, double x) {
        return diffusion_;
    }

    @Override
    public double drift(double t, double x) {
        return drift_;
    }

    @Override
    public double discount(double t, double x) {
        return discount_;
    }
}
