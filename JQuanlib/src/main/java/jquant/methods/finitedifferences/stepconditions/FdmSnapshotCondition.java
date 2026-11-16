package jquant.methods.finitedifferences.stepconditions;

import jquant.math.Array;

/**
 * Step condition which snapshots the values at a given time.
 *
 * <p>Port of {@code ql/methods/finitedifferences/stepconditions/fdmsnapshotcondition.{hpp,cpp}}.
 */
public class FdmSnapshotCondition implements FdmStepCondition {
    private final double t_;
    private Array values_;

    public FdmSnapshotCondition(double t) {
        t_ = t;
        values_ = new Array(0);
    }

    @Override
    public void applyTo(Array a, double t) {
        if (t == t_) {
            values_ = new Array(a);
        }
    }

    public double getTime() {
        return t_;
    }

    public Array getValues() {
        return values_;
    }
}
