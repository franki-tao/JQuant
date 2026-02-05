package jquant;

public interface VolatilityCompositor {
    TimeSeries<Double> calculate(final TimeSeries<Double> volatilitySeries);
    void calibrate(final TimeSeries<Double> volatilitySeries);
}
