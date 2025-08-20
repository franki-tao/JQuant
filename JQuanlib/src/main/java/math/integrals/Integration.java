package math.integrals;

import math.Function;

public abstract class Integration {
    protected int n;

    protected Function f;
    public Integration(int n) {
        this.n = n;
    }
    protected abstract void setF();

    public double value() {
        return 0;
    };
}
