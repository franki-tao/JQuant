package jquant.methods.lattices;

import jquant.DiscretizedAsset;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.methods.lattices.impl.TreeLatticeImpl;

import java.util.List;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

//! Tree-based lattice-method base class
/*! This class defines a lattice method that is able to rollback
    (with discount) a discretized asset object. It will be based
    on one or more trees.

    Derived classes must implement the following interface:
    \code
    public:
      DiscountFactor discount(Size i, Size index) const;
      Size descendant(Size i, Size index, Size branch) const;
      Real probability(Size i, Size index, Size branch) const;
    \endcode
    and may implement the following:
    \code
    public:
      void stepback(Size i,
                    const Array& values,
                    Array& newValues) const;
    \endcode

    \ingroup lattices
*/
public abstract class TreeLattice {
    private int n_;
    private int statePricesLimit_;
    protected TreeLatticeImpl impl_;
    protected TimeGrid t_;
    // Arrow-Debrew state prices
    protected List<Array> statePrices_;

    public TreeLattice(TimeGrid timeGrid, int n) {
        t_ = timeGrid;
        n_ = n;
        QL_REQUIRE(n > 0, "there is no zeronomial lattice!");
        statePrices_ = CommonUtil.ArrayInit(1, new Array(1, 1.0));
        statePricesLimit_ = 0;
    }

    // 取消模板，改成设置（初始化后再设置）
    public void setImpl_(TreeLatticeImpl impl) {
        impl_ = impl;
    }

    public final TimeGrid timeGrid() {
        return t_;
    }

    public void initialize(DiscretizedAsset asset, double t) {
        int i = t_.index(t);
        asset.setTime(t);
        asset.reset(impl_.size(i));
    }

    public void rollback(DiscretizedAsset asset, double to) {
        partialRollback(asset, to);
        asset.adjustValues();
    }

    public void partialRollback(DiscretizedAsset asset, double to) {
        double from = asset.time();

        if (close(from, to))
            return;

        QL_REQUIRE(from > to,
                "cannot roll the asset back to" + to
                        + " (it is already at t = " + from + ")");

        int iFrom = (t_.index(from));
        int iTo = (t_.index(to));

        for (int i = iFrom - 1; i >= iTo; --i) {
            Array newValues = new Array(impl_.size(i));
            impl_.stepback(i, asset.values(), newValues);
            asset.setTime(t_.get(i));
            asset.setValues(newValues);
            // skip the very last adjustment
            if (i != iTo)
                asset.adjustValues();
        }
    }

    //! Computes the present value of an asset using Arrow-Debrew prices
    public double presentValue(DiscretizedAsset asset) {
        int i = t_.index(asset.time());
        return DotProduct(asset.values(), statePrices(i));
    }

    public final Array statePrices(int i) {
        if (i > statePricesLimit_)
            computeStatePrices(i);
        return statePrices_.get(i);
    }

    public void stepback(int i, Array values, Array newValues) {
        for (int j = 0; j < impl_.size(i); j++) {
            double value = 0.0;
            for (int l = 0; l < n_; l++) {
                value += impl_.probability(i, j, l) *
                        values.get(impl_.descendant(i, j, l));
            }
            value *= impl_.discount(i, j);
            newValues.set(j, value);
        }
    }

    public abstract Array grid(double t);

    protected void computeStatePrices(int until) {
        for (int i = statePricesLimit_; i < until; i++) {
            statePrices_.add(new Array(impl_.size(i + 1), 0.0));
            for (int j = 0; j < impl_.size(i); j++) {
                double disc = impl_.discount(i, j);
                double statePrice = statePrices_.get(i).get(j);
                for (int l = 0; l < n_; l++) {
                    double temp = statePrices_.get(i + 1).get(impl_.descendant(i, j, l)) + statePrice * disc * impl_.probability(i, j, l);
                    statePrices_.get(i + 1).set(impl_.descendant(i, j, l), temp);
                }
            }
        }
        statePricesLimit_ = until;
    }
}
