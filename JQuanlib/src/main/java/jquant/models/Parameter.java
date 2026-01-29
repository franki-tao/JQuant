package jquant.models;

import jquant.math.Array;
import jquant.math.optimization.Constraint;
import jquant.math.optimization.NoConstraint;

//! Base class for model arguments
public class Parameter {
    //! Base class for model parameter implementation
    protected abstract static class Impl {
        public abstract double value(final Array params, double t);
    }

    protected Parameter.Impl impl_;
    protected Array params_;
    protected Constraint constraint_;

    public Parameter() {
        constraint_ = new NoConstraint();
    }

    public final Array params() {
        return params_;
    }

    public void setParam(int i, double x) {
        params_.set(i, x);
    }

    public boolean testParams(final Array params) {
        return constraint_.test(params);
    }

    public int size() {
        return params_.size();
    }

    public double value(double t) {
        return impl_.value(params_, t);
    }

    public final Parameter.Impl implementation() {
        return impl_;
    }

    public final Constraint constraint() {
        return constraint_;
    }

    protected Parameter(int size, Parameter.Impl impl, Constraint constraint) {
        impl_ = impl;
        params_ = new Array(size);
        constraint_ = constraint;
    }
}
