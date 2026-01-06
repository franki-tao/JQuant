package jquant.methods.lattices;

import jquant.DiscretizedAsset;
import jquant.math.Array;
import jquant.methods.lattices.impl.TreeLatticeImpl;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

//! Binomial lattice approximating the Tsiveriotis-Fernandes model
/*! At this time, this lattice only works with the DiscretizedConvertible class.

    \ingroup lattices
*/
public class TsiveriotisFernandesLattice extends BlackScholesLattice {
    private double creditSpread_;

    public TsiveriotisFernandesLattice(TreeLatticeImpl tree, double riskFreeRate, double end, int steps,
                                       double creditSpread, double volatility, double divYield) {
        super(tree, riskFreeRate, end, steps);
        creditSpread_ = creditSpread;
        QL_REQUIRE(this.pu_ <= 1.0,
                "probability (" + this.pu_ + ") higher than one");
        QL_REQUIRE(this.pu_ >= 0.0,
                "negative (" + this.pu_ + ") probability");
    }

    public double reditSpread() {
        return creditSpread_;
    }

    protected void stepback(
            int i,
            Array values,
            Array conversionProbability,
            Array spreadAdjustedRate,
            Array newValues,
            Array newConversionProbability,
            Array newSpreadAdjustedRate) {

        for (int j = 0; j < size(i); j++) {

            // new conversion probability is calculated via backward
            // induction using up and down probabilities on tree on
            // previous conversion probabilities, ie weighted average
            // of previous probabilities.
            newConversionProbability.set(j,
                    pd_ * conversionProbability.get(j) +
                            pu_ * conversionProbability.get(j + 1));

            // Use blended discounting rate
            newSpreadAdjustedRate.set(j,
                    newConversionProbability.get(j) * riskFreeRate_ +
                            (1 - newConversionProbability.get(j)) * (riskFreeRate_ + creditSpread_));

            newValues.set(j,
                    (this.pd_ * values.get(j) / (1 + (spreadAdjustedRate.get(j) * this.dt_)))
                            + (this.pu_ * values.get(j + 1) / (1 + (spreadAdjustedRate.get(j + 1) * this.dt_))));

        }
    }
//    public void partialRollback(DiscretizedAsset asset,
//                                double to) {
//
//        double from = asset.time();
//
//        if (close(from,to))
//            return;
//
//        QL_REQUIRE(from > to,
//                "cannot roll the asset back to" + to
//                        + " (it is already at t = " + from + ")");
//
//        DiscretizedConvertible convertible = dynamic_cast<DiscretizedConvertible&>(asset);
//
//        auto iFrom = Integer(this->t_.index(from));
//        auto iTo = Integer(this->t_.index(to));
//
//        for (Integer i=iFrom-1; i>=iTo; --i) {
//
//            Array newValues(this->size(i));
//            Array newSpreadAdjustedRate(this->size(i));
//            Array newConversionProbability(this->size(i));
//
//            stepback(i, convertible.values(),
//                    convertible.conversionProbability(),
//                    convertible.spreadAdjustedRate(), newValues,
//                    newConversionProbability,newSpreadAdjustedRate);
//
//            convertible.time() = this->t_[i];
//            convertible.values() = newValues;
//            convertible.spreadAdjustedRate() = newSpreadAdjustedRate;
//            convertible.conversionProbability() = newConversionProbability;
//
//            // skip the very last adjustment
//            if (i != iTo)
//                convertible.adjustValues();
//        }
//    }
}
