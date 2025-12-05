package jquant.math;

public interface Function {
    double value(double x);

    default double derivative(double x) {
        return (value(x+1e-8)-value(x))/1e-8;
    }

    default double secondDerivative(double x) {return (derivative(x+1e-8)-derivative(x))/1e-8;}
}
