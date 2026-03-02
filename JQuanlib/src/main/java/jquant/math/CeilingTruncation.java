package jquant.math;

public class CeilingTruncation extends Rounding{
    // digit = 5
    public CeilingTruncation(int precision, int digit) {
        super(precision, Type.Ceiling, digit);
    }
}
