package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.PrimeNumbers;
import jquant.methods.montecarlo.SampleVector;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.ArrayInit;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! Faure low-discrepancy sequence generator
 * ! It is based on existing Fortran and C algorithms to calculate pascal
 * matrix and gray transforms.
 * -# E. Thiemard Economic generation of low-discrepancy sequences with
 * a b-ary gray code.
 * -# Algorithms 659, 647. http://www.netlib.org/toms/647,
 * http://www.netlib.org/toms/659
 * <p>
 * \test the correctness of the returned values is tested by
 * reproducing known good values.
 */
public class FaureRsg {
    private int dimensionality_;
    // mutable unsigned Integer sequenceCounter_;
    private SampleVector sequence_;
    private List<Integer> integerSequence_;
    private List<Integer> bary_;
    private List<List<Integer>> gray_;
    private int base_, mbit_;
    private List<List<Integer>> powBase_;
    private List<Integer> addOne_;
    private List<List<List<Integer>>> pascal3D;
    private double normalizationFactor_;

    public FaureRsg(int dimensionality) {
        dimensionality_ = dimensionality;
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality, 0d), 1d);
        integerSequence_ = CommonUtil.ArrayInit(dimensionality, 0);
        QL_REQUIRE(dimensionality > 0,
                "dimensionality must be greater than 0");

        // base is the lowest prime number >= dimensionality_
        int i, j, k = 1;
        base_ = 2;
        while (base_ < dimensionality_) {
            base_ = (int) PrimeNumbers.get(k);
            k++;
        }


        mbit_ = (int) (Math.log(Integer.MAX_VALUE) / Math.log((double) base_));
        gray_ = new ArrayList<>();
        for (int l = 0; l < dimensionality_; l++) {
            gray_.add(CommonUtil.ArrayInit(mbit_ + 1, 0));
        }
        bary_ = CommonUtil.ArrayInit(mbit_ + 1, 0);

        //setMatrixValues();
        powBase_ = new ArrayList<>();
        for (int l = 0; l < mbit_; l++) {
            powBase_.add(CommonUtil.ArrayInit(2 * base_ - 1, 0));
        }
        powBase_.get(mbit_ - 1).set(base_, 1);
        for (int i2 = mbit_ - 2; i2 >= 0; --i2)
            powBase_.get(i2).set(base_, powBase_.get(i2 + 1).get(base_) * base_);
        for (int ii = 0; ii < (int) mbit_; ii++) {
            for (int j1 = base_ + 1; j1 < 2 * (int) base_ - 1; j1++)
                powBase_.get(ii).set(j1, powBase_.get(ii).get(j1 - 1) + powBase_.get(ii).get(base_));
            for (int j2 = base_ - 1; j2 >= 0; --j2)
                powBase_.get(ii).set(j2, powBase_.get(ii).get(j2 + 1) - powBase_.get(ii).get(base_));
        }

        addOne_ = CommonUtil.ArrayInit(base_);
        for (j = 0; j < base_; j++)
            addOne_.set(j, (Integer) (j + 1) % base_);


        //setPascalMatrix();
        pascal3D = new ArrayList<>();
        for (k = 0; k < mbit_; k++) {
            List<List<Integer>> mm = new ArrayList<>();
            for (int l = 0; l < dimensionality_ + 1; l++) {
                mm.add(ArrayInit(k + 1, 0));
            }
            pascal3D.add(mm);
            pascal3D.get(k).get(0).set(k, 1);
            pascal3D.get(k).get(1).set(0, 1);
            pascal3D.get(k).get(1).set(k, 1);
        }

        int p1, p2;
        for (k = 2; k < mbit_; k++) {
            for (i = 1; i < k; i++) {
                p1 = pascal3D.get(k - 1).get(1).get(i - 1);
                p2 = pascal3D.get(k - 1).get(1).get(i);
                pascal3D.get(k).get(1).set(i, (p1 + p2) % base_);
            }
        }

        int fact = 1, diag;
        for (j = 2; j < dimensionality_; j++) {
            for (int kk = mbit_ - 1; kk >= 0; --kk) {
                diag = mbit_ - kk - 1;
                if (diag == 0)
                    fact = 1;
                else
                    fact = (fact * j) % base_;
                for (int ii = 0; ii <= kk; ii++)
                    pascal3D.get((int) (diag + ii)).get(j).set((int) (ii), (fact *
                            pascal3D.get((int) (diag + ii)).get(1).get((int) (ii))) % base_);
            }
        }


        normalizationFactor_ = (double) base_ * (double) powBase_.get(0).get(base_);
        // std::cout << IntegerFormatter::toString(dimensionality_) << ", " ;
        // std::cout << IntegerFormatter::toString(normalizationFactor_);
        // std::cout << std::endl;
    }

    public final List<Integer> nextIntSequence() {
        generateNextIntSequence();
        return integerSequence_;
    }

    public final List<Integer> lastIntSequence() {
        return integerSequence_;
    }

    public final SampleVector nextSequence() {
        generateNextIntSequence();
        for (int i = 0; i < dimensionality_; i++)
            sequence_.value.set(i, integerSequence_.get(i) / normalizationFactor_);
        return sequence_;
    }

    public final SampleVector lastSequence() {
        return sequence_;
    }

    public int dimension() {
        return dimensionality_;
    }

    private void generateNextIntSequence() {
        // sequenceCounter_++;

        int bit = 0;
        int l = bary_.get(bit);
        bary_.set(bit, addOne_.get((int) l));
        while (bary_.get(bit) == 0) {
            bit++;
            l = bary_.get(bit);
            bary_.set(bit, addOne_.get((int) l));
        }
        QL_REQUIRE(bit != mbit_,
                "Error processing Faure sequence.");

        int tmp, g1, g2;
        for (int i = 0; i < dimensionality_; i++) {
            for (int j = 0; j <= bit; j++) {
                tmp = gray_.get(i).get(j);
                gray_.get(i).set(j, (pascal3D.get(bit).get(i).get(j) + tmp) % base_);
                g1 = gray_.get(i).get(j);
                g2 = base_ - 1 + g1 - tmp;
                integerSequence_.set(i, integerSequence_.get(i) + powBase_.get(j).get((int) g2));
            }
        }
    }

    public static void main(String[] args) {
        System.out.println(Math.log(Integer.MAX_VALUE));
        FaureRsg faureRsg = new FaureRsg(3);
        for (int i = 0; i < 10; i++) {
            System.out.println(faureRsg.nextIntSequence());
        }
    }
}
