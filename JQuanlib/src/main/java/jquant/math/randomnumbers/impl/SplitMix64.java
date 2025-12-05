package jquant.math.randomnumbers.impl;

// NOTE: The following copyright notice applies to the
// original C implementation https://prng.di.unimi.it/splitmix64.c
// that has been used for this class.

/** Written in 2015 by Sebastiano Vigna (vigna@acm.org)

    To the extent possible under law, the author has dedicated all copyright
    and related and neighboring rights to this software to the public domain
    worldwide. This software is distributed without any warranty.

    See <http://creativecommons.org/publicdomain/zero/1.0/>.
*/
public class SplitMix64 {
    private long x_;
    public SplitMix64(long x) {
        x_ = x;
    }
    public long next() {
        long z = (x_ += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
