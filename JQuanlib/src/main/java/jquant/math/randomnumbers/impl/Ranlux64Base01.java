package jquant.math.randomnumbers.impl;

public class Ranlux64Base01 {
    private static final long MASK48 = (1L << 48) - 1;
    private final int s = 10;
    private final int r = 24;

    private final long[] state = new long[r];
    private int index = 0;
    private long carry = 0;

    public Ranlux64Base01(long seed) {
        // 与 C++ 用 linear_congruential_engine 初始化保持一致
        long x = seed;
        for (int i = 0; i < r; ++i) {
            x = (6364136223846793005L * x + 1) & MASK48;
            state[i] = x;
        }
    }

    public long next() {
        int i = index;
        int j = (i + r - s) % r;

        long xi = state[i];
        long xj = state[j];
        long res = xj - xi - carry;

        if (res < 0) {
            res += (1L << 48);
            carry = 1;
        } else {
            carry = 0;
        }

        state[i] = res & MASK48;
        index = (i + 1) % r;

        return res;
    }
}
