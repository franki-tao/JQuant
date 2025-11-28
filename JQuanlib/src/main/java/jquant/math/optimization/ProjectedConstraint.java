package jquant.math.optimization;

import jquant.math.optimization.impl.ProjectedConstraintImpl;

public class ProjectedConstraint extends Constraint {
    public ProjectedConstraint(final Constraint constraint,
                            final Projection projection) {
        super(new ProjectedConstraintImpl(constraint, projection));
    }
}
