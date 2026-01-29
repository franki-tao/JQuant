package jquant.models;

import jquant.Handle;
import jquant.math.Array;
import jquant.math.optimization.NoConstraint;
import jquant.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Deterministic time-dependent parameter used for yield-curve fitting
public class TermStructureFittingParameter extends Parameter {
    public static class NumericalImpl extends Parameter.Impl {
        private List<Double> times_;
        private List<Double> values_;
        private Handle<YieldTermStructure> termStructure_;

        public NumericalImpl(Handle<YieldTermStructure> termStructure) {
            times_ = new ArrayList<>();
            values_ = new ArrayList<>();
            termStructure_ = termStructure;
        }

        public void set(double t, double x) {
            times_.add(t);
            values_.add(x);
        }

        public void change(double x) {
            values_.set(values_.size()-1, x);
        }

        public void reset() {
            times_.clear();
            values_.clear();
        }

        public final Handle<YieldTermStructure> termStructure() {
            return termStructure_;
        }

        @Override
        public double value(Array params, double t) {
            int result = times_.indexOf(t);
            QL_REQUIRE(result != -1,
                    "fitting parameter not set!");
            return values_.get(result);
        }
    }

    public TermStructureFittingParameter(final Parameter.Impl impl) {
        super(0, impl, new NoConstraint());
    }

    public TermStructureFittingParameter(final Handle<YieldTermStructure> term) {
        super(0, new NumericalImpl(term), new NoConstraint());
    }
}
