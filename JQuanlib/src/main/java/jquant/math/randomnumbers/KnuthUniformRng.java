package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.methods.montecarlo.SampleReal;

import java.util.List;

//! Uniform random number generator
/*! Random number generator by Knuth.
    For more details see Knuth, Seminumerical Algorithms,
    3rd edition, Section 3.6.
    \note This is <b>not</b> Knuth's original implementation which
          is available at
          http://www-cs-faculty.stanford.edu/~knuth/programs.html,
          but rather a slightly modified version wrapped in a C++ class.
          Such modifications did not affect the code but only the data
          structures used, which were converted to their standard C++
          equivalents.
*/
public class KnuthUniformRng {
    private static final int KK = 100, LL = 37, TT = 70, QUALITY = 1009;
    private List<Double> ranf_arr_buf;
    private int ranf_arr_ptr, ranf_arr_sentinel;
    private List<Double> ran_u;

    /**! if the given seed is 0, a random seed will be chosen
            based on clock() */
    public KnuthUniformRng(long seed) {
        ranf_arr_buf = CommonUtil.ArrayInit(QUALITY, Double.NaN);
        ran_u = CommonUtil.ArrayInit(QUALITY, Double.NaN);
        ranf_arr_ptr = ranf_arr_sentinel = ranf_arr_buf.size();
        ranf_start(seed != 0 ? seed : SeedGenerator.INSTANCE.get());
    }
    /**! returns a sample with weight 1.0 containing a random number
          uniformly chosen from (0.0,1.0) */
    public SampleReal next() {
        double result = (ranf_arr_ptr != ranf_arr_sentinel ?
                ranf_arr_buf.get(ranf_arr_ptr++) :
                ranf_arr_cycle());
        return new SampleReal(result, 1.0);
    }

    private void ranf_start(long seed) {
        int t, s, j;
        List<Double> u = CommonUtil.ArrayInit(KK + KK - 1, Double.NaN);
        List<Double> ul = CommonUtil.ArrayInit(KK + KK - 1, Double.NaN);
        double ulp = (1.0 / (1L << 30)) / (1L << 22);                // 2 to the -52
        double ss = 2.0 * ulp * ((seed & 0x3fffffff) + 2);

        for (j = 0; j < KK; j++) {
            u.set(j, ss);
            ul.set(j, 0.0);                    // bootstrap the buffer
            ss += ss;
            if (ss >= 1.0) ss -= 1.0 - 2 * ulp; // cyclic shift of 51 bits
        }
        for (; j < KK + KK - 1; j++) {
            u.set(j, 0d);
            ul.set(j, 0.0);
        }
        u.set(1, u.get(1) + ulp);
        ul.set(1, ulp);            // make u[1] (and only u[1]) "odd"
        s = (int) (seed & 0x3fffffff);
        t = TT - 1;
        while (t != 0) {
            for (j = KK - 1; j > 0; --j) {
                ul.set(j + j, ul.get(j));
                u.set(j + j, u.get(j));
            }   // "square"
            for (j = KK + KK - 2; j > KK - LL; j -= 2) {
                ul.set(KK + KK - 1 - j, 0.0);
                u.set(KK + KK - 1 - j, u.get(j) - ul.get(j));
            }
            for (j = KK + KK - 2; j >= KK; --j) {
                if (ul.get(j) != 0.0) {
                    ul.set(j - (KK - LL), ulp - ul.get(j - (KK - LL)));
                    u.set(j - (KK - LL), mod_sum(u.get(j - (KK - LL)), u.get(j)));
                    ul.set(j - KK, ulp - ul.get(j - KK));
                    u.set(j - KK, mod_sum(u.get(j - KK), u.get(j)));
                }
            }
            if (is_odd(s)) {                            // "multiply by z"
                for (j = KK; j > 0; --j) {
                    ul.set(j, ul.get(j - 1));
                    u.set(j, u.get(j - 1));
                }
                ul.set(0, ul.get(KK));
                u.set(0, u.get(KK));    // shift the buffer cyclically
                if (ul.get(KK) != 0.0) {
                    ul.set(LL, ulp - ul.get(LL));
                    u.set(LL, mod_sum(u.get(LL), u.get(KK)));
                }
            }
            if (s != 0)
                s >>= 1;
            else
                t--;
        }
        for (j = 0; j < LL; j++) {
            ran_u.set(j + KK - LL, u.get(j));
        }
        for (; j < KK; j++) {
            ran_u.set(j - LL, u.get(j));
        }
    }

    private double mod_sum(double x, double y) {
        return (x + y) - (int) (x + y);
    }

    private boolean is_odd(int s) {
        return (s & 1) != 0;
    }

    private void ranf_array(List<Double> aa,
                            int n) {
        int i, j;
        for (j = 0; j < KK; j++) aa.set(j, ran_u.get(j));
        for (; j < n; j++) aa.set(j, mod_sum(aa.get(j - KK), aa.get(j - LL)));
        for (i = 0; i < LL; i++, j++) ran_u.set(i, mod_sum(aa.get(j - KK), aa.get(j - LL)));
        for (; i < KK; i++, j++) ran_u.set(i, mod_sum(aa.get(j - KK), ran_u.get(i - LL)));
    }

    private double ranf_arr_cycle() {
        ranf_array(ranf_arr_buf,QUALITY);
        ranf_arr_ptr = 1;
        ranf_arr_sentinel = 100;
        return ranf_arr_buf.get(0);
    }

    public static void main(String[] args) {
        KnuthUniformRng rng = new KnuthUniformRng(1234);
        for (int i = 0; i < 10; i++) {
            System.out.println("( "+rng.next().value+" , "+rng.next().weight+" )");
        }
    }
}
