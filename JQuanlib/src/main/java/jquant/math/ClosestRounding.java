package jquant.math;

public class ClosestRounding extends Rounding{
    // digit = 5
    public ClosestRounding(int precision, int digit) {
        super(precision, Type.Closest, digit);
    }
}
