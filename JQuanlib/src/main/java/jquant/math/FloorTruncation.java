package jquant.math;

public class FloorTruncation extends Rounding {
    // digit = 5
    public FloorTruncation(int precision, int digit) {
        super(precision, Type.Floor, digit);
    }
}
