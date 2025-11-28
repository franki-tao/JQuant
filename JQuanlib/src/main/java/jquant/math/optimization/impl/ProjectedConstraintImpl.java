package jquant.math.optimization.impl;

import jquant.math.Array;
import jquant.math.optimization.Constraint;
import jquant.math.optimization.Projection;

public class ProjectedConstraintImpl extends ConstraintImpl {
    private Constraint constraint_;
    private Projection projection_;
    public ProjectedConstraintImpl(Constraint constraint,
                                   final Projection projection) {
        constraint_ = constraint;
        projection_ = projection;
    }
    @Override
    public boolean test(Array params) {
        return constraint_.test(projection_.include(params));
    }

    @Override
    public Array upperBound(final Array params) {
        return projection_.project(constraint_.upperBound(projection_.include(params)));
    }

    @Override
    public Array lowerBound(final Array params) {
        return projection_.project(constraint_.lowerBound(projection_.include(params)));
    }
}
