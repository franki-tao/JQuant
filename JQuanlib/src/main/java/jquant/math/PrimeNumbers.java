package jquant.math;


import java.util.ArrayList;
import java.util.List;

//! Prime numbers calculator
/*! Taken from "Monte Carlo Methods in Finance", by Peter Jäckel
 */
public class PrimeNumbers {
    public static final long[] firstPrimes = {
            // the first two primes are mandatory for bootstrapping
            2L, 3L,
            // optional additional precomputed primes
            5L, 7L, 11L, 13L, 17L, 19L, 23L, 29L,
            31L, 37L, 41L, 43L, 47L};
    public static List<Long> primeNumbers_ = new ArrayList<>();

    public static long get(int absoluteIndex) {
        if (primeNumbers_.isEmpty()) {
            int n = firstPrimes.length;
            primeNumbers_.addAll(List.of(2L, 3L,
                    // optional additional precomputed primes
                    5L, 7L, 11L, 13L, 17L, 19L, 23L, 29L,
                    31L, 37L, 41L, 43L, 47L));
        }
        while (primeNumbers_.size() <= absoluteIndex)
            nextPrimeNumber();
        return primeNumbers_.get(absoluteIndex);
    }

    public static long nextPrimeNumber() {
        long p, n, m = primeNumbers_.get(primeNumbers_.size() - 1);
        do {
            // skip the even numbers
            m += 2;
            n = (long) Math.sqrt(m);
            ;
            // i=1 since the even numbers have already been skipped
            int i = 1;
            do {
                p = primeNumbers_.get(i);
                ++i;
            } while (((m % p) != 0) && p <= n);
        } while (p <= n);
        primeNumbers_.add(m);
        return m;
    }
}
