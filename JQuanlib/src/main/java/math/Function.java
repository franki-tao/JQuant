package math;

public interface Function {
    double value(double x);

    default double derivative(double x) {
        return 0;
    }
}
