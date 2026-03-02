package jquant.math;

public class UpRounding extends Rounding {

    // digit = 5
    public UpRounding(int precision, int digit) {
        super(precision,Type.Up, digit);
    }
}
