package jquant.math.randomnumbers.impl;

public class DiscardBlockEngine {
    private final Ranlux64Base01 base;
    private final int P, R;
    private int count = 0;

    public DiscardBlockEngine(Ranlux64Base01 base, int P, int R) {
        this.base = base;
        this.P = P;
        this.R = R;
    }

    public long next() {
        if (count == P) {
            for (int i = P; i < R; i++)
                base.next();  // discard
            count = 0;
        }
        count++;
        return base.next();
    }
}
