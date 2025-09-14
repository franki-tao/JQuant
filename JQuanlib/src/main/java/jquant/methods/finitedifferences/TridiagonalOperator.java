package jquant.methods.finitedifferences;

import jquant.math.Array;
import jquant.methods.finitedifferences.impl.TimeSetter;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

public class TridiagonalOperator {
    protected int n_;
    protected Array diagonal_, lowerDiagonal_, upperDiagonal_;
    protected Array temp_;
    protected TimeSetter timeSetter_;

    public TridiagonalOperator(int size) {
        if (size >= 2) {
            n_ = size;
            diagonal_ = new Array(size);
            lowerDiagonal_ = new Array(size - 1);
            upperDiagonal_ = new Array(size - 1);
            temp_ = new Array(size);
        } else if (size == 0) {
            n_ = 0;
            diagonal_ = new Array(0);
            lowerDiagonal_ = new Array(0);
            upperDiagonal_ = new Array(0);
            temp_ = new Array(0);
        } else {
            QL_FAIL("invalid size (" + size + ") for tridiagonal operator " +
                    "(must be null or >= 2)");
        }
    }

    public TridiagonalOperator(Array low,
                               Array mid,
                               Array high) {
        n_ = (mid.size());
        diagonal_ = (mid);
        lowerDiagonal_ = (low);
        upperDiagonal_ = (high);
        temp_ = new Array(n_);
        QL_REQUIRE(low.size() == n_ - 1,
                "low diagonal vector of size " + low.size() +
                        " instead of " + (n_ - 1));
        QL_REQUIRE(high.size() == n_ - 1,
                "high diagonal vector of size " + high.size() +
                        " instead of " + (n_ - 1));
    }

    //! apply operator to a given array
    public Array applyTo(final Array v) {
        QL_REQUIRE(n_ != 0,
                "uninitialized TridiagonalOperator");
        QL_REQUIRE(v.size() == n_,
                "vector of the wrong size " + v.size() +
                        " instead of " + n_);
        Array result = new Array(n_);
        for (int i = 0; i < diagonal_.size(); i++) {
            result.set(i, diagonal_.get(i) * v.get(i));
        }
        // matricial product
        result.addEq(0, upperDiagonal_.get(0) * v.get(1));
        for (int j = 1; j <= n_ - 2; j++)
            result.addEq(j, lowerDiagonal_.get(j - 1) * v.get(j - 1) + upperDiagonal_.get(j) * v.get(j + 1));
        result.addEq(n_ - 1, lowerDiagonal_.get(n_ - 2) * v.get(n_ - 2));
        return result;
    }

    //! solve linear system for a given right-hand side
    public Array solveFor(final Array rhs) {
        Array result = new Array(rhs.size());
        solveFor(rhs, result);
        return result;
    }

    /*! solve linear system for a given right-hand side
        without result Array allocation. The rhs and result parameters
        can be the same Array, in which case rhs will be changed
    */
    public void solveFor(final Array rhs, Array result) {

        QL_REQUIRE(n_ != 0,
                "uninitialized TridiagonalOperator");
        QL_REQUIRE(rhs.size() == n_,
                "rhs vector of size " + rhs.size() +
                        " instead of " + n_);

        double bet = diagonal_.get(0);
        QL_REQUIRE(!close(bet, 0.0),
                "diagonal's first element (" + bet +
                        ") cannot be close to zero");
        result.set(0, rhs.get(0) / bet);
        for (int j = 1; j <= n_ - 1; ++j) {
            temp_.set(j, upperDiagonal_.get(j - 1) / bet);
            bet = diagonal_.get(j) - lowerDiagonal_.get(j - 1) * temp_.get(j);
            QL_REQUIRE(!close(bet, 0.0), "division by zero");
            result.set(j, (rhs.get(j) - lowerDiagonal_.get(j - 1) * result.get(j - 1)) / bet);
        }
        // cannot be j>=0 with Size j
        for (int j = n_ - 2; j > 0; --j)
            result.subtractEq(j, temp_.get(j + 1) * result.get(j + 1));
        result.subtractEq(0, temp_.get(1) * result.get(1));
    }

    //! solve linear system with SOR approach
    public Array SOR(final Array rhs, double tol) {
        QL_REQUIRE(n_ != 0,
                "uninitialized TridiagonalOperator");
        QL_REQUIRE(rhs.size() == n_,
                "rhs vector of size " + rhs.size() +
                        " instead of " + n_);
        // initial guess
        Array result = rhs;

        // solve tridiagonal system with SOR technique
        double omega = 1.5;
        double err = 2.0 * tol;
        double temp;
        for (int sorIteration = 0; err > tol; ++sorIteration) {
            QL_REQUIRE(sorIteration < 100000,
                    "tolerance (" + tol + ") not reached in " +
                            sorIteration + " iterations. " +
                            "The error still is " + err);

            temp = omega * (rhs.get(0) -
                    upperDiagonal_.get(0) * result.get(1) -
                    diagonal_.get(0) * result.get(0)) / diagonal_.get(0);
            err = temp * temp;
            result.addEq(0, temp);
            int i;
            for (i = 1; i < n_ - 1; ++i) {
                temp = omega * (rhs.get(i) -
                        upperDiagonal_.get(i) * result.get(i + 1) -
                        diagonal_.get(i) * result.get(i) -
                        lowerDiagonal_.get(i - 1) * result.get(i - 1)) / diagonal_.get(i);
                err += temp * temp;
                result.addEq(i, temp);
            }

            temp = omega * (rhs.get(i) -
                    diagonal_.get(i) * result.get(i) -
                    lowerDiagonal_.get(i - 1) * result.get(i - 1)) / diagonal_.get(i);
            err += temp * temp;
            result.addEq(i, temp);
        }
        return result;
    }

    //! identity instance
    public static TridiagonalOperator identity(int size) {
        return new TridiagonalOperator(new Array(size - 1, 0.0),     // lower diagonal
                new Array(size, 1.0),     // diagonal
                new Array(size - 1, 0.0));    // upper diagonal
    }

    //! \name Inspectors
    //@{
    public final int size() {
        return n_;
    }

    public final boolean isTimeDependent() {
        return timeSetter_ != null;
    }

    public final Array lowerDiagonal() {
        return lowerDiagonal_;
    }

    public final Array diagonal() {
        return diagonal_;
    }

    public final Array upperDiagonal() {
        return upperDiagonal_;
    }

    //@}
    //! \name Modifiers
    //@{
    public void setFirstRow(double valB, double valC) {
        diagonal_.set(0, valB);
        upperDiagonal_.set(0, valC);
    }

    public void setMidRow(int i,
                          double valA,
                          double valB,
                          double valC) {
        QL_REQUIRE(i >= 1 && i <= n_ - 2,
                "out of range in TridiagonalSystem::setMidRow");
        lowerDiagonal_.set(i - 1, valA);
        diagonal_.set(i, valB);
        upperDiagonal_.set(i, valC);
    }

    public void setMidRows(double valA,
                           double valB,
                           double valC) {
        for (int i = 1; i <= n_ - 2; i++) {
            lowerDiagonal_.set(i - 1, valA);
            diagonal_.set(i, valB);
            upperDiagonal_.set(i, valC);
        }
    }

    public void setLastRow(double valA,
                           double valB) {
        lowerDiagonal_.set(n_ - 2, valA);
        diagonal_.set(n_ - 1, valB);
    }

    public void setTime(double t) {
        if (timeSetter_ != null)
            timeSetter_.setTime(t, this);
    }

    //@}
    //! \name Utilities
    //@{
    public void swap(TridiagonalOperator from) {
        //swap value
        int tp = n_;
        n_ = from.n_;
        from.n_ = tp;

        diagonal_.swap(from.diagonal_);
        lowerDiagonal_.swap(from.lowerDiagonal_);
        upperDiagonal_.swap(from.upperDiagonal_);
        temp_.swap(from.temp_);

        //swap timesetter
        TimeSetter tp1 = timeSetter_;
        timeSetter_ = from.timeSetter_;
        from.timeSetter_ =tp1;
    }

    //todo 运算符重载暂未实现
}
