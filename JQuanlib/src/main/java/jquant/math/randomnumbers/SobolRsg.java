package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.RandomUtil;
import jquant.methods.montecarlo.SampleVector;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_LN2;
import static jquant.math.MathUtils.PPMT_MAX_DIM;
import static jquant.math.randomnumbers.SobolRsg.DirectionIntegers.*;

/**
 * ! Sobol low-discrepancy sequence generator
 * ! A Gray code counter and bitwise operations are used for very
 * fast sequence generation.
 * <p>
 * The implementation relies on primitive polynomials modulo two
 * from the book "Monte Carlo Methods in Finance" by Peter
 * Jäckel.
 * <p>
 * 21 200 primitive polynomials modulo two are provided in QuantLib.
 * Jäckel has calculated 8 129 334 polynomials: if you need that many
 * dimensions you can replace the primitivepolynomials.cpp file included
 * in QuantLib with the one provided in the CD of the "Monte Carlo
 * Methods in Finance" book.
 * <p>
 * The choice of initialization numbers (also know as free direction
 * integers) is crucial for the homogeneity properties of the sequence.
 * Sobol defines two homogeneity properties: Property A and Property A'.
 * <p>
 * The unit initialization numbers suggested in "Numerical
 * Recipes in C", 2nd edition, by Press, Teukolsky, Vetterling,
 * and Flannery (section 7.7) fail the test for Property A even
 * for low dimensions.
 * <p>
 * Bratley and Fox published coefficients of the free direction
 * integers up to dimension 40, crediting unpublished work of
 * Sobol' and Levitan. See Bratley, P., Fox, B.L. (1988)
 * "Algorithm 659: Implementing Sobol's quasirandom sequence
 * generator," ACM Transactions on Mathematical Software
 * 14:88-100. These values satisfy Property A for d<=20 and d =
 * 23, 31, 33, 34, 37; Property A' holds for d<=6.
 * <p>
 * Jäckel provides in his book (section 8.3) initialization
 * numbers up to dimension 32. Coefficients for d<=8 are the same
 * as in Bradley-Fox, so Property A' holds for d<=6 but Property
 * A holds for d<=32.
 * <p>
 * The implementation of Lemieux, Cieslak, and Luttmer includes
 * coefficients of the free direction integers up to dimension
 * 360.  Coefficients for d<=40 are the same as in Bradley-Fox.
 * For dimension 40<d<=360 the coefficients have
 * been calculated as optimal values based on the "resolution"
 * criterion. See "RandQMC user's guide - A package for
 * randomized quasi-Monte Carlo methods in C," by C. Lemieux,
 * M. Cieslak, and K. Luttmer, version January 13 2004, and
 * references cited there
 * (http://www.math.ucalgary.ca/~lemieux/randqmc.html).
 * The values up to d<=360 has been provided to the QuantLib team by
 * Christiane Lemieux, private communication, September 2004.
 * <p>
 * For more info on Sobol' sequences see also "Monte Carlo
 * Methods in Financial Engineering," by P. Glasserman, 2004,
 * Springer, section 5.2.3
 * <p>
 * The Joe--Kuo numbers and the Kuo numbers are due to Stephen Joe
 * and Frances Kuo.
 * <p>
 * S. Joe and F. Y. Kuo, Constructing Sobol sequences with better
 * two-dimensional projections, preprint Nov 22 2007
 * <p>
 * See http://web.maths.unsw.edu.au/~fkuo/sobol/ for more information.
 * <p>
 * The Joe-Kuo numbers are available under a BSD-style license
 * available at the above link.
 * <p>
 * Note that the Kuo numbers were generated to work with a
 * different ordering of primitive polynomials for the first 40
 * or so dimensions which is why we have the Alternative
 * Primitive Polynomials.
 * <p>
 * \test
 * - the correctness of the returned values is tested by
 * reproducing known good values.
 * - the correctness of the returned values is tested by checking
 * their discrepancy against known good values.
 */
public class SobolRsg {
    public enum DirectionIntegers {
        Unit, Jaeckel, SobolLevitan, SobolLevitanLemieux,
        JoeKuoD5, JoeKuoD6, JoeKuoD7,
        Kuo, Kuo2, Kuo3
    }

    private int dimensionality_;
    private int sequenceCounter_ = 0;
    private boolean firstDraw_ = true;
    private SampleVector sequence_;
    private List<Integer> integerSequence_;
    private List<List<Integer>> directionIntegers_;
    private boolean useGrayCode_;

    /**
     * ! The so called generating integer is chosen to be \f$\gamma(n) = n\f$ if useGrayCode is set to false and
     * \f$\gamma(n) = G(n)\f$ where \f$G(n)\f$ is the Gray code of \f$n\f$ otherwise. The Sobol integers are then
     * constructed using formula 8.20 resp. 8.23, see "Monte Carlo Methods in Finance" by Peter Jäckel. The default
     * is to use the Gray code since this allows a faster sequence generation. The Burley2020SobolRsg relies on an
     * underlying SobolRsg not using the Gray code on the other hand due to its specific way of constructing the
     * integer sequence.
     * <p>
     * \pre dimensionality must be <= PPMT_MAX_DIM
     *
     * @param dimensionality
     * @param seed              default = 0
     * @param directionIntegers default = Jaeckel
     * @param useGrayCode       default = true
     */
    public SobolRsg(int dimensionality,
                    long seed,
                    DirectionIntegers directionIntegers,
                    boolean useGrayCode) {
        dimensionality_ = dimensionality;
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality), 1d);
        integerSequence_ = CommonUtil.ArrayInit(dimensionality, 0);
        directionIntegers_ = CommonUtil.ArrayInit(dimensionality, CommonUtil.ArrayInit(32, 0));
        useGrayCode_ = useGrayCode;
        QL_REQUIRE(dimensionality > 0,
                "dimensionality must be greater than 0");
        QL_REQUIRE(dimensionality <= PPMT_MAX_DIM,
                "dimensionality " + dimensionality
                        + " exceeds the number of available "
                        + "primitive polynomials modulo two ("
                        + PPMT_MAX_DIM + ")");

        // initializes coefficient array of the k-th primitive polynomial
        // and degree of the k-th primitive polynomial
        List<Integer> degree = CommonUtil.ArrayInit(dimensionality_);
        List<Integer> ppmt = CommonUtil.ArrayInit(dimensionality_);

        boolean useAltPolynomials = false;

        if (directionIntegers == Kuo || directionIntegers == Kuo2 || directionIntegers == Kuo3
                || directionIntegers == SobolLevitan || directionIntegers == SobolLevitanLemieux)
            useAltPolynomials = true;

        // degree 0 is not used
        ppmt.set(0, 0);
        degree.set(0, 0);
        int k, index;
        int currentDegree = 1;
        k = 1;
        index = 0;

        int altDegree = useAltPolynomials ? RandomUtil.maxAltDegree : 0;

        for (; k < Math.min(dimensionality_, altDegree); k++, index++) {
            ppmt.set(k, RandomUtil.AltPrimitivePolynomials.get(currentDegree - 1).get(index));
            if (ppmt.get(k) == -1) {
                ++currentDegree;
                index = 0;
                ppmt.set(k, RandomUtil.AltPrimitivePolynomials.get(currentDegree - 1).get(index));
            }

            degree.set(k, currentDegree);
        }


        for (; k < dimensionality_; k++, index++) {
            ppmt.set(k, RandomUtil.PrimitivePolynomials.get(currentDegree - 1).get(index));
            if (ppmt.get(k) == -1) {
                ++currentDegree;
                index = 0;
                ppmt.set(k, RandomUtil.PrimitivePolynomials.get(currentDegree - 1).get(index));
            }
            degree.set(k, currentDegree);

        }

        // initializes 32 direction integers for each dimension
        // and store them into directionIntegers_[dimensionality_][32]
        //
        // In each dimension k with its associated primitive polynomial,
        // the first degree_[k] direction integers can be chosen freely
        // provided that only the l leftmost bits can be non-zero, and
        // that the l-th leftmost bit must be set

        // degenerate (no free direction integers) first dimension
        int j;
        for (j = 0; j < 32; j++)
            directionIntegers_.get(0).set(j, (1 << (32 - j - 1)));


        int maxTabulated = 0;
        // dimensions from 2 (k=1) to maxTabulated (k=maxTabulated-1) included
        // are initialized from tabulated coefficients
        switch (directionIntegers) {
            case Unit:
                maxTabulated = dimensionality_;
                for (k = 1; k < maxTabulated; k++) {
                    for (int l = 1; l <= degree.get(k); l++) {
                        directionIntegers_.get(k).set(l - 1, 1);
                        directionIntegers_.get(k).set(l - 1, directionIntegers_.get(k).get(l - 1) << (32 - l));
                    }
                }
                break;
            case Jaeckel:
                // maxTabulated=32
                maxTabulated = RandomUtil.initializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.initializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.initializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;
            case SobolLevitan:
                // maxTabulated=40
                maxTabulated = RandomUtil.SLinitializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.SLinitializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.SLinitializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;
            case SobolLevitanLemieux:
                // maxTabulated=360
                maxTabulated = RandomUtil.Linitializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.Linitializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.Linitializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;
            case JoeKuoD5:
                // maxTabulated=1898
                maxTabulated = RandomUtil.JoeKuoD5initializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.JoeKuoD5initializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.JoeKuoD5initializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;
            case JoeKuoD6:
                // maxTabulated=1799
                maxTabulated = RandomUtil.JoeKuoD6initializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.JoeKuoD6initializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.JoeKuoD5initializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;
            case JoeKuoD7:
                // maxTabulated=1898
                maxTabulated = RandomUtil.JoeKuoD7initializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.JoeKuoD7initializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.JoeKuoD7initializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;


            case Kuo:
                // maxTabulated=4925
                maxTabulated = RandomUtil.Kuoinitializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.Kuoinitializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.Kuoinitializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;
            case Kuo2:
                // maxTabulated=3946
                maxTabulated = RandomUtil.Kuo2initializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.Kuo2initializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.Kuo2initializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;

            case Kuo3:
                // maxTabulated=4585
                maxTabulated = RandomUtil.Kuo3initializers.size() + 1;
                for (k = 1; k < Math.min(dimensionality_, maxTabulated); k++) {
                    j = 0;
                    // 0UL marks coefficients' end for a given dimension
                    while (RandomUtil.Kuo3initializers.get(k - 1).get(j) != 0) {
                        directionIntegers_.get(k).set(j, RandomUtil.Kuo3initializers.get(k - 1).get(j));
                        directionIntegers_.get(k).set(j, directionIntegers_.get(k).get(j) << (32 - j - 1));
                        j++;
                    }
                }
                break;

            default:
                break;
        }

        // random initialization for higher dimensions
        if (dimensionality_ > maxTabulated) {
            MersenneTwisterUniformRng uniformRng = new MersenneTwisterUniformRng(seed);
            for (k = maxTabulated; k < dimensionality_; k++) {
                for (int l = 1; l <= degree.get(k); l++) {
                    do {
                        // u is in (0,1)
                        double u = uniformRng.next().value;
                        // the direction integer has at most the
                        // rightmost l bits non-zero
                        directionIntegers_.get(k).set(l - 1, (int) (u * (1 << l)));
                    } while ((directionIntegers_.get(k).get(l - 1) & 1) == 0);
                    // iterate until the direction integer is odd
                    // that is it has the rightmost bit set

                    // shifting 32-l bits to the left
                    // we are guaranteed that the l-th leftmost bit
                    // is set, and only the first l leftmost bit
                    // can be non-zero
                    directionIntegers_.get(k).set(l - 1, directionIntegers_.get(k).get(l - 1) << (32 - l));
                }
            }
        }

        // computation of directionIntegers_[k][l] for l>=degree_[k]
        // by recurrence relation
        for (k = 1; k < dimensionality_; k++) {
            int gk = degree.get(k);
            for (int l = gk; l < 32; l++) {
                // eq. 8.19 "Monte Carlo Methods in Finance" by P. J�ckel
                int n = (directionIntegers_.get(k).get(l - gk) >> gk);
                // a[k][j] are the coefficients of the monomials in ppmt[k]
                // The highest order coefficient a[k][0] is not actually
                // used in the recurrence relation, and the lowest order
                // coefficient a[k][gk] is always set: this is the reason
                // why the highest and lowest coefficient of
                // the polynomial ppmt[k] are not included in its encoding,
                // provided that its degree is known.
                // That is: a[k][j] = ppmt[k] >> (gk-j-1)
                for (j = 1; j < gk; j++) {
                    // XORed with a selection of (unshifted) direction
                    // integers controlled by which of the a[k][j] are set
                    if (((ppmt.get(k) >> (gk - j - 1)) & 1) != 0)
                        n ^= directionIntegers_.get(k).get(l - j);
                }
                // a[k][gk] is always set, so directionIntegers_[k][l-gk]
                // will always enter
                n ^= directionIntegers_.get(k).get(l - gk);
                directionIntegers_.get(k).set(l, n);
            }
        }
        // initialize the Sobol integer/double vectors
        // first draw, this is only needed if Gray code is used
        if (useGrayCode_) {
            for (k = 0; k < dimensionality_; k++) {
                integerSequence_.set(k, directionIntegers_.get(k).get(0));
            }
        }
    }

    /**
     * ! skip to the n-th sample in the low-discrepancy sequence
     */
    public final List<Integer> skipTo(int skip) {
        int N = skip + 1;

        if (useGrayCode_) {
            int ops = (int) (Math.log((double) N) / M_LN2) + 1;

            // Convert to Gray code
            int G = N ^ (N >> 1);
            for (int k = 0; k < dimensionality_; k++) {
                integerSequence_.set(k, 0);
                for (int index = 0; index < ops; index++) {
                    if ((G >> index & 1) != 0)
                        integerSequence_.set(k, integerSequence_.get(k) ^ directionIntegers_.get(k).get(index));
                }
            }
        } else {
            integerSequence_ = CommonUtil.ArrayInit(integerSequence_.size(), 0);
            int mask = 1;
            for (int index = 0; index < 32; index++) {
                if ((N & mask) != 0) {
                    for (int k = 0; k < dimensionality_; k++) {
                        integerSequence_.set(k, integerSequence_.get(k) ^ directionIntegers_.get(k).get(index));
                    }
                }
                mask = mask << 1;
            }
        }

        sequenceCounter_ = skip;
        return integerSequence_;
    }

    public final List<Integer> nextInt32Sequence() {
        if (!useGrayCode_) {
            skipTo(sequenceCounter_);
            if (firstDraw_) {
                firstDraw_ = false;
            } else {
                ++sequenceCounter_;
                QL_REQUIRE(sequenceCounter_ != 0, "period exceeded");
            }
            return integerSequence_;
        }

        if (firstDraw_) {
            // it was precomputed in the constructor
            firstDraw_ = false;
            return integerSequence_;
        }
        // increment the counter
        sequenceCounter_++;
        // did we overflow?
        QL_REQUIRE(sequenceCounter_ != 0, "period exceeded");

        // instead of using the counter n as new unique generating integer
        // for the n-th draw use the Gray code G(n) as proposed
        // by Antonov and Saleev
        int n = sequenceCounter_;
        // Find rightmost zero bit of n
        int j = 0;
        while ((n & 1) != 0) {
            n >>= 1;
            j++;
        }
        for (int k = 0; k < dimensionality_; k++) {
            // XOR the appropriate direction number into each component of
            // the integer sequence to obtain a new Sobol integer for that
            // component
            integerSequence_.set(k, integerSequence_.get(k) ^ directionIntegers_.get(k).get(j));
        }
        return integerSequence_;
    }

    public final SampleVector nextSequence() {
        final List<Integer> v = nextInt32Sequence();
        // normalize to get a double in (0,1)
        for (int k = 0; k < dimensionality_; ++k)
            sequence_.value.set(k, v.get(k) * (0.5 / (1 << 31)));
        return sequence_;
    }

    public final SampleVector lastSequence() { return sequence_; }
    public int dimension() { return dimensionality_; }

    public static void main(String[] args) {
        SobolRsg rsg = new SobolRsg(3, 200, Unit, true);
        for (int i = 0; i < 10; i++) {
            System.out.println(rsg.nextInt32Sequence());
        }
    }
}
