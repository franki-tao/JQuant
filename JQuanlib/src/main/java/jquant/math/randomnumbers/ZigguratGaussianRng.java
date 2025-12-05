package jquant.math.randomnumbers;


import jquant.math.randomnumbers.impl.RandomUtil;
import jquant.math.randomnumbers.impl.RngInt64;
import jquant.methods.montecarlo.SampleReal;

/**
 * ! Gaussian random number generator
 * ! It uses the Ziggurat transformation to return a
 * normal distributed Gaussian deviate with average 0.0 and
 * standard deviation of 1.0, from a random integer
 * in the [0,0xffffffffffffffffULL]-interval like.
 * <p>
 * For a more detailed description see the article
 * "An Improved Ziggurat Method to Generate Normal Random Samples"
 * by Jurgen A. Doornik
 * (https://www.doornik.com/research/ziggurat.pdf).
 * <p>
 * The code here is inspired by the rust implementation in
 * https://github.com/rust-random/rand/blob/d42daabf65a3ceaf58c2eefc7eb477c4d5a9b4ba/rand_distr/src/normal.rs
 * and
 * https://github.com/rust-random/rand/blob/d42daabf65a3ceaf58c2eefc7eb477c4d5a9b4ba/rand_distr/src/utils.rs.
 * <p>
 * Class RNG must implement the following interface:
 * \code
 * Real nextReal() const;
 * std::uint64_t nextInt64() const;
 * \endcode
 * Currently, Xoshiro256StarStarUniformRng is the only RNG supporting this.
 */
public class ZigguratGaussianRng {
    private RngInt64 uint64Generator_;

    public ZigguratGaussianRng(RngInt64 uint64Generator) {
        uint64Generator_ = uint64Generator;
    }

    //! returns a sample from a Gaussian distribution
    public SampleReal next() {
        return new SampleReal(nextReal(), 1.0);
    }

    //! return a random number from a Gaussian distribution
    public double nextReal() {
        while (true) {
            // As an optimisation we re-implement the conversion
            // to a double in the interval (-1,1).
            // From the remaining 12 most significant bits we use 8 to construct `i`.
            //
            // This saves us generating a whole extra random number, while the added
            // precision of using 64 bits for double does not buy us much.
            long randomU64 = uint64Generator_.nextInt64();
            double u = 2.0 * ((double) (randomU64 >>> 11) + 0.5) * (1.0 / (double) (1L << 53)) - 1.0;
            int i = (int) (randomU64 & 0xff);

            double x = u * normX(i);

            if (Math.abs(x) < normX(i + 1)) {
                return x;
            }
            if (i == 0) {
                // compute a random number in the tail by hand
                return zeroCase(u);
            }
            if (normF(i + 1) + (normF(i) - normF(i + 1) * uint64Generator_.nextReal()) < pdf(x)) {
                return x;
            }
        }
    }

    private double pdf(double x) {
        return Math.exp(-x * x / 2.0);
    }

    //! compute a random number in the tail by hand
    private double zeroCase(double u) {
        // compute a random number in the tail by hand
        double x, y;
        do {
            x = Math.log(uint64Generator_.nextReal()) / normR();
            y = Math.log(uint64Generator_.nextReal());
        } while (-2.0 * y < x * x);

        return (u < 0.0) ? x - normR() : normR() - x;
    }

    private double normR() {
        return 3.654152885361008796;
    }

    private double normX(int i) {
        return RandomUtil.normX[i];
    }

    private double normF(int i) {
        return RandomUtil.normF[i];
    }
}
