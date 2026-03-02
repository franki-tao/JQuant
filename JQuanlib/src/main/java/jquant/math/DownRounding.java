package jquant.math;

public class DownRounding extends Rounding{
    // digit = 5
    public DownRounding(int precision, int digit) {
        super(precision, Type.Down, digit);
    }
}
